package dodopay.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record RegisterWebhookRequest(
    @NotBlank @URL String url
) {}
