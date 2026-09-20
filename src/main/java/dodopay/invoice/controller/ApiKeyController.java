package dodopay.invoice.controller;

import dodopay.invoice.auth.BusinessContext;
import dodopay.invoice.service.BusinessService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api-keys")
@Profile("api")
public class ApiKeyController {

    private final BusinessService businessService;

    public ApiKeyController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createApiKey() {
        UUID businessId = BusinessContext.get();
        Map<String, Object> result = businessService.createApiKey(businessId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable UUID keyId) {
        UUID businessId = BusinessContext.get();
        businessService.revokeApiKey(businessId, keyId);
        return ResponseEntity.noContent().build();
    }
}
