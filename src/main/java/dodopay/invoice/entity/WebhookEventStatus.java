package dodopay.invoice.entity;

public enum WebhookEventStatus {
    PENDING("pending"),
    DELIVERED("delivered"),
    FAILED("failed");

    private final String value;

    WebhookEventStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static WebhookEventStatus fromValue(String value) {
        for (WebhookEventStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown webhook event status: " + value);
    }
}
