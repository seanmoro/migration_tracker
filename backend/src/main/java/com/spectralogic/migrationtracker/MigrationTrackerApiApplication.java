package com.spectralogic.migrationtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MigrationTrackerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationTrackerApiApplication.class, args);
    }
}
