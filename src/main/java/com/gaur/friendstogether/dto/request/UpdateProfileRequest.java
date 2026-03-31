package com.gaur.friendstogether.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    private String nickname;
    private String avatarUrl;
    private String gender;
    private LocalDate dob; // Date of Birth
    private String region;
    private String titleBadge;
    private String signature;
}