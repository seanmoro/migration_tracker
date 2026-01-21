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
            // 1. ds3.storage_domain table with name column (BlackPearl)
            // 2. storage_domains table with name column (public schema)
            // 3. domains table with name column
            // 4. brokers table with name column (for Rio)
            // 5. storage_domain column in various tables
            // 6. domain column in various tables
            
            // First, try ds3.storage_domain (most common for BlackPearl)
            try {
                List<String> ds3StorageDomains = jdbc.query(
                    "SELECT DISTINCT name FROM ds3.storage_domain WHERE name IS NOT NULL AND name != '' ORDER BY name",
                    (rs, rowNum) -> {
                        String value = rs.getString("name");
                        return value != null ? value : "";
                    }
                );
                if (!ds3StorageDomains.isEmpty()) {
                    domains.addAll(ds3StorageDomains);
                    logger.info("Found {} storage domains from ds3.storage_domain.name", ds3StorageDomains.size());
                }
            } catch (Exception e) {
                logger.debug("Could not query ds3.storage_domain: {}", e.getMessage());
            }
            
            // Then try public schema and other schemas
            String[] schemas = {"public", "ds3"};
            String[] tableNames = {"storage_domains", "domains", "brokers", "storage_domain", "domain"};
            String[] columnNames = {"name", "domain_name", "storage_domain", "domain", "broker_name"};
            
            for (String schema : schemas) {
                for (String tableName : tableNames) {
                    for (String columnName : columnNames) {
                        // Skip ds3.storage_domain.name as we already queried it
                        if (schema.equals("ds3") && tableName.equals("storage_domain") && columnName.equals("name")) {
                            continue;
                        }
                        
                        try {
                            // Check if table exists
                            List<String> tables = jdbc.query(
                                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                                (rs, rowNum) -> rs.getString("table_name"),
                                schema, tableName
                            );
                            
                            if (tables.isEmpty()) {
                                continue;
                            }
                            
                            // Check if column exists
                            List<String> columns = jdbc.query(
                                "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                                (rs, rowNum) -> rs.getString("column_name"),
                                schema, tableName, columnName
                            );
                            
                            if (columns.isEmpty()) {
                                continue;
                            }
                            
                            // Query distinct values
                            @SuppressWarnings("null")
                            List<String> values = jdbc.query(
                                String.format("SELECT DISTINCT %s FROM %s.%s WHERE %s IS NOT NULL AND %s != '' ORDER BY %s", 
                                    columnName, schema, tableName, columnName, columnName, columnName),
                                (rs, rowNum) -> {
                                    @SuppressWarnings("null")
                                    String value = rs.getString(columnName);
                                    return value != null ? value : "";
                                }
                            );
                            
                            if (!values.isEmpty()) {
                                domains.addAll(values);
                                logger.info("Found {} storage domains from {}.{}.{}", values.size(), schema, tableName, columnName);
                            }
                        } catch (Exception e) {
                            logger.debug("Could not query {}.{}.{}: {}", schema, tableName, columnName, e.getMessage());
                        }
                    }
                }
            }
            
            // Also try to find domains in any column that might contain domain info
            // Check both public and ds3 schemas
            for (String schema : new String[]{"public", "ds3"}) {
                try {
                    // Get all tables in this schema
                    List<String> allTables = jdbc.query(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name",
                        (rs, rowNum) -> rs.getString("table_name"),
                        schema
                    );
                    
                    // For each table, check for columns with "domain" or "broker" in the name
                    for (String tableName : allTables) {
                        try {
                            List<String> domainColumns = jdbc.query(
                                "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%')",
                                (rs, rowNum) -> rs.getString("column_name"),
                                schema, tableName
                            );
                            
                            for (String columnName : domainColumns) {
                                try {
                                    @SuppressWarnings("null")
                                    List<String> values = jdbc.query(
                                        String.format("SELECT DISTINCT %s FROM %s.%s WHERE %s IS NOT NULL AND %s != '' ORDER BY %s LIMIT 50", 
                                            columnName, schema, tableName, columnName, columnName, columnName),
                                        (rs, rowNum) -> {
                                            @SuppressWarnings("null")
                                            String value = rs.getString(columnName);
                                            return value != null ? value : "";
                                        }
                                    );
                                    
                                    if (!values.isEmpty()) {
                                        domains.addAll(values);
                                        logger.debug("Found {} values from {}.{}.{}", values.size(), schema, tableName, columnName);
                                    }
                                } catch (Exception e) {
                                    logger.debug("Could not query {}.{}.{}: {}", schema, tableName, columnName, e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Could not check columns for table {}.{}: {}", schema, tableName, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not list tables in schema {}: {}", schema, e.getMessage());
                }
            }
            
            // Convert to sorted list
            List<String> domainList = new ArrayList<>(domains);
            domainList.sort(String::compareToIgnoreCase);
            
            logger.info("Found {} unique storage domains from {} database ({})", domainList.size(), databaseType, actualDatabaseName);
            
            // Log what we found for debugging
            if (domainList.isEmpty()) {
                logger.warn("No storage domains found in database {}. Attempted to query tables: storage_domains, domains, brokers, and columns containing 'domain' or 'broker'", actualDatabaseName);
                
                // Try to list all tables for debugging (check both public and ds3 schemas)
                try {
                    for (String schema : new String[]{"public", "ds3"}) {
                        List<String> allTables = jdbc.query(
                            "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name",
                            (rs, rowNum) -> rs.getString("table_name"),
                            schema
                        );
                        logger.info("Available tables in schema {} of database {}: {}", schema, actualDatabaseName, allTables);
                        
                        // List all columns that might contain domain info
                        List<Map<String, String>> domainColumns = jdbc.query(
                            "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = ? AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%') ORDER BY table_name, column_name",
                            (rs, rowNum) -> {
                                Map<String, String> col = new HashMap<>();
                                col.put("table", rs.getString("table_name"));
                                col.put("column", rs.getString("column_name"));
                                return col;
                            },
                            schema
                        );
                        if (!domainColumns.isEmpty()) {
                            logger.info("Found potential domain/broker columns in schema {}: {}", schema, domainColumns);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not list tables for debugging: {}", e.getMessage());
                }
            } else {
                logger.info("Storage domains found: {}", domainList);
            }
            
            // Query tape partitions
            List<String> tapePartitions = new ArrayList<>();
            try {
                // First, try the known table: tape.tape_partition.name (most common)
                try {
                    List<String> values = jdbc.query(
                        "SELECT DISTINCT name FROM tape.tape_partition WHERE name IS NOT NULL AND name != '' ORDER BY name",
                        (rs, rowNum) -> {
                            String value = rs.getString("name");
                            return value != null ? value : "";
                        }
                    );
                    if (!values.isEmpty()) {
                        tapePartitions.addAll(values);
                        logger.info("Found {} tape partitions from tape.tape_partition.name", values.size());
                    }
                } catch (Exception e) {
                    logger.debug("Could not query tape.tape_partition.name: {}", e.getMessage());
                }
                
                // If not found, try other common table names for tape partitions
                if (tapePartitions.isEmpty()) {
                    String[] tapePartitionTables = {"tape_partitions", "tape_partition", "partitions", "partition"};
                    String[] tapePartitionColumns = {"name", "partition_name", "tape_partition"};
                    
                    for (String schema : new String[]{"public", "ds3", "tape"}) {
                        for (String tableName : tapePartitionTables) {
                            // Skip tape.tape_partition since we already tried it
                            if (schema.equals("tape") && tableName.equals("tape_partition")) {
                                continue;
                            }
                            
                            for (String columnName : tapePartitionColumns) {
                                try {
                                    // Check if table exists
                                    List<String> tables = jdbc.query(
                                        "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                                        (rs, rowNum) -> rs.getString("table_name"),
                                        schema, tableName
                                    );
                                    
                                    if (tables.isEmpty()) {
                                        continue;
                                    }
                                    
                                    // Check if column exists
                                    List<String> columns = jdbc.query(
                                        "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                                        (rs, rowNum) -> rs.getString("column_name"),
                                        schema, tableName, columnName
                                    );
                                    
                                    if (columns.isEmpty()) {
                                        continue;
                                    }
                                    
                                    // Query distinct values
                                    @SuppressWarnings("null")
                                    List<String> values = jdbc.query(
                                        String.format("SELECT DISTINCT %s FROM %s.%s WHERE %s IS NOT NULL AND %s != '' ORDER BY %s", 
                                            columnName, schema, tableName, columnName, columnName, columnName),
                                        (rs, rowNum) -> {
                                            @SuppressWarnings("null")
                                            String value = rs.getString(columnName);
                                            return value != null ? value : "";
                                        }
                                    );
                                    
                                    if (!values.isEmpty()) {
                                        tapePartitions.addAll(values);
                                        logger.info("Found {} tape partitions from {}.{}.{}", values.size(), schema, tableName, columnName);
                                        break; // Found partitions, no need to check other columns for this table
                                    }
                                } catch (Exception e) {
                                    logger.debug("Could not query {}.{}.{}: {}", schema, tableName, columnName, e.getMessage());
                                }
                            }
                            if (!tapePartitions.isEmpty()) {
                                break; // Found partitions, no need to check other tables
                            }
                        }
                        if (!tapePartitions.isEmpty()) {
                            break; // Found partitions, no need to check other schemas
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not query tape partitions: {}", e.getMessage());
            }
            
            // Remove duplicates and sort
            List<String> uniqueTapePartitions = new ArrayList<>(new HashSet<>(tapePartitions));
            uniqueTapePartitions.sort(String::compareToIgnoreCase);
            
            StorageDomains result = new StorageDomains();
            result.setDomains(domainList);
            result.setTapePartitions(uniqueTapePartitions);
            
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
            
            // Suggest first tape partition if available
            if (!uniqueTapePartitions.isEmpty()) {
                result.setSuggestedTapePartition(uniqueTapePartitions.get(0));
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
        private List<String> tapePartitions;
        private String suggestedSource;
        private String suggestedTarget;
        private String suggestedTapePartition;

        public List<String> getDomains() {
            return domains;
        }

        public void setDomains(List<String> domains) {
            this.domains = domains;
        }

        public List<String> getTapePartitions() {
            return tapePartitions;
        }

        public void setTapePartitions(List<String> tapePartitions) {
            this.tapePartitions = tapePartitions;
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
