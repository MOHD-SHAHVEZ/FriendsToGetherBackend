package com.gaur.friendstogether.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Utility — Enterprise-Grade Token Management
 *
 * Generates and validates two distinct token types:
 *   - ACCESS  (short-lived, 15 min default) — sent via Authorization header
 *   - REFRESH (long-lived, 7 days default) — stored as HttpOnly cookie
 *
 * Each token carries a "type" claim so the server can reject misuse
 * (e.g., a refresh token used as an access token).
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    // ==========================================
    // Token Type Constants
    // ==========================================
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";
    private static final String CLAIM_TYPE = "type";

    // ==========================================
    // Configuration — injected from application.properties
    // ==========================================
    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;

        logger.info("[JWT] JwtUtil initialized — Access TTL: {}ms ({}min), Refresh TTL: {}ms ({}days)",
                accessTokenExpirationMs, accessTokenExpirationMs / 60000,
                refreshTokenExpirationMs, refreshTokenExpirationMs / 86400000);
    }

    // ==========================================
    // Token Generation
    // ==========================================

    /**
     * Generate a short-lived ACCESS token (default 15 minutes).
     * Used by the frontend in the Authorization header.
     */
    public String generateAccessToken(String email) {
        return buildToken(email, TOKEN_TYPE_ACCESS, accessTokenExpirationMs);
    }

    /**
     * Generate a long-lived REFRESH token (default 7 days).
     * Stored as an HttpOnly cookie — never sent in response body.
     */
    public String generateRefreshToken(String email) {
        return buildToken(email, TOKEN_TYPE_REFRESH, refreshTokenExpirationMs);
    }

    /**
     * @deprecated Use {@link #generateAccessToken(String)} instead.
     * Kept temporarily for backward compatibility during migration.
     */
    @Deprecated(forRemoval = true)
    public String generateToken(String email) {
        return generateAccessToken(email);
    }

    // ==========================================
    // Token Parsing & Claims
    // ==========================================

    /**
     * Extract the email (subject) from any valid token.
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extract the token type claim ("ACCESS" or "REFRESH").
     */
    public String getTokenType(String token) {
        return parseClaims(token).get(CLAIM_TYPE, String.class);
    }

    /**
     * Get the access token expiration duration in seconds.
     * Used by the frontend to schedule silent refresh.
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    /**
     * Get the refresh token expiration duration in seconds.
     * Used for cookie Max-Age.
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationMs / 1000;
    }

    // ==========================================
    // Token Validation
    // ==========================================

    /**
     * Validate that the token is well-formed, not expired, and is an ACCESS token.
     */
    public boolean validateAccessToken(String token) {
        return validateTokenWithType(token, TOKEN_TYPE_ACCESS);
    }

    /**
     * Validate that the token is well-formed, not expired, and is a REFRESH token.
     */
    public boolean validateRefreshToken(String token) {
        return validateTokenWithType(token, TOKEN_TYPE_REFRESH);
    }

    /**
     * Generic validation — checks signature and expiry only (no type check).
     * Used by WebSocket auth where we only care about validity.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("[JWT] Token expired for subject: {}", e.getClaims().getSubject());
            return false;
        } catch (SecurityException | MalformedJwtException e) {
            logger.warn("[JWT] Invalid token signature or format: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("[JWT] Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ==========================================
    // Private Helpers
    // ==========================================

    private String buildToken(String email, String type, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    private boolean validateTokenWithType(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get(CLAIM_TYPE, String.class);

            if (!expectedType.equals(tokenType)) {
                logger.warn("[JWT] Token type mismatch — expected: {}, got: {}", expectedType, tokenType);
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("[JWT] {} token expired for subject: {}", expectedType, e.getClaims().getSubject());
            return false;
        } catch (SecurityException | MalformedJwtException e) {
            logger.warn("[JWT] Invalid token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("[JWT] Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}