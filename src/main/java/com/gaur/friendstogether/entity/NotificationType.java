package com.gaur.friendstogether.entity;

/**
 * User ko milne wale notifications ke types.
 */
public enum NotificationType {
    ROOM_INVITE,
    GIFT_RECEIVED,
    FRIEND_REQUEST,
    ROOM_ACTIVITY,
    SYSTEM_ALERT // App ki taraf se aane wale automatic alerts (e.g., maintenance, security warnings)
}