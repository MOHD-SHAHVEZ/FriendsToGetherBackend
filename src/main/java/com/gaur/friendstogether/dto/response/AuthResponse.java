package com.gaur.friendstogether.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * AuthResponse — Returned in the JSON response body after successful authentication.
 *
 * IMPORTANT: The refreshToken is NEVER included in the response body.
 * It is set as an HttpOnly cookie by the controller.
 * This DTO only carries the access token (stored in-memory by the frontend).
 */
@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String tokenType; // "Bearer"
    private String username;
    private long expiresIn;   // Access token TTL in seconds (for frontend refresh scheduling)
}