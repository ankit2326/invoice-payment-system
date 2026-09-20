package dodopay.invoice.service;

import dodopay.invoice.dto.PspChargeRequest;
import dodopay.invoice.dto.PspChargeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("api")
public class PspClient {

    private static final Logger log = LoggerFactory.getLogger(PspClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PspClient(@Value("${psp.base-url}") String baseUrl,
                     @Value("${psp.timeout-ms}") int timeoutMs) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Calls the PSP to charge a card.
     * Throws RestClientException on timeout or network error.
     */
    public PspChargeResponse charge(PspChargeRequest request) {
        String url = baseUrl + "/psp/charge";
        log.info("Calling PSP: token={}, amount={}, idempotencyKey={}",
                request.cardToken(), request.amountCents(), request.idempotencyKey());
        try {
            PspChargeResponse response = restTemplate.postForObject(url, request, PspChargeResponse.class);
            log.info("PSP response: status={}, pspRef={}, code={}",
                    response != null ? response.status() : "null",
                    response != null ? response.pspRef() : "null",
                    response != null ? response.code() : "null");
            return response;
        } catch (RestClientException e) {
            log.error("PSP call failed: {}", e.getMessage());
            throw e;
        }
    }
}
