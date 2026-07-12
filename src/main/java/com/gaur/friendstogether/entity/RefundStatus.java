package com.gaur.friendstogether.entity;

/**
 * ======================================================================
 * RefundStatus
======================================================================
 *
 * Represents current refund lifecycle.
 */
public enum RefundStatus {

    /**
     * Refund request created.
     */
    REQUESTED,

    /**
     * Refund processing is in progress.
     */
    PROCESSING,

    /**
     * Refund completed successfully.
     */
    COMPLETED,

    /**
     * Refund failed.
     */
    FAILED,

    /**
     * Refund cancelled.
     */
    CANCELLED

}
