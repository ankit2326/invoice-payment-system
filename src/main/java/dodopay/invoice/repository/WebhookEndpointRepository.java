package dodopay.invoice.repository;

import dodopay.invoice.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByBusinessIdAndActiveTrue(UUID businessId);
    List<WebhookEndpoint> findByBusinessId(UUID businessId);
}
