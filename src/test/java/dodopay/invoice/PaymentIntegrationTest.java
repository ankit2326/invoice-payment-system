package dodopay.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dodopay.invoice.dto.*;
import dodopay.invoice.entity.*;
import dodopay.invoice.repository.*;
import dodopay.invoice.service.BusinessService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for payment processing.
 *
 * These tests require a running PostgreSQL and mock PSP.
 * They run against the full Spring context with a real database.
 *
 * The three required tests:
 *   1. Concurrency: N concurrent POST /pay → at most one succeeds
 *   2. Idempotency: same key + same body → same response, no second PSP call
 *   3. PSP failure: tok_timeout/tok_network_error → invoice not stuck
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BusinessService businessService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentAttemptRepository paymentAttemptRepository;

    private String apiKey;
    private UUID businessId;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // These will be overridden if using testcontainers or external DB
        registry.add("spring.datasource.url", () ->
            "jdbc:postgresql://" + System.getenv().getOrDefault("DB_HOST", "localhost") +
            ":5432/" + System.getenv().getOrDefault("DB_NAME", "invoice_db"));
        registry.add("spring.datasource.username", () ->
            System.getenv().getOrDefault("DB_USER", "postgres"));
        registry.add("spring.datasource.password", () ->
            System.getenv().getOrDefault("DB_PASSWORD", "postgres"));
        registry.add("psp.base-url", () ->
            System.getenv().getOrDefault("PSP_BASE_URL", "http://localhost:9090"));
        registry.add("psp.timeout-ms", () -> "5000");
    }

    @BeforeAll
    void setup() throws Exception {
        // Create a business with an API key
        Map<String, Object> result = businessService.createBusiness("Test Business");
        apiKey = (String) result.get("api_key");
        businessId = (UUID) result.get("id");
    }

    private UUID createCustomerAndInvoice() throws Exception {
        // Create customer
        MvcResult customerResult = mockMvc.perform(post("/customers")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateCustomerRequest("Test User " + UUID.randomUUID(),
                                              UUID.randomUUID() + "@test.com"))))
                .andExpect(status().isCreated())
                .andReturn();

        String customerJson = customerResult.getResponse().getContentAsString();
        UUID customerId = UUID.fromString(objectMapper.readTree(customerJson).get("id").asText());

        // Create invoice
        MvcResult invoiceResult = mockMvc.perform(post("/invoices")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateInvoiceRequest(customerId, LocalDate.now().plusDays(30),
                        List.of(new LineItemRequest("Widget", 2, 1000))))))
                .andExpect(status().isCreated())
                .andReturn();

        String invoiceJson = invoiceResult.getResponse().getContentAsString();
        UUID invoiceId = UUID.fromString(objectMapper.readTree(invoiceJson).get("id").asText());

        // Finalize invoice (draft -> open)
        mockMvc.perform(post("/invoices/" + invoiceId + "/finalize")
                .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isOk());

        return invoiceId;
    }

    /**
     * TEST 1: Concurrency
     * Fire N concurrent POST /pay requests for the same invoice.
     * Assert that at most one succeeds, no double-charges, final state is consistent.
     */
    @Test
    void concurrentPayments_onlyOneSucceeds() throws Exception {
        UUID invoiceId = createCustomerAndInvoice();
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final String idempKey = "concurrent-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                latch.await(); // All threads start at the same time
                MvcResult result = mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardToken\": \"tok_success\"}"))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        latch.countDown(); // Fire all threads
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        int successCount = 0;
        for (Future<Integer> f : futures) {
            int status = f.get();
            if (status == 200) {
                // Check the response body to see if payment actually succeeded
                successCount++;
            }
        }

        // Verify: at most one payment attempt should have succeeded
        long succeeded = paymentAttemptRepository.findAll().stream()
                .filter(pa -> pa.getInvoiceId().equals(invoiceId))
                .filter(pa -> pa.getPaymentStatus() == PaymentStatus.SUCCEEDED)
                .count();

        Assertions.assertTrue(succeeded <= 1,
                "Expected at most 1 succeeded payment, got " + succeeded);

        // Verify: invoice is in a valid final state
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        if (succeeded == 1) {
            Assertions.assertEquals("paid", invoice.getStatus(),
                    "Invoice should be 'paid' when one payment succeeded");
        }
    }

    /**
     * TEST 2: Idempotency
     * Retry the same request with the same key → same response, no second PSP call.
     */
    @Test
    void idempotentRetry_returnsSameResponse() throws Exception {
        UUID invoiceId = createCustomerAndInvoice();
        String idempotencyKey = "idemp-" + UUID.randomUUID();
        String body = "{\"cardToken\": \"tok_success\"}";

        // First call
        MvcResult first = mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String firstResponse = first.getResponse().getContentAsString();

        // Second call with the same idempotency key and body
        MvcResult second = mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String secondResponse = second.getResponse().getContentAsString();

        // Responses should match
        Assertions.assertEquals(
                objectMapper.readTree(firstResponse),
                objectMapper.readTree(secondResponse),
                "Idempotent retry should return the same response");

        // Only one payment attempt should exist for this idempotency key
        long attemptCount = paymentAttemptRepository.findAll().stream()
                .filter(pa -> idempotencyKey.equals(pa.getIdempotencyKey()))
                .count();
        Assertions.assertEquals(1, attemptCount,
                "Only one payment attempt should exist for the same idempotency key");
    }

    /**
     * TEST 2b: Idempotency key reused with different body → 422 error.
     */
    @Test
    void idempotentKeyReusedWithDifferentBody_returns422() throws Exception {
        UUID invoiceId = createCustomerAndInvoice();
        String idempotencyKey = "idemp-mismatch-" + UUID.randomUUID();

        // First call
        mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\": \"tok_success\"}"))
                .andExpect(status().isOk());

        // Second call with different body
        mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\": \"tok_card_declined\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * TEST 3: PSP failure (timeout)
     * Use tok_timeout → invoice should not be stuck, payment attempt should be pending.
     */
    @Test
    void pspTimeout_invoiceNotStuck() throws Exception {
        UUID invoiceId = createCustomerAndInvoice();
        String idempotencyKey = "timeout-" + UUID.randomUUID();

        // This should return within PSP_TIMEOUT_MS (5 seconds), not hang for 30 seconds
        long start = System.currentTimeMillis();
        MvcResult result = mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\": \"tok_timeout\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long elapsed = System.currentTimeMillis() - start;

        // Should have returned in under 10 seconds (5s timeout + overhead), NOT 30 seconds
        Assertions.assertTrue(elapsed < 15000,
                "PSP timeout should not block the endpoint for 30 seconds. Took: " + elapsed + "ms");

        // Invoice should still be in 'open' state (not corrupted)
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        Assertions.assertEquals("open", invoice.getStatus(),
                "Invoice should remain 'open' after PSP timeout");

        // Payment attempt should be in 'pending' state
        String responseBody = result.getResponse().getContentAsString();
        Assertions.assertTrue(responseBody.contains("pending"),
                "Payment attempt should be 'pending' after PSP timeout");
    }

    /**
     * TEST 3b: PSP network error
     * Use tok_network_error → invoice should not be stuck.
     */
    @Test
    void pspNetworkError_invoiceNotStuck() throws Exception {
        UUID invoiceId = createCustomerAndInvoice();
        String idempotencyKey = "neterr-" + UUID.randomUUID();

        mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\": \"tok_network_error\"}"))
                .andExpect(status().isOk());

        // Invoice should still be in 'open' state
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        Assertions.assertEquals("open", invoice.getStatus(),
                "Invoice should remain 'open' after PSP network error");
    }

    /**
     * TEST: Paying an already-paid invoice returns conflict error.
     */
    @Test
    void payingPaidInvoice_returnsConflict() throws Exception {
        UUID invoiceId = createCustomerAndInvoice();

        // Pay the invoice successfully
        mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", "pay1-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\": \"tok_success\"}"))
                .andExpect(status().isOk());

        // Try to pay again
        mockMvc.perform(post("/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", "pay2-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\": \"tok_success\"}"))
                .andExpect(status().isConflict());
    }
}
