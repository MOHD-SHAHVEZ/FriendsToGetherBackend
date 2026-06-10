package com.gaur.friendstogether.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gaur.friendstogether.entity.RoomRole; // Ensure this import is correct
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomMemberResponse {

    private String userId;      // 9-digit Unique ID
    private String nickname;    // Profile Nickname or Username
    private String avatarUrl;   // User Profile Picture

    private RoomRole role;      // Enum (OWNER, ADMIN, MEMBER)

    private boolean isMuted;    // UI ke liye (Mic icon dikhane ke liye)

    private LocalDateTime joinedAt;
}