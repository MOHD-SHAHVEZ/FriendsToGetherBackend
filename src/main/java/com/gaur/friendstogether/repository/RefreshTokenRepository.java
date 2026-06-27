package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.RefreshToken;
import com.gaur.friendstogether.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find a refresh token by its SHA-256 hash.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Find all active (non-revoked) refresh tokens for a user.
     * Useful for listing active sessions.
     */
    List<RefreshToken> findAllByUserAndRevokedFalse(User user);

    /**
     * Count active refresh tokens for a user (session limit enforcement).
     */
    long countByUserAndRevokedFalse(User user);

    /**
     * Revoke all active refresh tokens for a user (forced logout from all devices).
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
    int revokeAllByUser(@Param("user") User user);

    /**
     * Delete all expired tokens (scheduled cleanup).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") LocalDateTime now);

    /**
     * Delete all tokens for a user (account deletion scenario).
     */
    void deleteAllByUser(User user);
}
