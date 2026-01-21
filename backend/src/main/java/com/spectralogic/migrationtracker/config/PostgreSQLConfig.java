package com.spectralogic.migrationtracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class PostgreSQLConfig {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLConfig.class);

    @Value("${postgres.blackpearl.host:localhost}")
    private String blackpearlHost;

    @Value("${postgres.blackpearl.port:5432}")
    private int blackpearlPort;

    @Value("${postgres.blackpearl.database:tapesystem}")
    private String blackpearlDatabase;

    @Value("${postgres.blackpearl.username:postgres}")
    private String blackpearlUsername;

    @Value("${postgres.blackpearl.password:}")
    private String blackpearlPassword;

    @Value("${postgres.rio.host:localhost}")
    private String rioHost;

    @Value("${postgres.rio.port:5432}")
    private int rioPort;

    @Value("${postgres.rio.database:rio_db}")
    private String rioDatabase;

    @Value("${postgres.rio.username:postgres}")
    private String rioUsername;

    @Value("${postgres.rio.password:}")
    private String rioPassword;

    public DataSource getBlackPearlDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String url = String.format("jdbc:postgresql://%s:%d/%s", blackpearlHost, blackpearlPort, blackpearlDatabase);
        dataSource.setUrl(url);
        dataSource.setUsername(blackpearlUsername);
        dataSource.setPassword(blackpearlPassword != null ? blackpearlPassword : "");
        
        // Test connection and log details
        try (Connection conn = dataSource.getConnection()) {
            logger.debug("Successfully connected to BlackPearl database at {}", url);
        } catch (SQLException e) {
            logger.warn("Cannot connect to BlackPearl database at {}: {}", url, e.getMessage());
        }
        
        return dataSource;
    }

    public DataSource getRioDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String url = String.format("jdbc:postgresql://%s:%d/%s", rioHost, rioPort, rioDatabase);
        dataSource.setUrl(url);
        dataSource.setUsername(rioUsername);
        dataSource.setPassword(rioPassword != null ? rioPassword : "");
        
        // Test connection and log details
        try (Connection conn = dataSource.getConnection()) {
            logger.debug("Successfully connected to Rio database at {}", url);
        } catch (SQLException e) {
            logger.warn("Cannot connect to Rio database at {}: {}", url, e.getMessage());
        }
        
        return dataSource;
    }

    @SuppressWarnings("null")
    public JdbcTemplate getBlackPearlJdbcTemplate() {
        DataSource ds = getBlackPearlDataSource();
        return new JdbcTemplate(ds);
    }

    @SuppressWarnings("null")
    public JdbcTemplate getRioJdbcTemplate() {
        DataSource ds = getRioDataSource();
        return new JdbcTemplate(ds);
    }
}
