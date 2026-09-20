package dodopay.invoice.controller;

import dodopay.invoice.auth.BusinessContext;
import dodopay.invoice.dto.RegisterWebhookRequest;
import dodopay.invoice.entity.WebhookEndpoint;
import dodopay.invoice.repository.WebhookEndpointRepository;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/webhook-endpoints")
@Profile("api")
public class WebhookEndpointController {

    private final WebhookEndpointRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookEndpointController(WebhookEndpointRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterWebhookRequest request) {
        UUID businessId = BusinessContext.get();

        // Generate a signing secret for this endpoint
        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setBusinessId(businessId);
        endpoint.setUrl(request.url());
        endpoint.setSecret(secret);
        endpoint = repository.save(endpoint);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", endpoint.getId().toString());
        response.put("url", endpoint.getUrl());
        response.put("secret", secret); // Shown once at creation
        response.put("active", endpoint.isActive());
        response.put("created_at", endpoint.getCreatedAt().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        UUID businessId = BusinessContext.get();
        List<WebhookEndpoint> endpoints = repository.findByBusinessId(businessId);
        List<Map<String, Object>> response = endpoints.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId().toString());
            map.put("url", e.getUrl());
            map.put("active", e.isActive());
            map.put("created_at", e.getCreatedAt().toString());
            return map;
        }).toList();
        return ResponseEntity.ok(response);
    }
}
