package dodopay.invoice.controller;

import dodopay.invoice.auth.BusinessContext;
import dodopay.invoice.dto.CreateCustomerRequest;
import dodopay.invoice.entity.Customer;
import dodopay.invoice.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
@Profile("api")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<Customer> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        UUID businessId = BusinessContext.get();
        Customer customer = customerService.createCustomer(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(customer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomer(@PathVariable UUID id) {
        UUID businessId = BusinessContext.get();
        return ResponseEntity.ok(customerService.getCustomer(businessId, id));
    }

    @GetMapping
    public ResponseEntity<List<Customer>> listCustomers() {
        UUID businessId = BusinessContext.get();
        return ResponseEntity.ok(customerService.listCustomers(businessId));
    }
}
