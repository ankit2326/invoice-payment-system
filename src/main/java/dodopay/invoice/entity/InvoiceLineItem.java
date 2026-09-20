package dodopay.invoice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items")
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnore
    private Invoice invoice;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_amount_cents", nullable = false)
    private long unitAmountCents;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    public InvoiceLineItem() {}

    public InvoiceLineItem(Invoice invoice, String description, int quantity, long unitAmountCents) {
        this.invoice = invoice;
        this.description = description;
        this.quantity = quantity;
        this.unitAmountCents = unitAmountCents;
        this.totalCents = (long) quantity * unitAmountCents;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public long getUnitAmountCents() { return unitAmountCents; }
    public void setUnitAmountCents(long unitAmountCents) { this.unitAmountCents = unitAmountCents; }
    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long totalCents) { this.totalCents = totalCents; }
}
