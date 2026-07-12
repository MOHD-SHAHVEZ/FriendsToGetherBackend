package com.gaur.friendstogether.scheduler;

import com.gaur.friendstogether.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * =============================================================================
 * IdempotencyCleanupScheduler
 * =============================================================================
 *
 * Scheduled job to cleanup expired idempotency records.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * WHY THIS EXISTS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * IdempotencyRecord keys have a 24-hour TTL.
 * After expiry, the records serve no functional purpose
 * (the order/payment has either completed or expired).
 *
 * This scheduler marks ACTIVE records as EXPIRED after
 * their expiresAt time passes.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * SCHEDULE
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * Runs every 1 hour.
 * Less frequent than order expiry because idempotency cleanup
 * is not time-critical.
 *
 * =============================================================================
 */
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyService idempotencyService;

    /**
     * Every 1 hour, mark expired idempotency records as EXPIRED.
     *
     * fixedRate = 3600000 ms = 1 hour
     * initialDelay = 120000 ms = 2 minutes (wait for app startup)
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 120_000)
    public void cleanupExpiredRecords() {

        logger.info("[SCHEDULER] Running idempotency cleanup...");

        try {
            int cleanedCount = idempotencyService.cleanupExpiredRecords();

            if (cleanedCount > 0) {
                logger.info("[SCHEDULER] Idempotency cleanup complete. Expired {} records.", cleanedCount);
            } else {
                logger.debug("[SCHEDULER] No expired idempotency records found.");
            }
        } catch (Exception e) {
            // Scheduler must NEVER crash the application.
            logger.error("[SCHEDULER] Idempotency cleanup failed: {}", e.getMessage(), e);
        }
    }
}
