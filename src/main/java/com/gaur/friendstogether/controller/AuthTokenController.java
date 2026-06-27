package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.AuthResponse;
import com.gaur.friendstogether.service.RefreshTokenService;
import com.gaur.friendstogether.util.CookieUtil;
import com.gaur.friendstogether.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AuthTokenController — Handles refresh and logout flows.
 *
 * POST /api/auth/refresh  — Reads refresh token from HttpOnly cookie, rotates, returns new access token
 * POST /api/auth/logout   — Revokes refresh token, clears cookie
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthTokenController {

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenController.class);

    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;
    private final JwtUtil jwtUtil;

    /**
     * POST /api/auth/refresh
     *
     * Reads the refresh token from the HttpOnly cookie.
     * Validates it, rotates (revokes old, issues new), and returns
     * a new access token in the body + new refresh cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        try {
            // 1. Extract refresh token from cookie
            String rawRefreshToken = extractRefreshTokenFromCookie(request);

            if (rawRefreshToken == null) {
                logger.warn("[REFRESH] No refresh_token cookie found in request");
                return ResponseEntity.status(401)
                        .body(Map.of("error", "No refresh token provided."));
            }

            // 2. Rotate: validate → revoke old → create new pair
            String deviceInfo = request.getHeader("User-Agent");
            String ipAddress = request.getRemoteAddr();

            RefreshTokenService.RefreshResult result =
                    refreshTokenService.refreshAccessToken(rawRefreshToken, deviceInfo, ipAddress);

            // 3. Set new refresh token cookie
            ResponseCookie newCookie = cookieUtil.createRefreshTokenCookie(
                    result.rawRefreshToken(),
                    jwtUtil.getRefreshTokenExpirationSeconds()
            );

            // 4. Return new access token in body
            AuthResponse response = AuthResponse.builder()
                    .accessToken(result.accessToken())
                    .tokenType("Bearer")
                    .username(result.username())
                    .expiresIn(jwtUtil.getAccessTokenExpirationSeconds())
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                    .body(response);

        } catch (RuntimeException e) {
            logger.warn("[REFRESH] Refresh failed: {}", e.getMessage());

            // Clear the invalid cookie
            ResponseCookie clearCookie = cookieUtil.createClearRefreshTokenCookie();

            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/auth/logout
     *
     * Revokes the refresh token and clears the cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            // 1. Extract refresh token from cookie
            String rawRefreshToken = extractRefreshTokenFromCookie(request);

            if (rawRefreshToken != null) {
                // 2. Revoke token in DB
                refreshTokenService.revokeToken(rawRefreshToken);
            }

            // 3. Clear cookie (even if no token was found — defensive)
            ResponseCookie clearCookie = cookieUtil.createClearRefreshTokenCookie();

            logger.info("[LOGOUT] Logout completed");

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .body(Map.of("message", "Logged out successfully."));

        } catch (Exception e) {
            logger.error("[LOGOUT] Error during logout: {}", e.getMessage());

            // Still clear the cookie even on error
            ResponseCookie clearCookie = cookieUtil.createClearRefreshTokenCookie();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .body(Map.of("message", "Logged out."));
        }
    }

    // ==========================================
    // Helper
    // ==========================================

    /**
     * Extract the refresh_token value from the request cookies.
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (CookieUtil.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
