package dodopay.invoice.service;

import dodopay.invoice.entity.WebhookEndpoint;
import dodopay.invoice.entity.WebhookEvent;
import dodopay.invoice.entity.WebhookEventStatus;
import dodopay.invoice.repository.WebhookEndpointRepository;
import dodopay.invoice.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Background worker that polls for pending webhook events and delivers them.
 *
 * Retry policy (exponential backoff):
 *   Attempt 1: immediate
 *   Attempt 2: after 60 seconds
 *   Attempt 3: after 5 minutes
 *   Attempt 4: after 30 minutes
 *   Attempt 5: after 2 hours
 *   After 5 attempts: marked as FAILED
 *
 * Signing scheme: HMAC-SHA256
 *   Header: X-Webhook-Signature: t=<unix_timestamp>,v1=<hex_signature>
 *   Signed data: "<timestamp>.<payload_body>"
 *   This allows receivers to verify authenticity and provides replay protection via timestamp.
 */
@Component
@Profile("api")
public class WebhookDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);
    private static final long[] RETRY_DELAYS_SECONDS = {0, 60, 300, 1800, 7200};

    private final WebhookEventRepository eventRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final RestTemplate restTemplate;

    public WebhookDeliveryWorker(WebhookEventRepository eventRepository,
                                  WebhookEndpointRepository endpointRepository) {
        this.eventRepository = eventRepository;
        this.endpointRepository = endpointRepository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Scheduled(fixedDelay = 2000) // Poll every 2 seconds
    @Transactional
    public void deliverPendingWebhooks() {
        List<WebhookEvent> events = eventRepository.findPendingEvents(Instant.now());

        for (WebhookEvent event : events) {
            try {
                deliverEvent(event);
            } catch (Exception e) {
                log.error("Error processing webhook event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    private void deliverEvent(WebhookEvent event) {
        WebhookEndpoint endpoint = endpointRepository.findById(event.getWebhookEndpointId()).orElse(null);
        if (endpoint == null || !endpoint.isActive()) {
            event.setStatus(WebhookEventStatus.FAILED.getValue());
            eventRepository.save(event);
            return;
        }

        event.setAttempts(event.getAttempts() + 1);
        event.setLastAttemptAt(Instant.now());

        try {
            // Sign the payload
            long timestamp = Instant.now().getEpochSecond();
            String signedPayload = timestamp + "." + event.getPayload();
            String signature = computeHmac(endpoint.getSecret(), signedPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Signature", "t=" + timestamp + ",v1=" + signature);
            headers.set("X-Webhook-Event-Type", event.getEventType());
            headers.set("X-Webhook-Event-Id", event.getId().toString());

            HttpEntity<String> httpEntity = new HttpEntity<>(event.getPayload(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint.getUrl(), HttpMethod.POST, httpEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                event.setStatus(WebhookEventStatus.DELIVERED.getValue());
                log.info("Webhook delivered: event={}, endpoint={}", event.getId(), endpoint.getUrl());
            } else {
                handleFailedAttempt(event);
            }
        } catch (Exception e) {
            log.warn("Webhook delivery failed: event={}, endpoint={}, error={}",
                    event.getId(), endpoint.getUrl(), e.getMessage());
            handleFailedAttempt(event);
        }

        eventRepository.save(event);
    }

    private void handleFailedAttempt(WebhookEvent event) {
        if (event.getAttempts() >= event.getMaxAttempts()) {
            event.setStatus(WebhookEventStatus.FAILED.getValue());
            log.warn("Webhook event {} exhausted all retry attempts", event.getId());
        } else {
            // Schedule next retry with exponential backoff
            int attemptIndex = Math.min(event.getAttempts(), RETRY_DELAYS_SECONDS.length - 1);
            long delaySeconds = RETRY_DELAYS_SECONDS[attemptIndex];
            event.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
            log.info("Webhook event {} scheduled for retry in {} seconds (attempt {}/{})",
                    event.getId(), delaySeconds, event.getAttempts(), event.getMaxAttempts());
        }
    }

    private String computeHmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }
}
