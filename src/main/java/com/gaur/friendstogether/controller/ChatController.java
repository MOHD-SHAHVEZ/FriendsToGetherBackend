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
        log.info("👤 [CHAT-CTRL] Sender Principal Identity: '{}'", senderEmail);
        log.info("📨 [CHAT-CTRL] Intended Receiver ID: {}", chatRequest.getReceiverId());

        // 3. Message ko Database mein save karna via Service
        log.info("💾 [CHAT-CTRL] ChatService.saveMessage() call ho raha hai...");
        com.gaur.friendstogether.dto.response.ChatMessageResponse savedMsgDto = chatService.saveMessage(chatRequest, senderEmail);
        log.info("✅ [CHAT-CTRL] Message saved successfully in DB, DTO prepared.");

        log.info("✅ [CHAT-CTRL] Message processed successfully and handled by Service.");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}