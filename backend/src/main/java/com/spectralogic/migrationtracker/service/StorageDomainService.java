package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StorageDomainService {

    private static final Logger logger = LoggerFactory.getLogger(StorageDomainService.class);
    
    private final CustomerService customerService;
    
    @Value("${postgres.blackpearl.host:localhost}")
    private String blackpearlHost;

    @Value("${postgres.blackpearl.port:5432}")
    private int blackpearlPort;

    @Value("${postgres.blackpearl.username:postgres}")
    private String blackpearlUsername;

    @Value("${postgres.blackpearl.password:}")
    private String blackpearlPassword;

    @Value("${postgres.rio.host:localhost}")
    private String rioHost;

    @Value("${postgres.rio.port:5432}")
    private int rioPort;

    @Value("${postgres.rio.username:postgres}")
    private String rioUsername;

    @Value("${postgres.rio.password:}")
    private String rioPassword;

    public StorageDomainService(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * Get storage domains from the customer-specific PostgreSQL database
     */
    public StorageDomains getStorageDomains(String customerId, String databaseType) {
        try {
            Customer customer = customerService.findById(customerId);
            String customerName = customer.getName().toLowerCase().replaceAll("[^a-z0-9]", "_");
            
            // Construct customer-specific database name
            String databaseName;
            String host;
            int port;
            String username;
            String password;
            
            if (databaseType.equalsIgnoreCase("blackpearl")) {
                databaseName = "tapesystem_" + customerName;
                host = blackpearlHost;
                port = blackpearlPort;
                username = blackpearlUsername;
                password = blackpearlPassword != null ? blackpearlPassword : "";
            } else {
                databaseName = "rio_db_" + customerName;
                host = rioHost;
                port = rioPort;
                username = rioUsername;
                password = rioPassword != null ? rioPassword : "";
            }
            
            logger.info("Querying storage domains from {} database: {}@{}:{}/{}", 
                databaseType, username, host, port, databaseName);
            
            // Create data source for customer-specific database
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName));
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            
            Set<String> domains = new HashSet<>();
            
            // Test database connection first - try customer-specific, fallback to generic
            String actualDatabaseName = databaseName;
            try {
                jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                logger.debug("Successfully connected to customer-specific database: {}", databaseName);
            } catch (Exception e) {
                logger.warn("Cannot connect to customer-specific database {}: {}. Trying generic database as fallback.", databaseName, e.getMessage());
                // Fallback to generic database (for data directory restores)
                String genericDatabaseName = databaseType.equalsIgnoreCase("blackpearl") ? "tapesystem" : "rio_db";
                try {
                    dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, genericDatabaseName));
                    jdbc = new JdbcTemplate(dataSource);
                    jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                    actualDatabaseName = genericDatabaseName;
                    logger.info("Successfully connected to generic database: {}. Using this for storage domain queries.", genericDatabaseName);
                } catch (Exception e2) {
                    logger.error("Cannot connect to either customer-specific database {} or generic database {}: {}. Please ensure the database has been restored.", 
                        databaseName, genericDatabaseName, e2.getMessage());
                    // Return empty result with defaults
                    StorageDomains result = new StorageDomains();
                    result.setDomains(new ArrayList<>());
                    result.setSuggestedSource(databaseType.equalsIgnoreCase("blackpearl") ? "BlackPearl" : "Rio");
                    result.setSuggestedTarget(databaseType.equalsIgnoreCase("blackpearl") ? "BlackPearl" : "Rio");
                    return result;
                }
            }
            
            // Try multiple table/column combinations to find storage domains
            // Common patterns:
            // 1. storage_domains table with name column
            // 2. domains table with name column
            // 3. brokers table with name column (for Rio)
            // 4. storage_domain column in various tables
            // 5. domain column in various tables
            
            String[] tableNames = {"storage_domains", "domains", "brokers", "storage_domain", "domain"};
            String[] columnNames = {"name", "domain_name", "storage_domain", "domain", "broker_name"};
            
            for (String tableName : tableNames) {
                for (String columnName : columnNames) {
                    try {
                        // Check if table exists
                        List<String> tables = jdbc.query(
                            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                            (rs, rowNum) -> rs.getString("table_name"),
                            tableName
                        );
                        
                        if (tables.isEmpty()) {
                            continue;
                        }
                        
                        // Check if column exists
                        List<String> columns = jdbc.query(
                            "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                            (rs, rowNum) -> rs.getString("column_name"),
                            tableName, columnName
                        );
                        
                        if (columns.isEmpty()) {
                            continue;
                        }
                        
                        // Query distinct values
                        List<String> values = jdbc.query(
                            String.format("SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL AND %s != '' ORDER BY %s", 
                                columnName, tableName, columnName, columnName, columnName),
                            (rs, rowNum) -> {
                                String value = rs.getString(columnName);
                                return value != null ? value : "";
                            }
                        );
                        
                        if (!values.isEmpty()) {
                            domains.addAll(values);
                            logger.info("Found {} storage domains from {}.{}", values.size(), tableName, columnName);
                        }
                    } catch (Exception e) {
                        logger.debug("Could not query {}.{}: {}", tableName, columnName, e.getMessage());
                    }
                }
            }
            
            // Also try to find domains in any column that might contain domain info
            try {
                // Get all tables
                List<String> allTables = jdbc.query(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                    (rs, rowNum) -> rs.getString("table_name")
                );
                
                // For each table, check for columns with "domain" or "broker" in the name
                for (String tableName : allTables) {
                    try {
                        List<String> domainColumns = jdbc.query(
                            "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%')",
                            (rs, rowNum) -> rs.getString("column_name"),
                            tableName
                        );
                        
                        for (String columnName : domainColumns) {
                            try {
                                List<String> values = jdbc.query(
                                    String.format("SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL AND %s != '' ORDER BY %s LIMIT 50", 
                                        columnName, tableName, columnName, columnName, columnName),
                                    (rs, rowNum) -> {
                                        String value = rs.getString(columnName);
                                        return value != null ? value : "";
                                    }
                                );
                                
                                if (!values.isEmpty()) {
                                    domains.addAll(values);
                                    logger.debug("Found {} values from {}.{}", values.size(), tableName, columnName);
                                }
                            } catch (Exception e) {
                                logger.debug("Could not query {}.{}: {}", tableName, columnName, e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Could not check columns for table {}: {}", tableName, e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not list tables: {}", e.getMessage());
            }
            
            // Convert to sorted list
            List<String> domainList = new ArrayList<>(domains);
            domainList.sort(String::compareToIgnoreCase);
            
            logger.info("Found {} unique storage domains from {} database ({})", domainList.size(), databaseType, actualDatabaseName);
            
            // Log what we found for debugging
            if (domainList.isEmpty()) {
                logger.warn("No storage domains found in database {}. Attempted to query tables: storage_domains, domains, brokers, and columns containing 'domain' or 'broker'", actualDatabaseName);
                
                // Try to list all tables for debugging
                try {
                    List<String> allTables = jdbc.query(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                        (rs, rowNum) -> rs.getString("table_name")
                    );
                    logger.info("Available tables in database {}: {}", actualDatabaseName, allTables);
                    
                    // List all columns that might contain domain info
                    List<Map<String, String>> domainColumns = jdbc.query(
                        "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public' AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%') ORDER BY table_name, column_name",
                        (rs, rowNum) -> {
                            Map<String, String> col = new HashMap<>();
                            col.put("table", rs.getString("table_name"));
                            col.put("column", rs.getString("column_name"));
                            return col;
                        }
                    );
                    if (!domainColumns.isEmpty()) {
                        logger.info("Found potential domain/broker columns: {}", domainColumns);
                    }
                } catch (Exception e) {
                    logger.debug("Could not list tables for debugging: {}", e.getMessage());
                }
            } else {
                logger.info("Storage domains found: {}", domainList);
            }
            
            StorageDomains result = new StorageDomains();
            result.setDomains(domainList);
            
            // Suggest source and target based on database type and found domains
            if (!domainList.isEmpty()) {
                // For BlackPearl, first domain is often source, last might be target
                // For Rio, similar logic
                if (domainList.size() >= 1) {
                    result.setSuggestedSource(domainList.get(0));
                }
                if (domainList.size() >= 2) {
                    result.setSuggestedTarget(domainList.get(domainList.size() - 1));
                } else if (domainList.size() == 1) {
                    // If only one domain, use it for both (user can change)
                    result.setSuggestedTarget(domainList.get(0));
                }
            } else {
                // Fallback to database type name
                result.setSuggestedSource(databaseType.equalsIgnoreCase("blackpearl") ? "BlackPearl" : "Rio");
                result.setSuggestedTarget(databaseType.equalsIgnoreCase("blackpearl") ? "BlackPearl" : "Rio");
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error fetching storage domains: {}", e.getMessage(), e);
            // Return defaults based on database type
            StorageDomains result = new StorageDomains();
            result.setDomains(new ArrayList<>());
            result.setSuggestedSource(databaseType.equalsIgnoreCase("blackpearl") ? "BlackPearl" : "Rio");
            result.setSuggestedTarget(databaseType.equalsIgnoreCase("blackpearl") ? "BlackPearl" : "Rio");
            return result;
        }
    }

    public static class StorageDomains {
        private List<String> domains;
        private String suggestedSource;
        private String suggestedTarget;
        private String suggestedTapePartition;

        public List<String> getDomains() {
            return domains;
        }

        public void setDomains(List<String> domains) {
            this.domains = domains;
        }

        public String getSuggestedSource() {
            return suggestedSource;
        }

        public void setSuggestedSource(String suggestedSource) {
            this.suggestedSource = suggestedSource;
        }

        public String getSuggestedTarget() {
            return suggestedTarget;
        }

        public void setSuggestedTarget(String suggestedTarget) {
            this.suggestedTarget = suggestedTarget;
        }

        public String getSuggestedTapePartition() {
            return suggestedTapePartition;
        }

        public void setSuggestedTapePartition(String suggestedTapePartition) {
            this.suggestedTapePartition = suggestedTapePartition;
        }
    }
}
