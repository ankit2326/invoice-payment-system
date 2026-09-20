package dodopay.invoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dodopay.invoice.dto.PayInvoiceRequest;
import dodopay.invoice.dto.PspChargeRequest;
import dodopay.invoice.dto.PspChargeResponse;
import dodopay.invoice.entity.*;
import dodopay.invoice.exception.ApiException;
import dodopay.invoice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("api")
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PspClient pspClient;
    private final WebhookService webhookService;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    public PaymentService(InvoiceRepository invoiceRepository,
                          PaymentAttemptRepository paymentAttemptRepository,
                          IdempotencyRecordRepository idempotencyRecordRepository,
                          PspClient pspClient,
                          WebhookService webhookService,
                          InvoiceService invoiceService,
                          ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.pspClient = pspClient;
        this.webhookService = webhookService;
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a payment attempt for an invoice.
     *
     * Concurrency mechanism: pessimistic row-level lock (SELECT FOR UPDATE) on the invoice row.
     * This guarantees that only one payment attempt can be processed at a time for a given invoice.
     * Concurrent requests will block on the lock and then see the updated state.
     *
     * Idempotency: If the same Idempotency-Key is reused with the same body, return the cached response.
     * If reused with a different body, reject with 422.
     */
    @Transactional
    public Map<String, Object> processPayment(UUID businessId, UUID invoiceId,
                                               String idempotencyKey, PayInvoiceRequest request) {
        // 1. Check idempotency cache first (before locking)
        String bodyHash = hashBody(request);
        var cached = idempotencyRecordRepository.findByIdempotencyKeyAndBusinessId(idempotencyKey, businessId);
        if (cached.isPresent()) {
            IdempotencyRecord record = cached.get();
            if (!record.getRequestBodyHash().equals(bodyHash)) {
                throw ApiException.unprocessable(
                    "Idempotency key already used with a different request body");
            }
            log.info("Returning cached idempotent response for key={}", idempotencyKey);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResponse = objectMapper.readValue(record.getResponseBody(), Map.class);
                return cachedResponse;
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize cached response", e);
            }
        }

        // 2. Acquire pessimistic lock on invoice row
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(invoiceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));

        // 2b. Re-check idempotency after acquiring lock (another thread may have committed
        //     while we were waiting for the lock)
        var rechecked = idempotencyRecordRepository.findByIdempotencyKeyAndBusinessId(idempotencyKey, businessId);
        if (rechecked.isPresent()) {
            IdempotencyRecord record = rechecked.get();
            if (!record.getRequestBodyHash().equals(bodyHash)) {
                throw ApiException.unprocessable(
                    "Idempotency key already used with a different request body");
            }
            log.info("Returning cached idempotent response for key={} (post-lock recheck)", idempotencyKey);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedResponse = objectMapper.readValue(record.getResponseBody(), Map.class);
                return cachedResponse;
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize cached response", e);
            }
        }

        // 3. Check invoice is in a payable state
        if (invoice.getInvoiceStatus() != InvoiceStatus.OPEN) {
            throw ApiException.conflict(
                "Invoice cannot be paid. Current state: " + invoice.getStatus() +
                ". Only invoices in 'open' state can be paid.");
        }

        // 4. Check for existing pending payment attempts (prevents double-charge on PSP timeout)
        if (paymentAttemptRepository.existsByInvoiceIdAndStatus(invoiceId, PaymentStatus.PENDING.getValue())) {
            throw ApiException.conflict(
                "Invoice has a pending payment attempt. Wait for it to resolve or contact support.");
        }

        // 5. Create payment attempt in PENDING state
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setInvoiceId(invoiceId);
        attempt.setIdempotencyKey(idempotencyKey);
        attempt.setCardToken(request.cardToken());
        attempt.setAmountCents(invoice.getTotalAmountCents());
        attempt.setPaymentStatus(PaymentStatus.PENDING);
        attempt = paymentAttemptRepository.save(attempt);

        // 6. Call PSP with timeout protection.
        //    The PSP idempotency key is the client's idempotency key, so if we crash after
        //    PSP success but before commit, a retry with the same key won't double-charge.
        try {
            PspChargeResponse pspResponse = pspClient.charge(new PspChargeRequest(
                    idempotencyKey, request.cardToken(), invoice.getTotalAmountCents()));

            if ("succeeded".equals(pspResponse.status())) {
                attempt.setPaymentStatus(PaymentStatus.SUCCEEDED);
                attempt.setPspReference(pspResponse.pspRef());
                invoice.setInvoiceStatus(InvoiceStatus.PAID);
                log.info("Payment succeeded for invoice={}, pspRef={}", invoiceId, pspResponse.pspRef());

                webhookService.dispatchEvent(businessId, "invoice.paid",
                        invoiceService.buildInvoicePayload(invoice));
            } else {
                attempt.setPaymentStatus(PaymentStatus.FAILED);
                attempt.setFailureCode(pspResponse.code());
                log.info("Payment failed for invoice={}, code={}", invoiceId, pspResponse.code());

                webhookService.dispatchEvent(businessId, "invoice.payment_failed",
                        Map.of(
                            "invoice_id", invoiceId.toString(),
                            "payment_attempt_id", attempt.getId().toString(),
                            "failure_code", pspResponse.code() != null ? pspResponse.code() : "unknown"
                        ));
            }
        } catch (Exception e) {
            // PSP timeout or network error: payment attempt stays PENDING, invoice stays OPEN
            // This is safe: no state corruption. A reconciliation job would resolve this.
            attempt.setPaymentStatus(PaymentStatus.PENDING);
            attempt.setFailureCode("psp_timeout");
            log.warn("PSP call failed for invoice={}: {}. Payment attempt left as PENDING.",
                    invoiceId, e.getMessage());
        }

        paymentAttemptRepository.save(attempt);
        invoiceRepository.save(invoice);

        // 7. Build response
        Map<String, Object> response = buildPaymentResponse(attempt, invoice);

        // 8. Cache the idempotent response
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(idempotencyKey);
            record.setBusinessId(businessId);
            record.setRequestPath("/invoices/" + invoiceId + "/pay");
            record.setRequestBodyHash(bodyHash);
            record.setResponseStatusCode(200);
            record.setResponseBody(objectMapper.writeValueAsString(response));
            idempotencyRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to cache idempotency response", e);
        }

        return response;
    }

    private Map<String, Object> buildPaymentResponse(PaymentAttempt attempt, Invoice invoice) {
        return Map.of(
            "payment_attempt_id", attempt.getId().toString(),
            "invoice_id", attempt.getInvoiceId().toString(),
            "status", attempt.getStatus(),
            "amount_cents", attempt.getAmountCents(),
            "card_token", attempt.getCardToken(),
            "psp_reference", attempt.getPspReference() != null ? attempt.getPspReference() : "",
            "failure_code", attempt.getFailureCode() != null ? attempt.getFailureCode() : "",
            "invoice_status", invoice.getStatus()
        );
    }

    private String hashBody(PayInvoiceRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request body", e);
        }
    }
}
