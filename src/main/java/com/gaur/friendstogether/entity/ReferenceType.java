package com.gaur.friendstogether.entity;

/**
 * Identifies which module generated this wallet transaction.
 */
public enum ReferenceType {
    PAYMENT,
    GIFT,
    REFUND,
    DAILY_REWARD,
    REFERRAL,
    WELCOME_BONUS,
    ADMIN,
    SYSTEM
}
