package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.RoomBlacklist;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.VoiceRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RoomBlacklistRepository extends JpaRepository<RoomBlacklist, Long> {

    // 1. Join logic mein use hoga: "Kya ye user block hai?"
    boolean existsByRoomAndBlockedUser(VoiceRoom room, User blockedUser);

    // 2. Idempotency aur safe fetch ke liye
    Optional<RoomBlacklist> findByRoomAndBlockedUser(VoiceRoom room, User blockedUser);

    /**
     * 🔥 PRO FEATURE: Atomic Delete
     * Ye database level par direct delete maarta hai aur batata hai kitne rows delete huye.
     * Race condition se bachne ka yahi sahi tarika hai.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RoomBlacklist rb WHERE rb.room = :room AND rb.blockedUser = :user")
    int deleteIfExists(@Param("room") VoiceRoom room, @Param("user") User user);
}