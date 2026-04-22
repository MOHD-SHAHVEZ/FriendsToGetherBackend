package com.gaur.friendstogether.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SocialUserResponse {
    private String userId; // 9-digit ID
    private String nickname;
    private String avatarUrl;
    private int level;
    private String status; // "STRANGER", "PENDING", "FRIENDS", "SELF"
    private Long friendsCount;
    private String email;
}