package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.ChatRequest;
import com.gaur.friendstogether.entity.ChatMessage;
import com.gaur.friendstogether.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage") // Full path: /app/chat.sendMessage
    public void processMessage(@Payload ChatRequest chatRequest, Principal principal) {

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📩 [CHAT-CTRL] Naya message RECEIVE hua @MessageMapping par!");
        log.info("📩 [CHAT-CTRL] Payload: receiverId={}, content='{}', type={}",
                chatRequest.getReceiverId(), chatRequest.getContent(), chatRequest.getType());

        // 1. Principal null check — agar auth nahi hua toh yahan error aayega
        if (principal == null) {
            log.error("🚨 [CHAT-CTRL] PRINCIPAL NULL HAI! STOMP CONNECT mein authentication nahi hua!");
            log.error("🚨 [CHAT-CTRL] Message REJECT ho raha hai — pehle CONNECT frame mein JWT bhejo!");
            throw new IllegalStateException("User authenticated nahi hai! STOMP CONNECT mein JWT token bhejo.");
        }

        // 2. Sender ka email nikalna (authenticated user)
        String senderEmail = principal.getName();
        log.info("👤 [CHAT-CTRL] Sender identified: {}", senderEmail);
        log.info("📨 [CHAT-CTRL] Receiver ID: {}", chatRequest.getReceiverId());

        // 3. Message ko Database mein save karna via Service
        log.info("💾 [CHAT-CTRL] ChatService.saveMessage() call ho raha hai...");
        com.gaur.friendstogether.dto.response.ChatMessageResponse savedMsgDto = chatService.saveMessage(chatRequest, senderEmail);
        log.info("✅ [CHAT-CTRL] Message saved successfully in DB, DTO prepared.");

        // 4. Real-time Delivery: Direct Queue Routing (Bypassing Spring's Buggy User Registry)
        // Spring ki 'convertAndSendToUser' kabhi-kabhi STOMP sessions ko correctly map nahi karti
        // isliye hum direct unki specific ID path par broadcast karenge.
        String receiverUserId = savedMsgDto.getReceiver().getUserId();
        String destination = "/queue/chat-" + receiverUserId;
        log.info("📡 [CHAT-CTRL] Direct delivery shuru — Destination: {}", destination);

        messagingTemplate.convertAndSend(
                destination,
                savedMsgDto
        );

        log.info("✅ [CHAT-CTRL] Message DELIVERED to destination: {}", destination);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}