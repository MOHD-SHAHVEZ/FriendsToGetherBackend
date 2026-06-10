package com.gaur.friendstogether.entity;

/**
 * Room mein bheje jaane wale Gifts ki list.
 * Keemat (Coins) logic hum service layer mein handle karenge.
 */
public enum GiftType {
    // --- Basic Gifts (Saste wale) ---
    LUCKY_STAR,    // 5 Coins
    HEART,         // 10 Coins
    ROSE,          // 20 Coins
    THUMBS_UP,     // 50 Coins

    // --- Mid-Range Gifts ---
    CHOCOLATE,     // 100 Coins
    COFFEE,        // 200 Coins
    CAKE,          // 500 Coins
    FIREWORKS,     // 800 Coins

    // --- Premium Gifts ---
    CHICKEN_DINNER, // 1000 Coins
    GOLD_MEDAL,     // 2000 Coins
    MAGIC_CARPET,   // 3500 Coins
    DRAGON,         // 5000 Coins

    // --- Luxury Gifts (Ekdum Mehnge) ---
    SUPER_CAR,      // 10,000 Coins
    PRIVATE_JET,    // 25,000 Coins
    ROYAL_CASTLE    // 50,000 Coins
}