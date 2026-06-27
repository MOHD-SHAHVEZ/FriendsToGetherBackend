package com.gaur.friendstogether.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Internal DTO used between AuthService/OAuthAuthenticationService and controllers.
 * Carries both the access token (for response body) and the raw refresh token
 * (for the controller to set as HttpOnly cookie).
 *
 * This DTO is NEVER serialized to JSON or returned to the client.
 */
@Data
@Builder
public class AuthTokenPair {
    private String accessToken;
    private String rawRefreshToken; // For controller to set as cookie — NEVER in JSON response
    private String username;
    private long accessTokenExpiresIn; // seconds
}
