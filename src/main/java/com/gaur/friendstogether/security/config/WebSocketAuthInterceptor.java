package com.gaur.friendstogether.security.config;

import com.gaur.friendstogether.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            log.warn("⚠️ [WS-AUTH] StompHeaderAccessor null mila — ye message skip ho raha hai");
            return message;
        }

        StompCommand command = accessor.getCommand();
        log.debug("📡 [WS-AUTH] STOMP Command aaya: {}", command);

        // ==================== CONNECT Frame — Authentication Yahan Hogi ====================
        if (StompCommand.CONNECT.equals(command)) {
            log.info("🔌 [WS-AUTH] STOMP CONNECT frame receive hua — Authentication shuru...");

            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.error("❌ [WS-AUTH] CONNECT frame mein Authorization header NAHI mila ya format galat hai!");
                log.error("❌ [WS-AUTH] Expected format: 'Authorization: Bearer <JWT_TOKEN>'");
                throw new IllegalArgumentException("WebSocket CONNECT mein valid Authorization header zaroori hai!");
            }

            String token = authHeader.substring(7);
            log.info("🔑 [WS-AUTH] JWT Token mila — Validation shuru ho rahi hai...");

            try {
                String email = jwtUtil.getEmailFromToken(token);
                log.info("📧 [WS-AUTH] Token se email nikala: {}", email);

                if (email != null && jwtUtil.validateAccessToken(token)) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    // ★ YEH SABSE ZAROORI LINE HAI — Principal set ho raha hai STOMP session par ★
                    accessor.setUser(authToken);

                    log.info("✅ [WS-AUTH] WebSocket AUTHENTICATED! User: {} | Authorities: {}",
                            email, userDetails.getAuthorities());
                } else {
                    log.error("❌ [WS-AUTH] Token INVALID hai ya email null mila! Email: {}", email);
                    throw new IllegalArgumentException("JWT Token invalid hai ya expire ho gaya!");
                }

            } catch (IllegalArgumentException e) {
                throw e; // Re-throw taaki connection reject ho jaye
            } catch (Exception e) {
                log.error("💀 [WS-AUTH] Authentication mein unexpected error: {}", e.getMessage(), e);
                throw new IllegalArgumentException("WebSocket authentication FAIL — Token check karo: " + e.getMessage());
            }
        }

        // ==================== SEND Frame — Debugging Logs ====================
        if (StompCommand.SEND.equals(command)) {
            String destination = accessor.getDestination();
            String user = accessor.getUser() != null ? accessor.getUser().getName() : "UNKNOWN (null)";
            log.info("📤 [WS-SEND] SEND frame aaya | User: {} | Destination: {}", user, destination);

            if (accessor.getUser() == null) {
                log.error("🚨 [WS-SEND] Principal NULL hai! CONNECT frame mein auth nahi hua tha!");
            }
        }

        // ==================== DISCONNECT Frame — Cleanup Log ====================
        if (StompCommand.DISCONNECT.equals(command)) {
            String user = accessor.getUser() != null ? accessor.getUser().getName() : "UNKNOWN";
            log.info("🔴 [WS-DISCONNECT] User disconnected: {}", user);
        }

        // ==================== SUBSCRIBE Frame — Log ====================
        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            String user = accessor.getUser() != null ? accessor.getUser().getName() : "UNKNOWN";
            log.info("📥 [WS-SUBSCRIBE] User: {} subscribed to: {}", user, destination);
        }

        // ★ YEH SECOND SABSE ZAROORI LINE HAI ★
        // Always rebuild the message using the accessor headers to ensure that our setUser() call is persisted
        return org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }
}
