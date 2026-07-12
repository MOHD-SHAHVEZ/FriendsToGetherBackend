package com.gaur.friendstogether.entity;

/**
 * ============================================================================
 * Payment Provider
 * ============================================================================
 *
 * Defines which payment gateway processed the payment.
 *
 * Keeping this as an Enum makes the payment system gateway-independent.
 *
 * Today:
 *  - Razorpay
 *
 * Future:
 *  - Stripe
 *  - Google Play Billing
 *  - Apple In-App Purchase
 *  - PayPal
 * ============================================================================
 */
public enum PaymentProvider {

    /**
     * Razorpay Payment Gateway
     */
    RAZORPAY,

    /**
     * Stripe Payment Gateway
     */
    STRIPE,

    /**
     * Google Play Billing
     */
    GOOGLE_PLAY,

    /**
     * Apple In-App Purchase
     */
    APPLE,

    /**
     * PayPal
     */
    PAYPAL
}
