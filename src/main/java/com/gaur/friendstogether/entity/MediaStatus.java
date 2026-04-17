package com.gaur.friendstogether.entity;

/**
 * Gaane ya Video ki current state define karta hai.
 */
public enum MediaStatus {
    PENDING,  // Line mein hai (Waiting in Queue)
    PLAYING,  // Abhi room mein baj raha hai
    PLAYED,   // Poora khatam ho gaya hai
    SKIPPED   // Admin ya Owner ne skip kar diya
}