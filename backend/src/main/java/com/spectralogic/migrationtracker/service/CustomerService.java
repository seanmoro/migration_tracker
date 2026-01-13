package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.Customer;
import com.spectralogic.migrationtracker.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<Customer> findAll() {
        return repository.findAll();
    }

    public Customer findById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
    }

    public List<Customer> searchByName(String name) {
        return repository.searchByName(name);
    }

    public Customer create(Customer customer) {
        return repository.save(customer);
    }

    public Customer update(String id, Customer customer) {
        Customer existing = findById(id);
        customer.setId(existing.getId());
        customer.setCreatedAt(existing.getCreatedAt());
        return repository.save(customer);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }
}
