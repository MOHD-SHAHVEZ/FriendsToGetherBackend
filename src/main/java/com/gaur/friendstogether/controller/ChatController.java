package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.ChatRequest;
import com.gaur.friendstogether.entity.ChatMessage;
import com.gaur.friendstogether.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate; // --- [CORE]: For pushing messages ---

    @MessageMapping("/chat.sendMessage") // Endpoint: /app/chat.sendMessage
    public void processMessage(@Payload ChatRequest chatRequest, Principal principal) {

        // 1. Sender ka email nikalna (Logged in user)
        String senderEmail = principal.getName();
        log.info("Message received from {} to {}", senderEmail, chatRequest.getReceiverId());

        // 2. Message ko Database mein save karna via Service
        ChatMessage savedMsg = chatService.saveMessage(chatRequest, senderEmail);

        // 3. Real-time Delivery: Target user ke private queue mein push karna
        // Path: /user/{receiverId}/queue/messages
        messagingTemplate.convertAndSendToUser(
                chatRequest.getReceiverId(),
                "/queue/messages",
                savedMsg
        );

        log.info("Message delivered to user queue: {}", chatRequest.getReceiverId());
    }
}