package com.gaur.friendstogether.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * RoleChangeRequest:
 * Promote aur Demote actions ke liye strict validation ke saath Request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleChangeRequest {

    /**
     * Room ID: Must be exactly 9 digits
     */
    @NotBlank(message = "Room ID cannot be empty")
    @Pattern(regexp = "\\d{9}", message = "Room ID must be exactly 9 digits")
    private String roomId;

    /**
     * Target User ID: Must be exactly 9 digits
     */
    @NotBlank(message = "Target User ID cannot be empty")
    @Pattern(regexp = "\\d{9}", message = "Target User ID must be exactly 9 digits")
    private String targetUserId;
}