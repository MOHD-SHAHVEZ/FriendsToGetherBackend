package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.ChatMessage;
import com.gaur.friendstogether.entity.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/*
Yahan hum Pagination use karenge. Socho agar kisi ki chat mein 5000 messages hain,
toh hum saare ek saath load nahi karenge. Hum sirf last 20 messages uthayenge (Lazy Loading).
 */

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // --- [BUG FIX]: One-to-One Chat History (Bi-directional) ---
    // Hum m.sender.userId aur m.receiver.userId use kar rahe hain kyunki sender/receiver User Objects hain
    @Query("""
        SELECT m FROM ChatMessage m 
        WHERE (m.sender.userId = :user1 AND m.receiver.userId = :user2) 
           OR (m.sender.userId = :user2 AND m.receiver.userId = :user1) 
        ORDER BY m.timestamp ASC
    """)
    List<ChatMessage> findPersonalChatHistory(
            @Param("user1") String user1,
            @Param("user2") String user2
    );

    // --- [PERFORMANCE]: Get last X messages for a room sorted by time ---
    List<ChatMessage> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);

    // --- [BUSINESS LOGIC]: Count unread messages for a user ---
    long countByRoomIdAndReceiverUserIdAndStatus(String roomId, String receiverId, String status);

    // --- [OFFLINE MESSAGING]: Fetch all pending messages for online reconnections ---
    List<ChatMessage> findByReceiverEmailAndStatusOrderByTimestampAsc(String receiverEmail, MessageStatus status);

    // --- [READ RECEIPTS]: Batch update DELIVERED → READ via JPQL ---
    @Modifying
    @Query("UPDATE ChatMessage m SET m.status = :newStatus WHERE m.roomId = :roomId AND m.receiver.email = :receiverEmail AND m.status = :oldStatus")
    int updateStatusByRoomIdAndReceiverEmail(
        @Param("roomId") String roomId,
        @Param("receiverEmail") String receiverEmail,
        @Param("oldStatus") MessageStatus oldStatus,
        @Param("newStatus") MessageStatus newStatus
    );

    // --- [READ RECEIPTS]: Batch update SENT → READ (edge case: message arrives while chat is open) ---
    @Modifying
    @Query("UPDATE ChatMessage m SET m.status = :newStatus WHERE m.roomId = :roomId AND m.receiver.email = :receiverEmail AND m.status = :oldStatus")
    int updateSentToReadByRoomIdAndReceiverEmail(
        @Param("roomId") String roomId,
        @Param("receiverEmail") String receiverEmail,
        @Param("oldStatus") MessageStatus oldStatus,
        @Param("newStatus") MessageStatus newStatus
    );
}