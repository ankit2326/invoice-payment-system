package dodopay.invoice.dto;

public record PspChargeRequest(
    String idempotencyKey,
    String cardToken,
    long amountCents
) {}
