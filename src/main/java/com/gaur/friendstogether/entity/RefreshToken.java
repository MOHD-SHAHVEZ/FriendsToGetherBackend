package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RefreshToken Entity — Tracks all issued refresh tokens.
 *
 * Each row represents one active (or revoked) refresh token.
 * The raw token is NEVER stored — only a SHA-256 hash.
 *
 * Token Rotation: When a refresh token is used, it is revoked and
 * replaced by a new one. The `replacedByToken` field links the chain
 * for detecting reuse attacks (if a revoked token is presented again,
 * the entire chain can be invalidated).
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_rt_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_rt_user_id", columnList = "user_id"),
        @Index(name = "idx_rt_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 hash of the raw refresh token.
     * Never store the raw token in the database.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * The user this refresh token belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * User-Agent or device description at token creation time.
     */
    @Column(name = "device_info", length = 512)
    private String deviceInfo;

    /**
     * IP address at the time the token was issued.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Absolute expiry timestamp — server-side authority on token validity.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * When this token was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Whether this token has been revoked (logout, rotation, or forced).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    /**
     * Hash of the replacement token (populated during rotation).
     * Used for reuse detection: if this revoked token is presented again,
     * we can trace the chain and revoke everything.
     */
    @Column(name = "replaced_by_token", length = 64)
    private String replacedByToken;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /**
     * Check if this token has expired based on server time.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /**
     * Check if this token is usable (not revoked AND not expired).
     */
    public boolean isUsable() {
        return !this.revoked && !isExpired();
    }
}
