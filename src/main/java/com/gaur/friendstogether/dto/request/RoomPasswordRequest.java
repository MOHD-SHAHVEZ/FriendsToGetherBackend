package com.gaur.friendstogether.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * RoomPasswordRequest: Room ka password enable/disable/update karne ke liye.
 * Owner ke paas hi ye power hogi.
 */
@Data
public class RoomPasswordRequest {

    @NotBlank(message = "Room ID cannot be empty")
    @Pattern(regexp = "\\d{8,9}", message = "Room ID must be 8-9 digits")
    private String roomId;

    /**
     * New password (Required when enabling or updating password).
     * Null/blank allowed only when disabling.
     */
    private String password;

    /**
     * true  = Enable password protection (password field required)
     * false = Disable password protection (password field ignored)
     */
    @JsonProperty("enabled")
    private boolean enabled;
}
