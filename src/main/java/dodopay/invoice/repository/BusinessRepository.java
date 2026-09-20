package dodopay.invoice.repository;

import dodopay.invoice.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
}
