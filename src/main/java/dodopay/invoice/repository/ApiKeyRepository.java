package dodopay.invoice.repository;

import dodopay.invoice.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByKeyPrefixAndRevokedFalse(String keyPrefix);
    List<ApiKey> findByBusinessIdAndRevokedFalse(UUID businessId);
}
