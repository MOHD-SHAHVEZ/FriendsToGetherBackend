package com.gaur.friendstogether.entity;

/**
 * Voice Room ke andar kis tarah ka message bheja gaya hai.
 */
public enum VoiceRoomMessageType {
    TEXT,   // Normal baatcheet
    IMAGE,  // Photo
    VIDEO,  // Video clips
    AUDIO,  // Voice notes
    FILE,   // Documents ya zip files
    SYSTEM // Room ke automatic messages (e.g., User ka aana/jana, ya Admin dwara kisi ko mute karna)

    /*
     * System-Example: "Mohd Shahvez joined the room" ya "Javed has been muted by Admin."
     */
}