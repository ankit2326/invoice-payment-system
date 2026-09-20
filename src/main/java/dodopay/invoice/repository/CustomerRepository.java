package dodopay.invoice.repository;

import dodopay.invoice.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByBusinessId(UUID businessId);
    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);
    boolean existsByBusinessIdAndEmail(UUID businessId, String email);
}
