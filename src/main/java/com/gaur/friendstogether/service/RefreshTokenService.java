package com.gaur.friendstogether.service;

import com.gaur.friendstogether.entity.RefreshToken;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.RefreshTokenRepository;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * RefreshTokenService — Manages the full lifecycle of refresh tokens.
 *
 * Operations:
 *   - Create: Issue a new refresh token, hash it, persist to DB
 *   - Refresh (Rotate): Validate old token → revoke it → issue new pair
 *   - Revoke: Mark a single token as revoked (logout)
 *   - Revoke All: Invalidate all tokens for a user (forced logout)
 *   - Cleanup: Scheduled deletion of expired tokens from DB
 *
 * Security features:
 *   - Token stored as SHA-256 hash (raw token never in DB)
 *   - Token rotation on every refresh (old token invalidated)
 *   - Reuse detection: if a revoked token is presented, revoke ALL user tokens
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    // ==========================================
    // Create
    // ==========================================

    /**
     * Generate a new refresh token, hash it, and persist to DB.
     *
     * @param user       the authenticated user
     * @param deviceInfo User-Agent or device description
     * @param ipAddress  client IP address
     * @return the RAW refresh token (to be set as HttpOnly cookie)
     */
    @Transactional
    public String createRefreshToken(User user, String deviceInfo, String ipAddress) {
        String rawToken = jwtUtil.generateRefreshToken(user.getEmail());
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .deviceInfo(truncate(deviceInfo, 512))
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpirationSeconds()))
                .build();

        refreshTokenRepository.save(refreshToken);
        logger.info("[REFRESH-TOKEN] Created for user: {} from IP: {}", user.getEmail(), ipAddress);

        return rawToken;
    }

    // ==========================================
    // Refresh (Rotate)
    // ==========================================

    /**
     * Validate the raw refresh token, rotate it, and return a new access token.
     *
     * @param rawRefreshToken the raw token from the HttpOnly cookie
     * @param deviceInfo      current device info
     * @param ipAddress       current IP
     * @return RefreshResult containing the new access token and new raw refresh token
     * @throws RuntimeException if token is invalid, expired, or revoked
     */
    @Transactional
    public RefreshResult refreshAccessToken(String rawRefreshToken, String deviceInfo, String ipAddress) {
        // 1. Validate JWT signature and type
        if (!jwtUtil.validateRefreshToken(rawRefreshToken)) {
            logger.warn("[REFRESH-TOKEN] JWT validation failed");
            throw new RuntimeException("Invalid or expired refresh token.");
        }

        // 2. Look up in DB by hash
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    logger.warn("[REFRESH-TOKEN] Token hash not found in DB — possible forgery");
                    return new RuntimeException("Refresh token not recognized.");
                });

        // 3. Check if token was already revoked (REUSE DETECTION)
        if (storedToken.isRevoked()) {
            logger.error("[REFRESH-TOKEN] ⚠️ REUSE DETECTED for user: {} — revoking ALL tokens!",
                    storedToken.getUser().getEmail());
            // Someone is using a token that was already rotated — compromise detected
            refreshTokenRepository.revokeAllByUser(storedToken.getUser());
            throw new RuntimeException("Refresh token reuse detected. All sessions have been terminated.");
        }

        // 4. Check server-side expiry
        if (storedToken.isExpired()) {
            logger.warn("[REFRESH-TOKEN] Token expired server-side for user: {}", storedToken.getUser().getEmail());
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new RuntimeException("Refresh token has expired. Please log in again.");
        }

        // 5. ROTATE: Revoke old token
        User user = storedToken.getUser();
        String newRawRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        String newTokenHash = hashToken(newRawRefreshToken);

        storedToken.setRevoked(true);
        storedToken.setReplacedByToken(newTokenHash);
        refreshTokenRepository.save(storedToken);

        // 6. Persist new refresh token
        RefreshToken newRefreshToken = RefreshToken.builder()
                .tokenHash(newTokenHash)
                .user(user)
                .deviceInfo(truncate(deviceInfo, 512))
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpirationSeconds()))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        // 7. Generate new access token
        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail());

        logger.info("[REFRESH-TOKEN] Rotated successfully for user: {}", user.getEmail());

        return new RefreshResult(newAccessToken, newRawRefreshToken, user.getUsername());
    }

    // ==========================================
    // Revoke (Logout)
    // ==========================================

    /**
     * Revoke a single refresh token (single-device logout).
     */
    @Transactional
    public void revokeToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            logger.info("[REFRESH-TOKEN] Revoked for user: {}", token.getUser().getEmail());
        });
    }

    /**
     * Revoke all refresh tokens for a user (forced logout from all devices).
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        int count = refreshTokenRepository.revokeAllByUser(user);
        logger.info("[REFRESH-TOKEN] Revoked {} tokens for user: {}", count, user.getEmail());
    }

    /**
     * Revoke all refresh tokens for a user identified by email.
     */
    @Transactional
    public void revokeAllUserTokens(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        revokeAllUserTokens(user);
    }

    // ==========================================
    // Cleanup (Scheduled)
    // ==========================================

    /**
     * Delete expired tokens from the database.
     * Runs every 6 hours to keep the table clean.
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // Every 6 hours
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            logger.info("[REFRESH-TOKEN] Cleanup: deleted {} expired tokens", deleted);
        }
    }

    // ==========================================
    // Utility
    // ==========================================

    /**
     * SHA-256 hash of the raw token for secure storage.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Safely truncate a string to max length (null-safe).
     */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    // ==========================================
    // Result DTO (inner class)
    // ==========================================

    /**
     * Holds the result of a successful token refresh.
     */
    public record RefreshResult(
            String accessToken,
            String rawRefreshToken,
            String username
    ) {}
}
