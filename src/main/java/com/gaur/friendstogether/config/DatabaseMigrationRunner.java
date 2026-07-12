package com.gaur.friendstogether.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import org.springframework.core.annotation.Order;

/**
 * Migration runner that executes on application startup.
 * Safely migrates existing user rows that lack an auth_provider
 * value by setting them to 'LOCAL'.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("Running database migrations...");
        migrateAuthProvider();
        migratePasswordColumnNullable();
        migrateWalletTransactionsEnums();
        migrateWalletTransactionsDropUserId();
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

    /**
     * Migration 3: Fix ENUM truncation errors in wallet_transactions by converting them to VARCHAR(255).
     * Hibernate ddl-auto=update does not reliably update ENUM column definitions in MySQL
     * when new enum values (like WELCOME_BONUS) are added.
     */
    private void migrateWalletTransactionsEnums() {
        try {
            jdbcTemplate.execute("ALTER TABLE wallet_transactions MODIFY COLUMN reason VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE wallet_transactions MODIFY COLUMN type VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE wallet_transactions MODIFY COLUMN reference_type VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE wallet_transactions MODIFY COLUMN status VARCHAR(255)");
            log.info("Migration: 'wallet_transactions' enum columns are now VARCHAR(255) to prevent truncation errors.");
        } catch (Exception e) {
            log.error("Failed to alter wallet_transactions columns: {}", e.getMessage());
        }
    }

    /**
     * Migration 4: Fix stale 'user_id' NOT NULL column in wallet_transactions.
     * The WalletTransaction entity only uses wallet_id, not user_id.
     * This orphan NOT NULL column causes: "Field 'user_id' doesn't have a default value".
     *
     * Fix: Make it nullable so inserts succeed without providing user_id.
     * Column is kept (not dropped) for production safety.
     */
    private void migrateWalletTransactionsDropUserId() {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE wallet_transactions MODIFY COLUMN user_id BIGINT NULL DEFAULT NULL"
            );
            log.info("Migration: wallet_transactions.user_id is now nullable.");
        } catch (Exception e) {
            // Column may not exist — safe to ignore
            log.debug("wallet_transactions.user_id migration skipped: {}", e.getMessage());
        }
    }
}
