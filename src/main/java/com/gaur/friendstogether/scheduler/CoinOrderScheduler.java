package com.gaur.friendstogether.scheduler;

import com.gaur.friendstogether.service.CoinOrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * =============================================================================
 * CoinOrderScheduler
 * =============================================================================
 *
 * Scheduled job to auto-expire stale CREATED orders.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * WHY THIS EXISTS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * When a user clicks "Buy Now", a CoinOrder is created with status = CREATED
 * and an expiresAt = now + 30 minutes.
 *
 * If the user never proceeds to payment, the order should not stay CREATED
 * forever. The scheduler marks it EXPIRED so:
 *
 * 1. The user can place a new order for the same plan
 * 2. The pending order check doesn't block new purchases
 * 3. Admin dashboard shows accurate active order counts
 * 4. Database stays clean
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * SCHEDULE
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * Runs every 5 minutes.
 * Queries: SELECT * FROM coin_orders WHERE status = 'CREATED' AND expires_at <= NOW()
 * Updates: status = EXPIRED, remarks = "Auto-expired by system scheduler"
 *
 * =============================================================================
 */
@Component
@RequiredArgsConstructor
public class CoinOrderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CoinOrderScheduler.class);

    private final CoinOrderService coinOrderService;

    /**
     * Every 5 minutes, find and expire stale CREATED orders.
     *
     * fixedRate = 300000 ms = 5 minutes
     * initialDelay = 60000 ms = 1 minute (wait for app startup)
     */
    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void expireStaleOrders() {

        logger.info("[SCHEDULER] Running order expiry check...");

        try {
            int expiredCount = coinOrderService.expireStaleOrders();

            if (expiredCount > 0) {
                logger.info("[SCHEDULER] Order expiry complete. Expired {} orders.", expiredCount);
            } else {
                logger.debug("[SCHEDULER] No stale orders found.");
            }
        } catch (Exception e) {
            // Scheduler must NEVER crash the application.
            // Log error and continue — next run will retry.
            logger.error("[SCHEDULER] Order expiry failed: {}", e.getMessage(), e);
        }
    }
}
