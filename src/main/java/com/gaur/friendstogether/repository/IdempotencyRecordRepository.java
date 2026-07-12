package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.IdempotencyRecord;
import com.gaur.friendstogether.entity.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * =============================================================================
 * IdempotencyRecordRepository
 * =============================================================================
 *
 * Database operations for the idempotency_records table.
 *
 * Queries use the new method + path approach
 * (no more ResourceType enum).
 * =============================================================================
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    // =========================================================================
    // PRIMARY LOOKUP (for duplicate detection)
    // =========================================================================

    /**
     * Primary lookup: find by key + method + path.
     *
     * This is the main deduplication query:
     *   Frontend sends idempotencyKey
     *     → find record with same key + same endpoint
     *     → If found, return cached response
     *     → If not, proceed with creation
     *
     * Matches the composite unique constraint:
     *   uk_idempotency_key_method_path(user_id, idempotency_key, http_method, request_path)
     */
    Optional<IdempotencyRecord> findByIdempotencyKeyAndHttpMethodAndRequestPath(
            String idempotencyKey,
            String httpMethod,
            String requestPath
    );

    /**
     * Find by key only (any endpoint).
     *
     * Used when we just want to check if a key
     * has been used anywhere.
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    // =========================================================================
    // REVERSE LOOKUPS (by resource)
    // =========================================================================

    /**
     * Find idempotency record by CoinOrder ID.
     *
     * Example: Given orderId = 42 → find its idempotency key.
     * Useful for admin views and debugging.
     *
     * Direct FK lookup — no need for resourceType filter.
     */
    Optional<IdempotencyRecord> findByOrder_Id(Long orderId);

    /**
     * Find idempotency record by Payment ID.
     *
     * Example: Given paymentId = 99 → find its idempotency record.
     * Used for admin views and payment tracking.
     */
    Optional<IdempotencyRecord> findByPaymentId(Long paymentId);

    /**
     * Find record by checkout session ID.
     *
     * Used during webhook processing:
     *   Razorpay webhook → checkout session ID → our record
     *     → linked Payment → process
     */
    Optional<IdempotencyRecord> findByCheckoutSessionId(String checkoutSessionId);

    // =========================================================================
    // SCHEDULER QUERIES
    // =========================================================================

    /**
     * Find expired records with given status for cleanup.
     *
     * Scheduler marks IN_PROGRESS records as EXPIRED
     * after their expiresAt time passes.
     */
    @Query("SELECT r FROM IdempotencyRecord r WHERE r.status = :status AND r.expiresAt <= :now")
    List<IdempotencyRecord> findExpiredRecords(
            @Param("status") IdempotencyStatus status,
            @Param("now") LocalDateTime now
    );

    // =========================================================================
    // ANALYTICS / RATE LIMITING (future)
    // =========================================================================

    /**
     * Count records per user per endpoint per status.
     *
     * Useful for:
     *   - Rate limiting (future)
     *   - Monitoring abnormal patterns
     *   - Admin analytics
     */
    long countByUser_IdAndHttpMethodAndRequestPathAndStatus(
            Long userId,
            String httpMethod,
            String requestPath,
            IdempotencyStatus status
    );
}
