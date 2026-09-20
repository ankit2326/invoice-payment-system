package dodopay.invoice.dto;

import jakarta.validation.constraints.NotBlank;

public record PayInvoiceRequest(
    @NotBlank String cardToken
) {}
