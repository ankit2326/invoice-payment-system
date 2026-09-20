package dodopay.invoice.controller;

import dodopay.invoice.auth.BusinessContext;
import dodopay.invoice.dto.CreateInvoiceRequest;
import dodopay.invoice.entity.Invoice;
import dodopay.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/invoices")
@Profile("api")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        UUID businessId = BusinessContext.get();
        Invoice invoice = invoiceService.createInvoice(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(invoice));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getInvoice(@PathVariable UUID id) {
        UUID businessId = BusinessContext.get();
        Invoice invoice = invoiceService.getInvoice(businessId, id);
        return ResponseEntity.ok(toResponse(invoice));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listInvoices(
            @RequestParam(required = false) String status) {
        UUID businessId = BusinessContext.get();
        List<Invoice> invoices = invoiceService.listInvoices(businessId, status);
        return ResponseEntity.ok(invoices.stream().map(this::toResponse).toList());
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<Map<String, Object>> finalizeInvoice(@PathVariable UUID id) {
        UUID businessId = BusinessContext.get();
        Invoice invoice = invoiceService.finalizeInvoice(businessId, id);
        return ResponseEntity.ok(toResponse(invoice));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<Map<String, Object>> voidInvoice(@PathVariable UUID id) {
        UUID businessId = BusinessContext.get();
        Invoice invoice = invoiceService.voidInvoice(businessId, id);
        return ResponseEntity.ok(toResponse(invoice));
    }

    @PostMapping("/{id}/mark-uncollectible")
    public ResponseEntity<Map<String, Object>> markUncollectible(@PathVariable UUID id) {
        UUID businessId = BusinessContext.get();
        Invoice invoice = invoiceService.markUncollectible(businessId, id);
        return ResponseEntity.ok(toResponse(invoice));
    }

    private Map<String, Object> toResponse(Invoice invoice) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", invoice.getId().toString());
        map.put("business_id", invoice.getBusinessId().toString());
        map.put("customer_id", invoice.getCustomerId().toString());
        map.put("status", invoice.getStatus());
        map.put("total_amount_cents", invoice.getTotalAmountCents());
        map.put("due_date", invoice.getDueDate().toString());
        map.put("created_at", invoice.getCreatedAt().toString());
        map.put("updated_at", invoice.getUpdatedAt().toString());

        List<Map<String, Object>> lineItems = invoice.getLineItems().stream().map(li -> {
            Map<String, Object> liMap = new LinkedHashMap<>();
            liMap.put("id", li.getId().toString());
            liMap.put("description", li.getDescription());
            liMap.put("quantity", li.getQuantity());
            liMap.put("unit_amount_cents", li.getUnitAmountCents());
            liMap.put("total_cents", li.getTotalCents());
            return liMap;
        }).toList();
        map.put("line_items", lineItems);

        return map;
    }
}
