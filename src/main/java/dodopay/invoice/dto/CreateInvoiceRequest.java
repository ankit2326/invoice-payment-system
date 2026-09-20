package dodopay.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID customerId,
    @NotNull LocalDate dueDate,
    @NotEmpty @Valid List<LineItemRequest> lineItems
) {}
