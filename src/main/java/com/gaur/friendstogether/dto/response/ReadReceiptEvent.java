package com.gaur.friendstogether.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Type-safe WebSocket payload for read receipt notifications.
 * Sent to the original sender when their messages are marked as READ.
 * Destination: /user/queue/read-receipts
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReadReceiptEvent {
    private String roomId;
    private String status;      // Always "READ"
    private String receiver;    // Email of the user who read the messages
    private int updatedCount;   // Number of messages marked as READ
}
