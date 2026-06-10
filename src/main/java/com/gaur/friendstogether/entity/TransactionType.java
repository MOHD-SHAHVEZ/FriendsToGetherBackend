package com.gaur.friendstogether.entity;

/**
 * Transaction ka nature: Paisa aaya ya gaya.
 */
public enum TransactionType {
    CREDIT, // Jab coins add hon (Daily Bonus, Reward)
    DEBIT   // Jab coins kharch hon (Gift bhejna)
}