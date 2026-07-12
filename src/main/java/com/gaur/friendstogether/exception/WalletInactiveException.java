package com.gaur.friendstogether.exception;

/**
 * WalletInactiveException
 *
 * Jab wallet ka status ACTIVE nahi hai.
 *
 * HTTP Status: 403 Forbidden
 *
 * Possible statuses:
 * - FROZEN   → Suspicious activity / Chargeback investigation
 * - BLOCKED  → Permanently blocked by admin
 * - CLOSED   → Account closed
 *
 * User ko clear message dikhana chahiye:
 * "Your wallet is temporarily frozen. Please contact support."
 */
public class WalletInactiveException extends RuntimeException {

    public WalletInactiveException(String message) {
        super(message);
    }
}
