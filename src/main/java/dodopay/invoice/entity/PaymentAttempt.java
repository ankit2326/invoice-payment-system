package dodopay.invoice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "card_token", nullable = false)
    private String cardToken;

    @Column(nullable = false, length = 20)
    private String status = PaymentStatus.PENDING.getValue();

    @Column(name = "psp_reference")
    private String pspReference;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PaymentAttempt() {}

    public PaymentStatus getPaymentStatus() {
        return PaymentStatus.fromValue(this.status);
    }

    public void setPaymentStatus(PaymentStatus ps) {
        this.status = ps.getValue();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPspReference() { return pspReference; }
    public void setPspReference(String pspReference) { this.pspReference = pspReference; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public Instant getCreatedAt() { return createdAt; }
}
