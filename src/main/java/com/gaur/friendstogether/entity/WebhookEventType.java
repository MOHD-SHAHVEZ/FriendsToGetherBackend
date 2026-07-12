package com.gaur.friendstogether.entity;

/**
 * ============================================================================
 * WebhookEventType
 * ============================================================================
 *
 * Represents supported webhook events received from payment gateways.
 *
 * NOTE
 * ---------------------------------------------------------------------------
 * Service Layer maps gateway-specific event names
 * to these generic event types.
 */
public enum WebhookEventType {

    /**
     * Payment authorized.
     */
    PAYMENT_AUTHORIZED,

    /**
     * Payment successfully captured.
     */
    PAYMENT_CAPTURED,

    /**
     * Payment failed.
     */
    PAYMENT_FAILED,

    /**
     * Order successfully paid.
     */
    ORDER_PAID,

    /**
     * Refund initiated.
     */
    REFUND_CREATED,

    /**
     * Refund successfully processed.
     */
    REFUND_PROCESSED

}
