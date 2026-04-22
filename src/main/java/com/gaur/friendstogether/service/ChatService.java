package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.ChatRequest;
import com.gaur.friendstogether.dto.response.ChatMessageResponse;
import com.gaur.friendstogether.dto.response.DeleteEvent;
import com.gaur.friendstogether.dto.response.ReadReceiptEvent;
import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.repository.*;
import com.gaur.friendstogether.util.ChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    /** Delete-for-everyone time window (10 minutes as per user requirement) */
    private static final Duration DELETE_FOR_ALL_WINDOW = Duration.ofMinutes(10);

    // ====================================================================
    // SAVE & DELIVER MESSAGE
    // ====================================================================
    @Transactional
    public ChatMessageResponse saveMessage(ChatRequest request, String senderEmail) {

        log.info("[CHAT-SVC] saveMessage() START — sender: {}, receiverId: {}", senderEmail, request.getReceiverId());

        // 1. Find Sender
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> {
                    log.error("[CHAT-SVC] Sender not found in DB! Email: {}", senderEmail);
                    return new RuntimeException("Sender not found with email: " + senderEmail);
                });
        log.info("[CHAT-SVC] Sender resolved: {} (userId: {})", sender.getEmail(), sender.getUserId());

        // Self-message prevention
        if (sender.getUserId().equals(request.getReceiverId())) {
            throw new RuntimeException("Cannot send message to yourself.");
        }

        // 2. Find Receiver
        User receiver = userRepository.findByUserId(request.getReceiverId())
                .orElseThrow(() -> {
                    log.error("[CHAT-SVC] Receiver not found in DB! UserId: {}", request.getReceiverId());
                    return new RuntimeException("Receiver not found with userId: " + request.getReceiverId());
                });
        log.info("[CHAT-SVC] Receiver resolved: {} (userId: {})", receiver.getEmail(), receiver.getUserId());

        // 3. Generate Room ID
        String roomId = ChatUtils.generateRoomId(sender.getUserId(), receiver.getUserId());
        log.info("[CHAT-SVC] Room ID: {}", roomId);

        // 4. ChatRoom upsert (create or update last message)
        chatRoomRepository.findByRoomId(roomId).ifPresentOrElse(
                room -> {
                    log.info("[CHAT-SVC] Room exists — updating last message");
                    room.setLastMessage(request.getContent());
                    room.setLastMessageTime(LocalDateTime.now());
                    chatRoomRepository.save(room);
                },
                () -> {
                    log.info("[CHAT-SVC] Room not found — creating new room");
                    ChatRoom newRoom = ChatRoom.builder()
                            .roomId(roomId)
                            .user1(sender)
                            .user2(receiver)
                            .lastMessage(request.getContent())
                            .lastMessageTime(LocalDateTime.now())
                            .build();
                    chatRoomRepository.save(newRoom);
                    log.info("[CHAT-SVC] New ChatRoom created: {}", roomId);
                });

        // 5. Build & Save ChatMessage
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
        log.info("[CHAT-SVC] Message saved to DB (id: {}, status: SENT)", savedMessage.getId());

        // 6. Presence-based delivery via PresenceService (not raw SimpUserRegistry)
        boolean isReceiverOnline = presenceService.isOnline(receiver.getEmail());

        if (isReceiverOnline) {
            log.info("[CHAT-SVC] Receiver ONLINE — upgrading to DELIVERED and pushing via WebSocket");
            savedMessage.setStatus(MessageStatus.DELIVERED);
            chatMessageRepository.save(savedMessage);

            // Build DTO and send to receiver
            ChatMessageResponse receiverDto = toResponseDto(savedMessage);
            messagingTemplate.convertAndSendToUser(receiver.getEmail(), "/queue/messages", receiverDto);
            log.info("[WS] Message dispatched to receiver: {}", receiver.getEmail());
        } else {
            log.info("[CHAT-SVC] Receiver OFFLINE — message stored for later delivery (status: SENT)");
        }

        // 7. Echo back to sender (confirms message hit the server)
        ChatMessageResponse senderDto = toResponseDto(savedMessage);
        messagingTemplate.convertAndSendToUser(sender.getEmail(), "/queue/messages", senderDto);
        log.info("[WS] Echo dispatched to sender: {}", sender.getEmail());

        log.info("[CHAT-SVC] saveMessage() COMPLETE — id: {}, status: {}", savedMessage.getId(), savedMessage.getStatus());
        return senderDto;
    }

    // ====================================================================
    // CHAT HISTORY (FIXED FOR PINK/GREY BUG)
    // ====================================================================
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(String userId1, String userId2, int limit) {
        log.info("[CHAT-SVC] Fetching personal chat history between {} and {} (limit: {})", userId1, userId2, limit);

        // 🔥 BI-DIRECTIONAL QUERY: Dono side ke messages uthayega
        List<ChatMessage> messages = chatMessageRepository.findPersonalChatHistory(userId1, userId2);

        return messages.stream()
                .filter(msg -> {
                    // Determine perspective dynamically
                    boolean isSender = msg.getSender().getUserId().equals(userId1);
                    boolean isReceiver = msg.getReceiver().getUserId().equals(userId1);

                    // Hide locally deleted messages
                    if (isSender && msg.isDeletedForSender()) return false;
                    if (isReceiver && msg.isDeletedForReceiver()) return false;

                    return true;
                })
                .map(msg -> {
                    // Global masking for delete-for-all
                    if (msg.isDeletedForAll()) {
                        return toMaskedResponseDto(msg);
                    }
                    return toResponseDto(msg);
                })
                .limit(limit) // Apply limit as per controller
                .collect(Collectors.toList());
    }

    // ====================================================================
    // OFFLINE MESSAGE DELIVERY (Called on Reconnect)
    // ====================================================================
    @Transactional
    public void deliverPendingMessages(String userEmail) {
        log.info("[CHAT-SVC] Checking pending offline messages for: {}", userEmail);

        List<ChatMessage> pendingMessages = chatMessageRepository
                .findByReceiverEmailAndStatusOrderByTimestampAsc(userEmail, MessageStatus.SENT);

        if (pendingMessages.isEmpty()) {
            log.info("[CHAT-SVC] No pending messages for: {}", userEmail);
            return;
        }

        log.info("[CHAT-SVC] Found {} undelivered messages — burst delivering now", pendingMessages.size());

        for (ChatMessage msg : pendingMessages) {
            msg.setStatus(MessageStatus.DELIVERED);

            ChatMessageResponse responseDto = toResponseDto(msg);
            messagingTemplate.convertAndSendToUser(userEmail, "/queue/messages", responseDto);
            log.info("[CHAT-SVC] Delivered pending message id: {} to {}", msg.getId(), userEmail);
        }

        chatMessageRepository.saveAll(pendingMessages);
        log.info("[CHAT-SVC] Bulk-updated {} offline messages to DELIVERED", pendingMessages.size());
    }

    // ====================================================================
    // READ RECEIPTS (Blue Tick Pipeline)
    // ====================================================================
    @Transactional
    public void markMessagesAsRead(String roomId, String receiverEmail) {
        log.info("[READ] Marking messages as READ — room: {}, receiver: {}", roomId, receiverEmail);

        // Pass 1: DELIVERED → READ (standard flow)
        int deliveredToRead = chatMessageRepository.updateStatusByRoomIdAndReceiverEmail(
                roomId, receiverEmail, MessageStatus.DELIVERED, MessageStatus.READ);
        log.info("[READ] DELIVERED → READ: {} messages updated", deliveredToRead);

        // Pass 2: SENT → READ (edge case: message arrived while chat is already open)
        int sentToRead = chatMessageRepository.updateSentToReadByRoomIdAndReceiverEmail(
                roomId, receiverEmail, MessageStatus.SENT, MessageStatus.READ);
        log.info("[READ] SENT → READ: {} messages updated (edge case)", sentToRead);

        int totalUpdated = deliveredToRead + sentToRead;

        if (totalUpdated == 0) {
            log.info("[READ] No unread messages found — idempotent, skipping notification");
            return;
        }

        log.info("[READ] Total updated: {} messages marked as READ", totalUpdated);

        // Notify the original sender via WebSocket
        chatRoomRepository.findByRoomId(roomId).ifPresent(room -> {
            String senderEmail = room.getUser1().getEmail().equals(receiverEmail)
                    ? room.getUser2().getEmail()
                    : room.getUser1().getEmail();

            ReadReceiptEvent event = ReadReceiptEvent.builder()
                    .roomId(roomId)
                    .status("READ")
                    .receiver(receiverEmail)
                    .updatedCount(totalUpdated)
                    .build();

            messagingTemplate.convertAndSendToUser(senderEmail, "/queue/read-receipts", event);
            log.info("[WS] Read receipt dispatched to sender: {} (count: {})", senderEmail, totalUpdated);
        });
    }

    // ====================================================================
    // MESSAGE DELETION (WhatsApp Style + 10-Minute Limit)
    // ====================================================================
    @Transactional
    public void deleteMessage(Long messageId, String type, String currentUserEmail) {
        log.info("[DELETE] Request received — user: {}, messageId: {}, type: {}", currentUserEmail, messageId, type);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> {
                    log.error("[DELETE] Message not found: {}", messageId);
                    return new RuntimeException("Message not found: " + messageId);
                });

        boolean isSender = message.getSender().getEmail().equals(currentUserEmail);
        boolean isReceiver = message.getReceiver().getEmail().equals(currentUserEmail);

        if (!isSender && !isReceiver) {
            log.warn("[DELETE] Authorization FAILED — user {} is neither sender nor receiver of message {}", currentUserEmail, messageId);
            throw new SecurityException("Unauthorized to modify this message.");
        }

        log.info("[DELETE] Authorization SUCCESS — user: {}, role: {}", currentUserEmail, isSender ? "SENDER" : "RECEIVER");

        // --- DELETE FOR EVERYONE ---
        if ("ALL".equalsIgnoreCase(type)) {
            if (!isSender) {
                log.warn("[DELETE] Denied — only sender can delete for everyone. User: {}", currentUserEmail);
                throw new SecurityException("Only the sender can delete for everyone.");
            }

            // Idempotency: skip if already deleted
            if (message.isDeletedForAll()) {
                log.info("[DELETE] Idempotent — message {} already globally deleted", messageId);
                return;
            }

            // 10-minute time limit enforcement
            LocalDateTime messageTime = message.getTimestamp();
            Duration elapsed = Duration.between(messageTime, LocalDateTime.now());
            if (elapsed.compareTo(DELETE_FOR_ALL_WINDOW) > 0) {
                log.warn("[DELETE] Time limit exceeded — message {} was sent {} minutes ago (limit: {} minutes)",
                        messageId, elapsed.toMinutes(), DELETE_FOR_ALL_WINDOW.toMinutes());
                throw new RuntimeException("Cannot delete for everyone — time limit of "
                        + DELETE_FOR_ALL_WINDOW.toMinutes() + " minutes exceeded.");
            }

            message.setDeletedForAll(true);
            message.setDeletedAt(LocalDateTime.now());
            log.info("[DELETE] Flags updated — isDeletedForAll=true, deletedAt={}", message.getDeletedAt());
        }
        // --- DELETE FOR ME ---
        else if ("ME".equalsIgnoreCase(type)) {
            if (isSender) message.setDeletedForSender(true);
            if (isReceiver) message.setDeletedForReceiver(true);
            log.info("[DELETE] Local flags updated — deletedForSender: {}, deletedForReceiver: {}",
                    message.isDeletedForSender(), message.isDeletedForReceiver());
        } else {
            throw new IllegalArgumentException("Invalid delete type: '" + type + "'. Expected 'ALL' or 'ME'.");
        }

        chatMessageRepository.save(message);
        log.info("[DELETE] Database updated successfully for message {}", messageId);

        // --- WEBSOCKET EVENT DISPATCH ---
        DeleteEvent event = DeleteEvent.builder()
                .messageId(messageId)
                .type(message.isDeletedForAll() ? MessageType.SYSTEM.name() : message.getType().name())
                .deleteType(type.toUpperCase())
                .content(message.isDeletedForAll() ? "\uD83D\uDEAB This message was deleted" : message.getContent())
                .build();

        if ("ALL".equalsIgnoreCase(type)) {
            messagingTemplate.convertAndSendToUser(message.getSender().getEmail(), "/queue/delete-events", event);
            messagingTemplate.convertAndSendToUser(message.getReceiver().getEmail(), "/queue/delete-events", event);
            log.info("[WS] Delete event dispatched to BOTH participants");
        } else {
            messagingTemplate.convertAndSendToUser(currentUserEmail, "/queue/delete-events", event);
            log.info("[WS] Delete event dispatched to requester only: {}", currentUserEmail);
        }
    }

    // ====================================================================
    // DTO MAPPING HELPERS
    // ====================================================================

    private ChatMessageResponse toResponseDto(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .roomId(msg.getRoomId())
                .content(msg.getContent())
                .type(msg.getType() != null ? msg.getType().name() : "TEXT")
                .status(msg.getStatus() != null ? msg.getStatus().name() : "SENT")
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
                .build();
    }

    private ChatMessageResponse toMaskedResponseDto(ChatMessage msg) {
        ChatMessageResponse dto = toResponseDto(msg);
        dto.setContent("\uD83D\uDEAB This message was deleted");
        dto.setType("SYSTEM");
        return dto;
    }
}