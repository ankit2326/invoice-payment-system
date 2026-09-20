package dodopay.invoice.controller;

import dodopay.invoice.dto.CreateBusinessRequest;
import dodopay.invoice.service.BusinessService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public endpoint for creating businesses and their initial API key.
 * This endpoint does NOT require authentication (it bootstraps the auth).
 */
@RestController
@Profile("api")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @PostMapping("/businesses")
    public ResponseEntity<Map<String, Object>> createBusiness(@Valid @RequestBody CreateBusinessRequest request) {
        Map<String, Object> result = businessService.createBusiness(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
