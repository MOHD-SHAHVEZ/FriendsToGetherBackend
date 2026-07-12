package com.gaur.friendstogether.entity;

/**
 * ============================================================================
 * Payment Verification Status
 * ============================================================================
 *
 * Payment success and Signature verification are two different things.
 *
 * Example
 *
 * Payment Success
 *        ↓
 * Verify Signature
 *        ↓
 * VERIFIED
 *
 * If signature verification fails,
 * coins must NEVER be credited.
 * ============================================================================
 */
public enum PaymentVerificationStatus {

    /**
     * Verification not started yet.
     */
    PENDING,

    /**
     * Payment signature verified successfully.
     */
    VERIFIED,

    /**
     * Signature verification failed.
     */
    FAILED
}
