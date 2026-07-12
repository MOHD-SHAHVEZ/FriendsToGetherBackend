package com.gaur.friendstogether.entity;

/**
 * ============================================================================
 * Payment Method
 * ============================================================================
 *
 * Represents how the customer actually paid.
 *
 * Example:
 * User selects Razorpay
 * ↓
 * Pays using UPI
 *
 * Provider = RAZORPAY
 * Method = UPI
 * ============================================================================
 */
public enum PaymentMethod {

    /**
     * Unified Payment Interface
     */
    UPI,

    /**
     * Credit / Debit Card
     */
    CARD,

    /**
     * Internet Banking
     */
    NET_BANKING,

    /**
     * Mobile Wallet
     *
     * Example:
     * Amazon Pay
     * PhonePe Wallet
     */
    WALLET,

    /**
     * EMI Payment
     */
    EMI,

    /**
     * Buy Now Pay Later
     */
    BNPL,

    /**
     * Unknown / Not Available
     */
    UNKNOWN
}
