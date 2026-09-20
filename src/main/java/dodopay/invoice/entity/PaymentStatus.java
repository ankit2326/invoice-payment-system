package dodopay.invoice.entity;

public enum PaymentStatus {
    PENDING("pending"),
    SUCCEEDED("succeeded"),
    FAILED("failed");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PaymentStatus fromValue(String value) {
        for (PaymentStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown payment status: " + value);
    }
}
