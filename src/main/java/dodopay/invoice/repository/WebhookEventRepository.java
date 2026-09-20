package dodopay.invoice.repository;

import dodopay.invoice.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    @Query("SELECT e FROM WebhookEvent e WHERE e.status = 'pending' AND e.nextRetryAt <= :now ORDER BY e.nextRetryAt ASC")
    List<WebhookEvent> findPendingEvents(@Param("now") Instant now);
}
