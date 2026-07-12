package com.gaur.friendstogether.entity;

/**
 * ============================================================================
 * Payment Status
 * ============================================================================
 *
 * Represents the current lifecycle of a payment.
 *
 * IMPORTANT
 * ---------------------------------------------------------------------------
 * This status belongs to the Payment itself.
 *
 * It does NOT indicate whether wallet coins have been credited.
 *
 * Wallet processing is handled separately.
 * ============================================================================
 */
public enum PaymentStatus {

    /**
     * Payment record created.
     * Gateway payment not started yet.
     */
    CREATED,

    /**
     * Payment initiated by customer.
     */
    PENDING,

    /**
     * Payment authorized by bank.
     * Amount reserved but not yet captured.
     */
    AUTHORIZED,

    /**
     * Payment successfully captured.
     */
    CAPTURED,

    /**
     * Payment failed.
     */
    FAILED,

    /**
     * Payment cancelled by user.
     */
    CANCELLED,

    /**
     * Payment refunded.
     */
    REFUNDED,

    /**
     * Payment expired.
     */
    EXPIRED
}
