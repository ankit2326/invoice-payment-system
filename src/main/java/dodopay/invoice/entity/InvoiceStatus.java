package dodopay.invoice.entity;

public enum InvoiceStatus {
    DRAFT("draft"),
    OPEN("open"),
    PAID("paid"),
    VOID("void"),
    UNCOLLECTIBLE("uncollectible");

    private final String value;

    InvoiceStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static InvoiceStatus fromValue(String value) {
        for (InvoiceStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown invoice status: " + value);
    }

    public boolean isTerminal() {
        return this == PAID || this == VOID || this == UNCOLLECTIBLE;
    }
}
