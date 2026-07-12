package com.gaur.friendstogether.entity;

/**
 * ============================================================================
 * Wallet Status
 * ============================================================================
 *
 * Defines the current operational state of the user's wallet.
 */
public enum WalletStatus {

    /**
     * Wallet is fully operational.
     */
    ACTIVE,

    /**
     * Wallet temporarily frozen.
     *
     * Example:
     * Suspicious Activity
     * Chargeback Investigation
     */
    FROZEN,

    /**
     * Wallet permanently blocked.
     *
     * User cannot perform any wallet operations.
     */
    BLOCKED,

    /**
     * Wallet permanently closed.
     */
    CLOSED
}
