package com.gaur.friendstogether.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // --- [CORE]: Isse WebSocket messaging enable hogi ---
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // --- [CONNECTION]: Frontend yahan se connect karega (e.g., http://localhost:8080/ws) ---
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Production mein ise restrict karna zaroori hai
                .withSockJS(); // Fallback option agar browser WebSocket support na kare
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
    }
}