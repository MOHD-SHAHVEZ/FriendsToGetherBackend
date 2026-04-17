package com.gaur.friendstogether.entity;

/**
 * Transaction hone ki asli wajah.
 */
public enum TransactionReason {
    DAILY_BONUS,      // Rozana login par milne wale coins
    GIFT_SENT,        // Gift bhejne par kata paisa
    GIFT_RECEIVED,    // Gift milne par (agar coins mile hon)
    GIFT_REWARD,      // 20-80 random cashback coins
    PURCHASE,         // Real money se kharide gaye coins
    ADMIN_ADJUSTMENT  // Admin dwara coins kam ya zyada karna
}