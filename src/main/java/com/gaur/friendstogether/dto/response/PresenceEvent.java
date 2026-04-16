package com.gaur.friendstogether.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Type-safe WebSocket payload for user presence (online/offline) notifications.
 * Broadcast to all connected clients via /topic/status
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PresenceEvent {
    private String user;    // Email of the user whose status changed
    private String status;  // "ONLINE" or "OFFLINE"
}
