package com.gaur.friendstogether.dto.response;

import com.gaur.friendstogether.entity.RoomCategory;
import lombok.*;

import java.time.LocalDateTime;

/**
 * VoiceRoomResponse:
 * Discovery & Search APIs ke liye safe response DTO.
 * Sensitive fields (passwordHash, internal IDs) expose nahi karega.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceRoomResponse {

    // Public Room ID (8-digit ya custom format)
    private String roomId;

    // Room ka display title
    private String title;

    // Owner ka display name (nickname preferred)
    private String ownerName;

    // Owner ka Game ID (9-digit userId)
    private String ownerUserId;

    // Active members count (cached field)
    private int memberCount;

    // Room level (gifting / activity se increase hota hai)
    private int roomLevel;

    // Lock status (UI decision ke liye) added by antigravity for voiceroom joining
    // problem solve karne ka liya
    @com.fasterxml.jackson.annotation.JsonProperty("isLocked")
    private boolean isLocked;

    // Category (optional - future filters ke liye useful)
    private RoomCategory category;

    // Room creation time (sorting / recency ke liye)
    private LocalDateTime createdAt;

    // Personal room flag (UI ke liye — personal vs public distinction)
    @com.fasterxml.jackson.annotation.JsonProperty("isPersonal")
    private boolean isPersonal;

    // Maximum seats in the room
    private int maxSeat;

    // Room status (ACTIVE / CLOSED)
    private com.gaur.friendstogether.entity.RoomStatus status;
}