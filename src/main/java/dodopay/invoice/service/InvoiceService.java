package dodopay.invoice.service;

import dodopay.invoice.dto.CreateInvoiceRequest;
import dodopay.invoice.dto.LineItemRequest;
import dodopay.invoice.entity.Invoice;
import dodopay.invoice.entity.InvoiceLineItem;
import dodopay.invoice.entity.InvoiceStatus;
import dodopay.invoice.exception.ApiException;
import dodopay.invoice.repository.CustomerRepository;
import dodopay.invoice.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Profile("api")
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    // Valid state transitions: from -> set of allowed to states
    private static final Map<InvoiceStatus, Set<InvoiceStatus>> VALID_TRANSITIONS = Map.of(
        InvoiceStatus.DRAFT, Set.of(InvoiceStatus.OPEN, InvoiceStatus.VOID),
        InvoiceStatus.OPEN, Set.of(InvoiceStatus.PAID, InvoiceStatus.VOID, InvoiceStatus.UNCOLLECTIBLE)
    );

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final WebhookService webhookService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          WebhookService webhookService) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.webhookService = webhookService;
    }

    @Transactional
    public Invoice createInvoice(UUID businessId, CreateInvoiceRequest request) {
        // Verify customer belongs to this business
        customerRepository.findByIdAndBusinessId(request.customerId(), businessId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));

        Invoice invoice = new Invoice();
        invoice.setBusinessId(businessId);
        invoice.setCustomerId(request.customerId());
        invoice.setDueDate(request.dueDate());
        invoice.setStatus(InvoiceStatus.DRAFT.getValue());

        // Compute total from line items — server-side only, never trust client total
        long totalCents = 0;
        for (LineItemRequest item : request.lineItems()) {
            long lineTotalCents = (long) item.quantity() * item.unitAmountCents();
            totalCents += lineTotalCents;

            InvoiceLineItem lineItem = new InvoiceLineItem(invoice, item.description(), item.quantity(), item.unitAmountCents());
            invoice.getLineItems().add(lineItem);
        }
        invoice.setTotalAmountCents(totalCents);

        invoice = invoiceRepository.save(invoice);
        log.info("Created invoice {} for business {} with total {} cents", invoice.getId(), businessId, totalCents);

        webhookService.dispatchEvent(businessId, "invoice.created", buildInvoicePayload(invoice));
        return invoice;
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID businessId, UUID invoiceId) {
        return invoiceRepository.findByIdAndBusinessId(invoiceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }

    @Transactional(readOnly = true)
    public List<Invoice> listInvoices(UUID businessId, String status) {
        if (status != null && !status.isBlank()) {
            // Validate status value
            try {
                InvoiceStatus.fromValue(status);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Invalid status filter: " + status);
            }
            return invoiceRepository.findByBusinessIdAndStatus(businessId, status);
        }
        return invoiceRepository.findByBusinessId(businessId);
    }

    @Transactional
    public Invoice finalizeInvoice(UUID businessId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(invoiceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        validateTransition(invoice, InvoiceStatus.OPEN);

        invoice.setInvoiceStatus(InvoiceStatus.OPEN);
        invoice = invoiceRepository.save(invoice);
        log.info("Invoice {} finalized (draft -> open)", invoiceId);
        return invoice;
    }

    @Transactional
    public Invoice voidInvoice(UUID businessId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(invoiceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        validateTransition(invoice, InvoiceStatus.VOID);

        invoice.setInvoiceStatus(InvoiceStatus.VOID);
        invoice = invoiceRepository.save(invoice);
        log.info("Invoice {} voided", invoiceId);
        return invoice;
    }

    @Transactional
    public Invoice markUncollectible(UUID businessId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(invoiceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        validateTransition(invoice, InvoiceStatus.UNCOLLECTIBLE);

        invoice.setInvoiceStatus(InvoiceStatus.UNCOLLECTIBLE);
        invoice = invoiceRepository.save(invoice);
        log.info("Invoice {} marked uncollectible", invoiceId);
        return invoice;
    }

    public void validateTransition(Invoice invoice, InvoiceStatus targetStatus) {
        InvoiceStatus currentStatus = invoice.getInvoiceStatus();
        if (currentStatus.isTerminal()) {
            throw ApiException.conflict("Invoice is in terminal state: " + currentStatus.getValue());
        }
        Set<InvoiceStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw ApiException.conflict(
                "Invalid state transition: " + currentStatus.getValue() + " -> " + targetStatus.getValue());
        }
    }

    public Map<String, Object> buildInvoicePayload(Invoice invoice) {
        return Map.of(
            "invoice_id", invoice.getId().toString(),
            "business_id", invoice.getBusinessId().toString(),
            "customer_id", invoice.getCustomerId().toString(),
            "status", invoice.getStatus(),
            "total_amount_cents", invoice.getTotalAmountCents(),
            "due_date", invoice.getDueDate().toString()
        );
    }
}
