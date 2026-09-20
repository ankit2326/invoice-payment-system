package dodopay.invoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dodopay.invoice.entity.WebhookEndpoint;
import dodopay.invoice.entity.WebhookEvent;
import dodopay.invoice.entity.WebhookEventStatus;
import dodopay.invoice.repository.WebhookEndpointRepository;
import dodopay.invoice.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("api")
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public WebhookService(WebhookEndpointRepository endpointRepository,
                          WebhookEventRepository eventRepository,
                          ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Queue webhook events for all active endpoints of a business.
     * Joins the caller's transaction so webhook events commit atomically with the
     * invoice/payment state change. If the outer transaction rolls back, no orphan
     * webhook events are created. Delivery is async via WebhookDeliveryWorker.
     */
    @Transactional
    public void dispatchEvent(UUID businessId, String eventType, Map<String, Object> data) {
        List<WebhookEndpoint> endpoints = endpointRepository.findByBusinessIdAndActiveTrue(businessId);
        if (endpoints.isEmpty()) {
            log.debug("No active webhook endpoints for business {}", businessId);
            return;
        }

        Map<String, Object> payload = Map.of(
            "event_type", eventType,
            "data", data
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload", e);
            return;
        }

        for (WebhookEndpoint endpoint : endpoints) {
            WebhookEvent event = new WebhookEvent();
            event.setBusinessId(businessId);
            event.setWebhookEndpointId(endpoint.getId());
            event.setEventType(eventType);
            event.setPayload(payloadJson);
            event.setStatus(WebhookEventStatus.PENDING.getValue());
            eventRepository.save(event);
            log.info("Queued webhook event {} for endpoint {} ({})", eventType, endpoint.getId(), endpoint.getUrl());
        }
    }
}
