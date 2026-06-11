package com.gaur.friendstogether.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migration runner that executes on application startup.
 * Safely migrates existing user rows that lack an auth_provider
 * value by setting them to 'LOCAL'.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("Running database migrations for existing legacy users...");
        try {
            // Safe SQL update: only touches rows where auth_provider is missing
            int updatedRows = jdbcTemplate.update(
                "UPDATE users SET auth_provider = 'LOCAL' WHERE auth_provider IS NULL"
            );
            if (updatedRows > 0) {
                log.info("Successfully migrated {} legacy users to auth_provider = 'LOCAL'.", updatedRows);
            } else {
                log.info("No legacy users required auth_provider migration.");
            }
        } catch (Exception e) {
            log.error("Failed to execute auth_provider migration: {}", e.getMessage());
            // We do not throw to prevent application startup failure. 
            // The @PostLoad fallback in User.java provides defensive runtime safety.
        }
    }
}
