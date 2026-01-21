package com.spectralogic.migrationtracker.repository;

import com.spectralogic.migrationtracker.model.Customer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Customer> rowMapper = new RowMapper<Customer>() {
        @Override
        public Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
            Customer customer = new Customer();
            customer.setId(rs.getString("id"));
            customer.setName(rs.getString("name"));
            
            // Handle nullable dates - SQLite stores dates as strings
            String createdAtStr = rs.getString("created_at");
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                customer.setCreatedAt(LocalDate.parse(createdAtStr));
            } else {
                customer.setCreatedAt(LocalDate.now());
            }
            
            String lastUpdatedStr = rs.getString("last_updated");
            if (lastUpdatedStr != null && !lastUpdatedStr.isEmpty()) {
                customer.setLastUpdated(LocalDate.parse(lastUpdatedStr));
            } else {
                customer.setLastUpdated(LocalDate.now());
            }
            
            customer.setActive(rs.getBoolean("active"));
            return customer;
        }
    };

    public List<Customer> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM customer ORDER BY name",
            rowMapper
        );
    }

    public Optional<Customer> findById(String id) {
        List<Customer> results = jdbcTemplate.query(
            "SELECT * FROM customer WHERE id = ?",
            rowMapper,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Customer> searchByName(String name) {
        return jdbcTemplate.query(
            "SELECT * FROM customer WHERE name LIKE ? ORDER BY name",
            rowMapper,
            "%" + name + "%"
        );
    }

    public Customer save(Customer customer) {
        if (customer.getId() == null || findById(customer.getId()).isEmpty()) {
            // Insert
            jdbcTemplate.update(
                "INSERT INTO customer (id, name, created_at, last_updated, active) VALUES (?, ?, ?, ?, ?)",
                customer.getId(),
                customer.getName(),
                customer.getCreatedAt(),
                customer.getLastUpdated(),
                customer.getActive() != null ? customer.getActive() : true
            );
        } else {
            // Update
            customer.setLastUpdated(LocalDate.now());
            jdbcTemplate.update(
                "UPDATE customer SET name = ?, last_updated = ?, active = ? WHERE id = ?",
                customer.getName(),
                customer.getLastUpdated(),
                customer.getActive(),
                customer.getId()
            );
        }
        return customer;
    }

    public void deleteById(String id) {
        jdbcTemplate.update(
            "UPDATE customer SET active = 0 WHERE id = ?",
            id
        );
    }
}
