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
        log.info("Running database migrations...");
        migrateAuthProvider();
        migratePasswordColumnNullable();
    }

    /**
     * Migration 1: Set auth_provider = 'LOCAL' for legacy users who have NULL.
     */
    private void migrateAuthProvider() {
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

    /**
     * Migration 2: Make password column nullable for OAuth users.
     * Hibernate ddl-auto=update does NOT alter existing column constraints.
     * The User entity declares @Column(nullable = true) for password,
     * but the MySQL column was originally created as NOT NULL.
     * OAuth users (Google, Apple, etc.) have password = NULL by design.
     */
    private void migratePasswordColumnNullable() {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NULL"
            );
            log.info("Migration: 'password' column is now nullable (required for OAuth users).");
        } catch (Exception e) {
            log.error("Failed to alter password column: {}", e.getMessage());
            // Non-fatal: if already nullable, some MySQL versions may throw. Safe to continue.
        }
    }
}
