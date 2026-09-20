package dodopay.invoice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBusinessRequest(
    @NotBlank String name
) {}
