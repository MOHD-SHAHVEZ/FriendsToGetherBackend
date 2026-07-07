package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.RoomMember;
import com.gaur.friendstogether.entity.RoomRole;
import com.gaur.friendstogether.entity.VoiceRoom;
import com.gaur.friendstogether.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    // 1. Validation: Kya user is room mein pehle se active hai?
    boolean existsByRoomAndUserAndIsActiveTrue(VoiceRoom room, User user);

    // 2. Count present Member in VoiceRoom Total:
    long countByRoomAndIsActiveTrue(VoiceRoom room);

    // 3. REJOIN LOGIC: Lock-free read for validation checks (kick/ban permission checks)
    // For write operations (join/leave state changes), use findByRoomAndUserWithLock instead.
    Optional<RoomMember> findByRoomAndUser(VoiceRoom room, User user);

    // 4. Bulk Update: Performance improve karne ke liye single query
    @Modifying
    @Transactional
    @Query("""
        UPDATE RoomMember rm 
        SET rm.isActive = false, rm.leftAt = :time 
        WHERE rm.room = :room AND rm.isActive = true
    """)
    void deactivateAllByRoom(@Param("room") VoiceRoom room, @Param("time") LocalDateTime time);

    /**
     * NEW: MEMBER LIST API (Team Lead Approved)
     * Performance: JOIN FETCH se N+1 query problem khatam (User + Profile ek baar mein load)
     * Ordering: OWNER (1) -> ADMIN (2) -> MEMBER (3)
     */
    @Query("""
        SELECT rm FROM RoomMember rm 
        JOIN FETCH rm.user u 
        LEFT JOIN FETCH u.profile 
        WHERE rm.room = :room AND rm.isActive = true 
        ORDER BY 
            CASE rm.role 
                WHEN com.gaur.friendstogether.entity.RoomRole.OWNER THEN 1 
                WHEN com.gaur.friendstogether.entity.RoomRole.ADMIN THEN 2 
                ELSE 3 
            END ASC, 
            u.username ASC
    """)
    Page<RoomMember> findActiveMembersByRoom(@Param("room") VoiceRoom room, Pageable pageable);

    // 5. Normal count (Sirf display ya info ke liye)
    long countByRoomAndRoleAndIsActiveTrue(VoiceRoom room, RoomRole role);

    /**
     * 6. PRODUCTION SAFE ADMIN FETCH (Atomic Safety)
     * Lead Recommendation: COUNT with lock safe nahi hota, rows fetch with lock safe hota hai.
     * Ye method active admins ki rows ko lock kar dega taaki race condition mein limit breach na ho.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rm FROM RoomMember rm WHERE rm.room = :room AND rm.role = :role AND rm.isActive = true")
    List<RoomMember> findActiveAdminsWithLock(@Param("room") VoiceRoom room, @Param("role") RoomRole role);

    /**
     * 7. Locked Member Fetch (User ka role change karne ke liye safe lock)
     * Promotion/Demotion ke waqt target user ki row ko lock karne ke liye.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rm FROM RoomMember rm WHERE rm.room = :room AND rm.user = :user AND rm.isActive = true")
    Optional<RoomMember> findActiveMemberWithLock(@Param("room") VoiceRoom room, @Param("user") User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rm FROM RoomMember rm WHERE rm.room = :room AND rm.user = :user")
    Optional<RoomMember> findByRoomAndUserWithLock(@Param("room") VoiceRoom room, @Param("user") User user);

    /**
     * 8. HOST TRANSFER: Find the next best candidate for room ownership.
     * Priority: ADMIN first, then MEMBER, ordered by join time (seniority).
     * Excludes the leaving user from candidates.
     */
    @Query("""
        SELECT rm FROM RoomMember rm 
        WHERE rm.room = :room AND rm.isActive = true AND rm.user != :excludeUser 
        ORDER BY 
            CASE rm.role 
                WHEN com.gaur.friendstogether.entity.RoomRole.ADMIN THEN 1 
                ELSE 2 
            END ASC, 
            rm.joinedAt ASC
    """)
    List<RoomMember> findHostCandidates(@Param("room") VoiceRoom room, @Param("excludeUser") User excludeUser);

    /**
     * 9. PERSONAL ROOM EXIT CHECK: Kya room mein koi OWNER ya ADMIN baaqi hai?
     * Owner leaving ke waqt personal room ko band karna hai ya nahi — yahi decide karega.
     */
    @Query("""
        SELECT CASE WHEN COUNT(rm) > 0 THEN true ELSE false END 
        FROM RoomMember rm 
        WHERE rm.room = :room AND rm.isActive = true AND rm.user != :excludeUser 
        AND (rm.role = com.gaur.friendstogether.entity.RoomRole.OWNER OR rm.role = com.gaur.friendstogether.entity.RoomRole.ADMIN)
    """)
    boolean existsActiveOwnerOrAdmin(@Param("room") VoiceRoom room, @Param("excludeUser") User excludeUser);

    /**
     * Finds all active rooms for a user.
     * Useful for disconnecting users from rooms on WebSocket disconnect.
     */
    @Query("SELECT rm.room FROM RoomMember rm WHERE rm.user = :user AND rm.isActive = true")
    List<VoiceRoom> findActiveRoomsByUser(@Param("user") User user);
}