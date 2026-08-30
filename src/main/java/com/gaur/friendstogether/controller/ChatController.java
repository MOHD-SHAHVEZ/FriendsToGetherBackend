package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.ChatRequest;
import com.gaur.friendstogether.dto.response.ChatMessageResponse;
import com.gaur.friendstogether.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
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

        log.info("[CHAT-CTRL] Message received — receiverId: {}, type: {}", chatRequest.getReceiverId(), chatRequest.getType());

        // Principal null check — authentication must happen at STOMP CONNECT
        if (principal == null) {
            log.error("[CHAT-CTRL] Principal is NULL — STOMP CONNECT authentication failed");
            throw new IllegalStateException("User not authenticated. Send JWT in STOMP CONNECT frame.");
        }

        String senderEmail = principal.getName();
        log.info("[CHAT-CTRL] Sender: {} → Receiver: {}", senderEmail, chatRequest.getReceiverId());

        // Delegate to service — all business logic lives there
        ChatMessageResponse savedMsg = chatService.saveMessage(chatRequest, senderEmail);
        log.info("[CHAT-CTRL] Message processed — id: {}, status: {}", savedMsg.getId(), savedMsg.getStatus());
    }

    @MessageExceptionHandler
    public void handleException(Exception ex, Principal principal) {
        log.error("[CHAT-CTRL] Error processing message: {}", ex.getMessage(), ex);
        if (principal != null) {
            // Send the error message back to the sender
            messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", ex.getMessage());
        }
    }
}