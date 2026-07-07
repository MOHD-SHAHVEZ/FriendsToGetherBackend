package com.gaur.friendstogether.security.config;

import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.VoiceRoom;
import com.gaur.friendstogether.repository.RoomMemberRepository;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.VoiceRoomService;
import com.gaur.friendstogether.dto.response.PresenceEvent;
import com.gaur.friendstogether.service.ChatService;
import com.gaur.friendstogether.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final ChatService chatService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Injected to handle automatic room leave on disconnect
    private final VoiceRoomService voiceRoomService;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;

    /**
     * Fires on successful STOMP CONNECTED acknowledgment.
     * 1. Updates Redis presence → ONLINE
     * 2. Broadcasts presence event globally
     * 3. Delivers any pending offline messages
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        if (accessor.getUser() == null) {
            log.warn("[WS] SessionConnectedEvent fired but Principal is null — skipping");
            return;
        }

        String userEmail = accessor.getUser().getName();
        log.info("[WS] User connected: {}", userEmail);

        // 1. Update Redis Presence
        presenceService.userOnline(userEmail);

        // 2. Broadcast ONLINE status to all subscribed clients
        PresenceEvent presenceEvent = PresenceEvent.builder()
                .user(userEmail)
                .status("ONLINE")
                .build();
        messagingTemplate.convertAndSend("/topic/status", presenceEvent);
        log.info("[WS] Presence event broadcast: {} → ONLINE", userEmail);

        // 3. Deliver pending offline messages (wrapped in try-catch to prevent cascade
        // failure)
        try {
            log.info("[WS] Triggering offline message delivery for: {}", userEmail);
            chatService.deliverPendingMessages(userEmail);
        } catch (Exception e) {
            log.error("[WS] Failed to deliver pending messages for {}. Error: {}", userEmail, e.getMessage(), e);
            // Non-fatal: user is connected, messages can be fetched via history API
        }
    }

    /**
     * Fires on STOMP session disconnect (graceful or ungraceful).
     * 1. Updates Redis presence → OFFLINE
     * 2. Broadcasts presence event globally
     */

    // Voice room websocket check the owner is present in room or room status is
    // online or offline
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        if (accessor.getUser() == null) {
            log.warn("[WS] SessionDisconnectEvent fired but Principal is null — skipping");
            return;
        }

        String userEmail = accessor.getUser().getName();
        log.info("[WS] User disconnected: {}", userEmail);

        // 1. Update Redis Presence
        presenceService.userOffline(userEmail);

        // 2. Broadcast OFFLINE status to all subscribed clients
        PresenceEvent presenceEvent = PresenceEvent.builder()
                .user(userEmail)
                .status("OFFLINE")
                .build();
        messagingTemplate.convertAndSend("/topic/status", presenceEvent);
        log.info("[WS] Presence event broadcast: {} → OFFLINE", userEmail);

        // 3. Automatically leave active voice rooms
        try {
            Optional<User> optionalUser = userRepository.findByEmail(userEmail);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                List<VoiceRoom> activeRooms = roomMemberRepository.findActiveRoomsByUser(user);
                for (VoiceRoom room : activeRooms) {
                    log.info("[WS] Auto-leaving room '{}' for disconnected user '{}'", room.getRoomId(), userEmail);
                    voiceRoomService.leaveRoom(room.getRoomId(), user);
                }
            }
        } catch (Exception e) {
            log.error("[WS] Error while auto-leaving rooms for user '{}': {}", userEmail, e.getMessage(), e);
        }
    }
}
