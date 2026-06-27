package com.gaur.friendstogether.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * CookieUtil — Centralized HttpOnly Cookie Management
 *
 * Creates and clears the secure refresh token cookie.
 * Configuration is profile-aware:
 *   - Dev:  Secure=false, Domain=localhost
 *   - Prod: Secure=true,  Domain=yourdomain.com
 */
@Component
public class CookieUtil {

    private static final Logger logger = LoggerFactory.getLogger(CookieUtil.class);

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final boolean secure;
    private final String sameSite;

    public CookieUtil(
            @Value("${app.cookie.secure}") boolean secure,
            @Value("${app.cookie.same-site:Lax}") String sameSite
    ) {
        this.secure = secure;
        this.sameSite = sameSite;

        logger.info("[COOKIE] CookieUtil initialized — Secure: {}, SameSite: {}, Path: {}",
                secure, sameSite, COOKIE_PATH);
    }

    /**
     * Create a refresh token HttpOnly cookie.
     *
     * @param refreshToken  the raw refresh token value
     * @param maxAgeSeconds the cookie lifetime in seconds (should match token expiry)
     * @return ResponseCookie ready to be added to the response
     */
    public ResponseCookie createRefreshTokenCookie(String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();

        logger.debug("[COOKIE] Refresh token cookie created — MaxAge: {}s, Secure: {}, SameSite: {}",
                maxAgeSeconds, secure, sameSite);

        return cookie;
    }

    /**
     * Create an expired cookie that clears the refresh token from the browser.
     * Used during logout.
     *
     * @return ResponseCookie with maxAge=0 that instructs the browser to delete the cookie
     */
    public ResponseCookie createClearRefreshTokenCookie() {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();

        logger.debug("[COOKIE] Clear refresh token cookie created (logout)");

        return cookie;
    }
}
