package com.gaur.friendstogether.entity;

/**
 * Roles for Voice Room participants.
 */
public enum RoomRole {
    OWNER,   // Creator of the room
    ADMIN,   // Designated moderators (only for Personal Rooms)
    MEMBER   // Regular listeners/speakers
}