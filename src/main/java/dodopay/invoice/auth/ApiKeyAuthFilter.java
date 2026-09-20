package dodopay.invoice.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dodopay.invoice.dto.ErrorResponse;
import dodopay.invoice.entity.ApiKey;
import dodopay.invoice.repository.ApiKeyRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

@Component
@Profile("api")
@Order(1)
public class ApiKeyAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KEY_PREFIX = "dodo_";

    // Paths that don't require authentication
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/businesses",
        "/health"
    );

    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository, ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Allow public endpoints
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendError(httpResponse, 401, "unauthorized", "Missing or invalid Authorization header. Use: Bearer <api_key>");
            return;
        }

        String apiKeyRaw = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!apiKeyRaw.startsWith(KEY_PREFIX)) {
            sendError(httpResponse, 401, "unauthorized", "Invalid API key format");
            return;
        }

        // Extract prefix for lookup (first 12 chars)
        String prefix = apiKeyRaw.substring(0, Math.min(12, apiKeyRaw.length()));
        String keyHash = hashKey(apiKeyRaw);

        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndRevokedFalse(prefix);
        ApiKey matched = null;
        for (ApiKey candidate : candidates) {
            if (MessageDigest.isEqual(
                    candidate.getKeyHash().getBytes(StandardCharsets.UTF_8),
                    keyHash.getBytes(StandardCharsets.UTF_8))) {
                matched = candidate;
                break;
            }
        }

        if (matched == null) {
            sendError(httpResponse, 401, "unauthorized", "Invalid API key");
            return;
        }

        BusinessContext.set(matched.getBusinessId());
        try {
            chain.doFilter(request, response);
        } finally {
            BusinessContext.clear();
        }
    }

    private boolean isPublicPath(String path) {
        for (String pub : PUBLIC_PATHS) {
            if (path.equals(pub)) return true;
        }
        return false;
    }

    private void sendError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, error, message));
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
