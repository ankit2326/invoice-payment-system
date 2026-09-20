package dodopay.invoice.repository;

import dodopay.invoice.entity.IdempotencyRecord;
import dodopay.invoice.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {
    Optional<IdempotencyRecord> findByIdempotencyKeyAndBusinessId(String idempotencyKey, UUID businessId);
}
