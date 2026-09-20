package dodopay.invoice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LineItemRequest(
    @NotBlank String description,
    @Min(1) int quantity,
    @Min(0) long unitAmountCents
) {}
