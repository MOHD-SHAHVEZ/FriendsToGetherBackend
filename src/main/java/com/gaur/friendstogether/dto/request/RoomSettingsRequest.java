package com.gaur.friendstogether.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gaur.friendstogether.entity.RoomCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RoomSettingsRequest: Room ki settings update karne ke liye.
 * Partial update supported — sirf provided fields update honge.
 * Owner ke paas hi ye power hogi.
 */
@Data
public class RoomSettingsRequest {

    @NotBlank(message = "Room ID cannot be empty")
    @Pattern(regexp = "\\d{8,9}", message = "Room ID must be 8-9 digits")
    private String roomId;

    /**
     * Room title (optional for partial update).
     * If provided, must be 3-50 characters.
     */
    @Size(min = 3, max = 50, message = "Title must be between 3 and 50 characters")
    private String title;

    /**
     * Password for the room (required when enabling lock).
     */
    private String password;

    /**
     * Lock status toggle (optional).
     * true = enable password, false = disable password.
     * null = don't change lock status.
     */
    @JsonProperty("isLocked")
    private Boolean isLocked;

    /**
     * Maximum seats (optional, 1-10).
     * null = don't change.
     */
    @Min(value = 1, message = "Minimum 1 seat required")
    @Max(value = 10, message = "Maximum 10 seats allowed")
    private Integer maxSeat;

    /**
     * Room category (optional).
     * null = don't change.
     */
    private RoomCategory category;
}
