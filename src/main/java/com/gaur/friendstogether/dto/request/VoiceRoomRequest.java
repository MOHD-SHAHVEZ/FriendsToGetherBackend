package com.gaur.friendstogether.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * VoiceRoomRequest: Room create karte waqt aane wala data.
 */
@Data
public class VoiceRoomRequest {

    @NotBlank(message = "Room title is required")
    @Size(min = 3, max = 50, message = "Title must be between 3 and 50 chars")
    private String title;

    private String password;    // Optional: Agar room lock karna ho

    private boolean isLocked;   // true agar password chahiye

    private boolean isPersonal; // true agar private room ho

    @Max(value = 10, message = "Maximum 10 users allowed in MVP")
    private int maxUsers;       // Default limits ke liye
}