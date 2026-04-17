package com.gaur.friendstogether.entity;

/**
 * Transaction ki current halat.
 */
public enum TransactionStatus {
    PENDING, // Process ho raha hai
    SUCCESS, // Safal raha
    FAILED   // Kisi wajah se fail ho gaya
}