package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.ChatRequest;
import com.gaur.friendstogether.dto.response.ChatMessageResponse;
import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.repository.*;
import com.gaur.friendstogether.util.ChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatMessageResponse saveMessage(ChatRequest request, String senderEmail) {

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("💾 [CHAT-SVC] saveMessage() START — senderEmail: {}, receiverId: {}",
                senderEmail, request.getReceiverId());

        // 1. Sender Find Karo
        log.info("🔍 [CHAT-SVC] Database mein Sender dhoondh rahe hain: {}", senderEmail);
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> {
                    log.error("❌ [CHAT-SVC] Sender NAHI MILA database mein! Email: {}", senderEmail);
                    return new RuntimeException("Sender not found with email: " + senderEmail);
                });
        log.info("✅ [CHAT-SVC] Sender mila: {} (userId: {})", sender.getEmail(), sender.getUserId());

        //=======================================================================
        // Sender khud ko message nahi bhaj sakta receiver Id sender id se aalag honi chiaya tab kaam karo.
        if (sender.getUserId().equals(request.getReceiverId())) {
            throw new RuntimeException("Bhai, khud ko message nahi bhej sakte!");
        }
        //=======================================================================

        // 2. Receiver Find Karo
        log.info("🔍 [CHAT-SVC] Database mein Receiver dhoondh rahe hain: {}", request.getReceiverId());
        User receiver = userRepository.findByUserId(request.getReceiverId())
                .orElseThrow(() -> {
                    log.error("❌ [CHAT-SVC] Receiver NAHI MILA database mein! UserId: {}", request.getReceiverId());
                    return new RuntimeException("Receiver not found with userId: " + request.getReceiverId());
                });
        log.info("Receiver Email (DB): {}", receiver.getEmail());
        log.info("Active Principal (target): {}", receiver.getEmail());
        log.info("✅ [CHAT-SVC] Receiver mila: {} (userId: {})", receiver.getEmail(), receiver.getUserId());

        // 3. Room ID Generate Karo
        String roomId = ChatUtils.generateRoomId(sender.getUserId(), receiver.getUserId());
        log.info("🏠 [CHAT-SVC] Room ID generate hua: {}", roomId);

        // 4. ChatRoom Check & Update/Create Logic
        log.info("🔍 [CHAT-SVC] ChatRoom check ho raha hai — roomId: {}", roomId);
        chatRoomRepository.findByRoomId(roomId).ifPresentOrElse(
                room -> {
                    // Room pehle se hai, sirf 'Last Message' update karo
                    log.info("🏠 [CHAT-SVC] Room PEHLE SE HAI — last message update ho raha hai");
                    room.setLastMessage(request.getContent());
                    room.setLastMessageTime(LocalDateTime.now());
                    chatRoomRepository.save(room);
                    log.info("✅ [CHAT-SVC] Room updated successfully — roomId: {}", roomId);
                },
                () -> {
                    // Naya room bana rahe hain
                    log.info("🆕 [CHAT-SVC] Room NAHI MILA — Naya room create ho raha hai...");
                    ChatRoom newRoom = ChatRoom.builder()
                            .roomId(roomId)
                            .user1(sender)
                            .user2(receiver)
                            .lastMessage(request.getContent())
                            .lastMessageTime(LocalDateTime.now())
                            .build();
                    chatRoomRepository.save(newRoom);
                    log.info("✅ [CHAT-SVC] Naya ChatRoom CREATED — roomId: {}", roomId);
                }
        );

        // 5. ChatMessage Build & Save
        log.info("📝 [CHAT-SVC] ChatMessage build ho raha hai...");
        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .sender(sender)
                .receiver(receiver)
                .content(request.getContent())
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .type(MessageType.valueOf(request.getType().toUpperCase()))
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        log.info("✅ [CHAT-SVC] Message DATABASE MEIN SAVE HO GAYA!");
        
        // 6. MAP DTO INSIDE TRANSACTION TO PREVENT LAZY INITIALIZATION EXCEPTION
        ChatMessageResponse responseDto = ChatMessageResponse.builder()
                .id(savedMessage.getId())
                .roomId(savedMessage.getRoomId())
                .content(savedMessage.getContent())
                .type(savedMessage.getType() != null ? savedMessage.getType().name() : "")
                .status(savedMessage.getStatus() != null ? savedMessage.getStatus().name() : "")
                .timestamp(savedMessage.getTimestamp() != null ? savedMessage.getTimestamp().toString() : "")
                .sender(ChatMessageResponse.UserData.builder()
                        .userId(sender.getUserId())
                        .username(sender.getUsername())
                        .email(sender.getEmail())
                        .build())
                .receiver(ChatMessageResponse.UserData.builder()
                        .userId(receiver.getUserId())
                        .username(receiver.getUsername())
                        .email(receiver.getEmail())
                        .build())
                .build();

        // 7. BROADCAST via User Destination (1-to-1 WhatsApp style)
        // Spring internally translates this to /user/{receiverEmail}/queue/messages
        log.info("📡 [CHAT-SVC] Private message routing to Receiver: {} on /queue/messages", receiver.getEmail());
        messagingTemplate.convertAndSendToUser(
                receiver.getEmail(), 
                "/queue/messages", 
                responseDto
        );

        // 2. Sender ko bhi bhejo (VERY IMPORTANT)
        messagingTemplate.convertAndSendToUser(
                sender.getEmail(),
                "/queue/messages",
                responseDto
        );

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return responseDto;
    }

    @Transactional(readOnly = true)
    public java.util.List<ChatMessageResponse> getChatHistory(String userId1, String userId2, int limit) {
        String roomId = ChatUtils.generateRoomId(userId1, userId2);
        log.info("📜 [CHAT-SVC] Fetching Chat History for Room: {}", roomId);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);
        java.util.List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);

        // Sort chronologically (oldest first for frontend display)
        java.util.Collections.reverse(messages);

        return messages.stream().map(msg -> ChatMessageResponse.builder()
                .id(msg.getId())
                .roomId(msg.getRoomId())
                .content(msg.getContent())
                .type(msg.getType() != null ? msg.getType().name() : "")
                .status(msg.getStatus() != null ? msg.getStatus().name() : "")
                .timestamp(msg.getTimestamp() != null ? msg.getTimestamp().toString() : "")
                .sender(ChatMessageResponse.UserData.builder()
                        .userId(msg.getSender().getUserId())
                        .username(msg.getSender().getUsername())
                        .email(msg.getSender().getEmail())
                        .build())
                .receiver(ChatMessageResponse.UserData.builder()
                        .userId(msg.getReceiver().getUserId())
                        .username(msg.getReceiver().getUsername())
                        .email(msg.getReceiver().getEmail())
                        .build())
                .build()
        ).collect(java.util.stream.Collectors.toList());
    }
}