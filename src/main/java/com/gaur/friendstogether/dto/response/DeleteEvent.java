package com.gaur.friendstogether.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Type-safe WebSocket payload for message deletion notifications.
 * Sent to affected users when a message is deleted.
 * Destination: /user/queue/delete-events
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeleteEvent {
    private Long messageId;
    private String type;        // Message type after deletion (e.g., "SYSTEM" for delete-all)
    private String deleteType;  // "ME" or "ALL"
    private String content;     // Masked content for delete-all, original for delete-me
}
