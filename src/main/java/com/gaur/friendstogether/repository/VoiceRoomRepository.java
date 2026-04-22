package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.RoomStatus;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.VoiceRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VoiceRoomRepository extends JpaRepository<VoiceRoom, Long> {

    // 1. EXACT SEARCH: Sirf Active Rooms dikhana
    @Query("SELECT vr FROM VoiceRoom vr WHERE vr.roomId = :roomId AND vr.status = :status")
    Optional<VoiceRoom> findByRoomIdAndStatus(@Param("roomId") String roomId, @Param("status") RoomStatus status);

    // 2. DISCOVERY: Fast sorting using DB Column (Member Count + Recency)
    @Query("""
        SELECT vr FROM VoiceRoom vr 
        WHERE vr.status = :status 
        ORDER BY vr.currentMemberCount DESC, vr.createdAt DESC
    """)
    Page<VoiceRoom> findDiscoveryRooms(@Param("status") RoomStatus status, Pageable pageable);

    // 3. ATOMIC COUNT UPDATE: Database level par count change karna (Race condition safe)
    @Modifying
    @Query("UPDATE VoiceRoom vr SET vr.currentMemberCount = vr.currentMemberCount + :delta WHERE vr.id = :id")
    void updateMemberCount(@Param("id") Long id, @Param("delta") int delta);

    // 4. CATEGORY DISCOVERY: Category filtered discovery (Atomic Sync safe)
    @Query("""
        SELECT vr FROM VoiceRoom vr 
        WHERE vr.status = :status 
        AND vr.category = :category 
        ORDER BY vr.currentMemberCount DESC, vr.createdAt DESC
    """)
    Page<VoiceRoom> findDiscoveryRoomsByCategory(
            @Param("status") RoomStatus status,
            @Param("category") com.gaur.friendstogether.entity.RoomCategory category,
            Pageable pageable);


    // Room dhoondne ke liye (8-digit Numeric ID se)
    Optional<VoiceRoom> findByRoomId(String roomId);

    // Check karne ke liye ki ID pehle se hai ya nahi
    boolean existsByRoomId(String roomId);

    // Check User ke pass already se personal room hai ya nahi.
    boolean existsByOwnerAndIsPersonalTrue(User owner);

    // VoiceRoomRepository.java mein add karo
    Optional<VoiceRoom> findByOwnerAndIsPersonalTrue(User owner);
}