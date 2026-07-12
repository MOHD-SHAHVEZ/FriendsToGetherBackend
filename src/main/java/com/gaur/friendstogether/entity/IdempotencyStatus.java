package com.gaur.friendstogether.entity;

/**
 * =============================================================================
 * IdempotencyStatus
 * =============================================================================
 *
 * Lifecycle status of an idempotency record.
 *
 * Inspired by production payment microservice patterns.
 *
 * Flow:
 *
 * IN_PROGRESS → COMPLETED  (operation succeeded end-to-end)
 * IN_PROGRESS → FAILED     (operation failed, client can retry with new key)
 * IN_PROGRESS → CANCELLED  (user or admin cancelled the operation)
 * IN_PROGRESS → EXPIRED    (key timed out, scheduler cleanup)
 * =============================================================================
 */
public enum IdempotencyStatus {

    /**
     * Operation is in progress.
     *
     * The resource (order / payment) has been created,
     * but the full flow is not yet complete.
     *
     * Duplicate requests with this key will return
     * the cached response.
     */
    IN_PROGRESS,

    /**
     * Full flow completed successfully.
     *
     * Example:
     * Order Created → Payment Success → Coins Credited
     *
     * Record stays for audit, eventually cleaned up.
     */
    COMPLETED,

    /**
     * The operation failed.
     *
     * Client should generate a new idempotency key
     * and retry the request.
     */
    FAILED,

    /**
     * Operation was cancelled by user or admin.
     *
     * Example:
     * User cancelled the order before paying.
     * Admin cancelled the payment.
     */
    CANCELLED,

    /**
     * Key has expired.
     *
     * Scheduler marks IN_PROGRESS records as EXPIRED
     * after their expiresAt time passes.
     *
     * Expired records are eligible for cleanup/archival.
     */
    EXPIRED
}
