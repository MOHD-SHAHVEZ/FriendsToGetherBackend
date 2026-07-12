package com.gaur.friendstogether.entity;

/**
 * Defines the exact business reason for this wallet transaction.
 */
public enum TransactionReason {
    PURCHASE,
    GIFT_SENT,
    GIFT_RECEIVED,
    DAILY_REWARD,
    WELCOME_BONUS,
    REFERRAL,
    REFUND,
    EXPIRED,
    REVERSAL,
    ADMIN_CREDIT,
    ADMIN_DEBIT,
    SYSTEM_ADJUSTMENT
}