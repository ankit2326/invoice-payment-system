package dodopay.invoice.controller;

import dodopay.invoice.auth.BusinessContext;
import dodopay.invoice.dto.PayInvoiceRequest;
import dodopay.invoice.exception.ApiException;
import dodopay.invoice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@Profile("api")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/invoices/{invoiceId}/pay")
    public ResponseEntity<Map<String, Object>> payInvoice(
            @PathVariable UUID invoiceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayInvoiceRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Idempotency-Key header is required");
        }

        UUID businessId = BusinessContext.get();
        Map<String, Object> result = paymentService.processPayment(businessId, invoiceId, idempotencyKey, request);
        return ResponseEntity.ok(result);
    }
}
