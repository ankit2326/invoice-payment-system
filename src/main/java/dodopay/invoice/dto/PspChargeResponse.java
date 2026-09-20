package dodopay.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PspChargeResponse(
    String status,
    @JsonProperty("psp_ref") String pspRef,
    String code
) {}
