package com.gaur.friendstogether.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private String userId;
    private String username;
    private String email;
    private String nickname;
    private int level;
    private String avatarUrl;
    private String titleBadge;
    private String gender;
    private LocalDate dob;
    private String region;
    private String signature;
}