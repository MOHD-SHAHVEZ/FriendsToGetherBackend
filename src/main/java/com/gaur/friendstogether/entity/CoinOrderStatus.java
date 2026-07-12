package com.gaur.friendstogether.entity;

/**
 * Coin Order Lifecycle
 */
public enum CoinOrderStatus {

    /**
     * Order create ho gaya.
     * Payment abhi start nahi hui.
     */
    CREATED,

    /**
     * Payment processing me hai.
     */
    PAYMENT_PENDING,

    /**
     * Payment successful.
     *
     * Wallet me coins credit hone ka process start hoga.
     */
    PAYMENT_SUCCESS,

    /**
     * Payment fail ho gayi.
     */
    PAYMENT_FAILED,

    /**
     * Order expire ho gaya.
     */
    EXPIRED,

    /**
     * Order cancel kar diya gaya.
     */
    CANCELLED
}
