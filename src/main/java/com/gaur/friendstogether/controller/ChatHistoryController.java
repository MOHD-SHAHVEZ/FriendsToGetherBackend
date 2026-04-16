package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.ChatMessageResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    // --- [REST API]: Fetch chat history when user opens a conversation ---
    @GetMapping("/history/{otherUserId}")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @PathVariable String otherUserId,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {

        if (principal == null) {
            log.warn("[CHAT-API] Unauthorized history request — no principal");
            return ResponseEntity.status(401).build();
        }

        String myEmail = principal.getName();
        log.info("[CHAT-API] History request — user: {}, otherUserId: {}, limit: {}", myEmail, otherUserId, limit);

        User myUser = userRepository.findByEmail(myEmail)
                .orElseThrow(() -> {
                    log.error("[CHAT-API] User not found for email: {}", myEmail);
                    return new RuntimeException("User not found: " + myEmail);
                });

        List<ChatMessageResponse> history = chatService.getChatHistory(myUser.getUserId(), otherUserId, limit);
        log.info("[CHAT-API] History returned — {} messages", history.size());

        return ResponseEntity.ok(history);
    }

    // --- [REST API]: Mark messages as READ when user opens the chat ---
    @PostMapping("/read/{roomId}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String roomId,
            Principal principal) {

        if (principal == null) {
            log.warn("[CHAT-API] Unauthorized read-receipt request — no principal");
            return ResponseEntity.status(401).build();
        }

        String myEmail = principal.getName();
        log.info("[CHAT-API] Read receipt request — user: {}, roomId: {}", myEmail, roomId);

        chatService.markMessagesAsRead(roomId, myEmail);
        log.info("[CHAT-API] Read receipt processed for room: {}", roomId);

        return ResponseEntity.ok().build();
    }

    // --- [REST API]: Delete Message (For Me or Everyone) ---
    @DeleteMapping("/message/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @RequestParam String type,
            Principal principal) {

        if (principal == null) {
            log.warn("[CHAT-API] Unauthorized delete request — no principal");
            return ResponseEntity.status(401).build();
        }

        String myEmail = principal.getName();
        log.info("[CHAT-API] Delete request — user: {}, messageId: {}, type: {}", myEmail, messageId, type);

        chatService.deleteMessage(messageId, type, myEmail);
        log.info("[CHAT-API] Delete processed — messageId: {}, type: {}", messageId, type);

        return ResponseEntity.ok().build();
    }
}
