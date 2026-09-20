package dodopay.invoice.auth;

import java.util.UUID;

/**
 * Holds the authenticated business ID for the current request.
 * Set by ApiKeyAuthFilter, consumed by controllers.
 */
public class BusinessContext {

    private static final ThreadLocal<UUID> CURRENT_BUSINESS = new ThreadLocal<>();

    public static void set(UUID businessId) {
        CURRENT_BUSINESS.set(businessId);
    }

    public static UUID get() {
        UUID id = CURRENT_BUSINESS.get();
        if (id == null) {
            throw new IllegalStateException("No authenticated business in context");
        }
        return id;
    }

    public static void clear() {
        CURRENT_BUSINESS.remove();
    }
}
