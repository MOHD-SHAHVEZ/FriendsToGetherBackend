package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.dto.response.ModerationLogResponse;
import com.gaur.friendstogether.entity.ModerationAction;
import com.gaur.friendstogether.entity.RoomModerationLog;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.VoiceRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomModerationLogRepository extends JpaRepository<RoomModerationLog, Long> {

    // 1. Pagination ke sath (History dikhane ke liye)
//    Page<RoomModerationLog> findByRoom(VoiceRoom room, Pageable pageable);

    @Query("""
    SELECT new com.gaur.friendstogether.dto.response.ModerationLogResponse(
        l.id,
        CAST(l.room.id as string),
        CAST(a.id as string),
        a.username,
        CAST(t.id as string),
        t.username,
        l.action,
        l.reason,
        l.metadata,
        l.createdAt
    )
    FROM RoomModerationLog l
    JOIN l.actor a
    JOIN l.target t
    WHERE l.room = :room
""")
    Page<ModerationLogResponse> findLogsByRoom(VoiceRoom room, Pageable pageable);
    // 2. Specific user ki history dhoondne ke liye
    List<RoomModerationLog> findByRoomAndTargetOrderByCreatedAtDesc(VoiceRoom room, User target);

    // 3. Count nikalne ke liye (Ki is user ko kitni baar kick kiya gaya)
    long countByRoomAndTargetAndAction(VoiceRoom room, User target, ModerationAction action);
}