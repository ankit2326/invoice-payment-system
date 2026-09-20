package dodopay.invoice.service;

import dodopay.invoice.auth.ApiKeyAuthFilter;
import dodopay.invoice.entity.ApiKey;
import dodopay.invoice.entity.Business;
import dodopay.invoice.repository.ApiKeyRepository;
import dodopay.invoice.repository.BusinessRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("api")
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public BusinessService(BusinessRepository businessRepository, ApiKeyRepository apiKeyRepository) {
        this.businessRepository = businessRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public Map<String, Object> createBusiness(String name) {
        Business business = new Business(name);
        business = businessRepository.save(business);

        // Generate API key: dodo_ + 32 random bytes base64url
        String rawKey = generateApiKey();
        String prefix = rawKey.substring(0, 12);
        String hash = ApiKeyAuthFilter.hashKey(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setBusinessId(business.getId());
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(hash);
        apiKeyRepository.save(apiKey);

        return Map.of(
            "id", business.getId(),
            "name", business.getName(),
            "api_key", rawKey,
            "created_at", business.getCreatedAt()
        );
    }

    @Transactional
    public Map<String, Object> createApiKey(UUID businessId) {
        businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        String rawKey = generateApiKey();
        String prefix = rawKey.substring(0, 12);
        String hash = ApiKeyAuthFilter.hashKey(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setBusinessId(businessId);
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(hash);
        apiKeyRepository.save(apiKey);

        return Map.of(
            "id", apiKey.getId(),
            "api_key", rawKey,
            "prefix", prefix,
            "created_at", apiKey.getCreatedAt()
        );
    }

    @Transactional
    public void revokeApiKey(UUID businessId, UUID keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        if (!key.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException("API key does not belong to this business");
        }
        key.setRevoked(true);
        apiKeyRepository.save(key);
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "dodo_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
