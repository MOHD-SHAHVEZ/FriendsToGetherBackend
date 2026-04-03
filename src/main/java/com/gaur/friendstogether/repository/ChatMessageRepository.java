package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/*
Yahan hum Pagination use karenge. Socho agar kisi ki chat mein 5000 messages hain,
toh hum saare ek saath load nahi karenge. Hum sirf last 20 messages uthayenge (Lazy Loading).
 */

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // --- [PERFORMANCE]: Get last X messages for a room sorted by time ---
    List<ChatMessage> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);

    // --- [BUSINESS LOGIC]: Count unread messages for a user ---
    long countByRoomIdAndReceiverUserIdAndStatus(String roomId, String receiverId, String status);
}