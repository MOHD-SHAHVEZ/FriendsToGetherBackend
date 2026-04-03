package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.ChatRequest;
import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.repository.*;
import com.gaur.friendstogether.util.ChatUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository; // --- Naya Repo Add Kiya ---
    private final UserRepository userRepository;

    @Transactional
    public ChatMessage saveMessage(ChatRequest request, String senderEmail) {
        // 1. Sender & Receiver Find Karo
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        User receiver = userRepository.findByUserId(request.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        // 2. Room ID Generate Karo
        String roomId = ChatUtils.generateRoomId(sender.getUserId(), receiver.getUserId());

        // 3. [ZAROORI]: ChatRoom Check & Update/Create Logic
        chatRoomRepository.findByRoomId(roomId).ifPresentOrElse(
                room -> {
                    // Room pehle se hai, toh sirf 'Last Message' details update karo
                    room.setLastMessage(request.getContent());
                    room.setLastMessageTime(LocalDateTime.now());
                    chatRoomRepository.save(room);
                },
                () -> {
                    // Room nahi hai, toh naya banao
                    ChatRoom newRoom = ChatRoom.builder()
                            .roomId(roomId)
                            .user1(sender)
                            .user2(receiver)
                            .lastMessage(request.getContent())
                            .lastMessageTime(LocalDateTime.now())
                            .build();
                    chatRoomRepository.save(newRoom);
                }
        );

        // 4. ChatMessage Build & Save
        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .sender(sender)
                .receiver(receiver)
                .content(request.getContent())
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .type(MessageType.valueOf(request.getType().toUpperCase())) // toUpperCase safe hai
                .build();

        return chatMessageRepository.save(message);
    }
}