package com.gaur.friendstogether.entity;

/**
 * ======================================================================
 * RefundReason
 * ======================================================================
 *
 * Standard business reasons for refund.
 */
public enum RefundReason {

    /**
     * Customer requested refund.
     */
    CUSTOMER_REQUEST,

    /**
     * Duplicate payment received.
     */
    DUPLICATE_PAYMENT,

    /**
     * Payment succeeded but business process failed.
     */
    PAYMENT_FAILURE,

    /**
     * Fraud detected.
     */
    FRAUD_DETECTED,

    /**
     * Manual admin initiated refund.
     */
    ADMIN_ACTION,

    /**
     * Technical or system issue.
     */
    TECHNICAL_ERROR

}
