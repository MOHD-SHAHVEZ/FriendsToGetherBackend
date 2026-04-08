package com.gaur.friendstogether.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // --- [CONNECTION]: Client yahan se connect karega (e.g., ws://localhost:8080/ws) ---
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // Production mein ise restrict karna zaroori hai
//                .withSockJS(); // Fallback option agar browser WebSocket support na kare

        log.info("🟢 [WS-CONFIG] STOMP Endpoint registered: /ws");
        log.info("🟢 [WS-CONFIG] Allowed Origins: * (Production mein restrict karna!)");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // --- [INCOMING]: Jo messages '@MessageMapping' par jayenge ---
        registry.setApplicationDestinationPrefixes("/app");

        // --- [OUTGOING]: Server yahan se clients ko data push karega ---
        // '/topic' for Groups, '/queue' for 1-on-1 private chat
        registry.enableSimpleBroker("/topic", "/queue", "/user");

        // --- [USER-SPECIFIC]: Private messaging ke liye prefix ---
        registry.setUserDestinationPrefix("/user");

        log.info("🟢 [WS-CONFIG] Message Broker configured:");
        log.info("   ➡️ App Destination Prefix: /app");
        log.info("   ➡️ Simple Broker: /topic, /queue, /user");
        log.info("   ➡️ User Destination Prefix: /user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // --- [ZAROORI]: Har incoming STOMP frame yahan se guzarega ---
        // WebSocketAuthInterceptor CONNECT frame se JWT check karega
        registration.interceptors(webSocketAuthInterceptor);

        log.info("🟢 [WS-CONFIG] WebSocketAuthInterceptor REGISTERED on inbound channel");
        log.info("🟢 [WS-CONFIG] Ab har STOMP CONNECT frame authenticate hoga!");
    }
}