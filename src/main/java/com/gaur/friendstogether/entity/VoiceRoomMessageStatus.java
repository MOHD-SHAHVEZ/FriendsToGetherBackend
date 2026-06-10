package com.gaur.friendstogether.entity;

/**
 * Message bhejne ke baad uska current status kya hai.
 */
public enum VoiceRoomMessageStatus {
    SENT,      // Server tak pahunch gaya
    DELIVERED, // Sabko deliver ho gaya
    READ       // Dekh liya gaya (Redis/LastID logic se handle hoga)
}