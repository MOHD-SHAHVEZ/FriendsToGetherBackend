package com.gaur.friendstogether.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isLocked")   // Ab Jackson 'isLocked' ko sahi se padh payega ->  true agar password chahiye
    private boolean isLocked;

    @JsonProperty("isPersonal") // Same yahan bhi -> true agar private room ho
    private boolean isPersonal;

    @Max(value = 10, message = "Maximum 10 users allowed in MVP")
//    private int maxUsers;       // Default limits ke liye
    private int maxSeat;   //  max limit of room seats jispe baita kar baat karega users
    // jinka pass seat nahi wo baat nahi karega.
}