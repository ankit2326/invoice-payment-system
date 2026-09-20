package dodopay.invoice.repository;

import dodopay.invoice.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);
    boolean existsByInvoiceIdAndStatus(UUID invoiceId, String status);
}
