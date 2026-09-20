package dodopay.invoice.service;

import dodopay.invoice.dto.CreateCustomerRequest;
import dodopay.invoice.entity.Customer;
import dodopay.invoice.exception.ApiException;
import dodopay.invoice.repository.CustomerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Profile("api")
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer createCustomer(UUID businessId, CreateCustomerRequest request) {
        if (customerRepository.existsByBusinessIdAndEmail(businessId, request.email())) {
            throw ApiException.conflict("Customer with email " + request.email() + " already exists");
        }

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setName(request.name());
        customer.setEmail(request.email());
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(UUID businessId, UUID customerId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    @Transactional(readOnly = true)
    public List<Customer> listCustomers(UUID businessId) {
        return customerRepository.findByBusinessId(businessId);
    }
}
