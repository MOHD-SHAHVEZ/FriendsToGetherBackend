package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.OAuthSessionData;
import com.gaur.friendstogether.dto.response.AuthResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OAuthAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthAuthenticationService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public String generateAndStoreTempCode(String email, String username, String providerId, String provider) {
        String tempCode = UUID.randomUUID().toString();
        String key = "oauth:code:" + tempCode;
        
        OAuthSessionData sessionData = new OAuthSessionData(email, username, providerId, provider);
        redisTemplate.opsForValue().set(key, sessionData, 60, TimeUnit.SECONDS);
        logger.info("[OAUTH-EXCHANGE] Stored TEMP_CODE in Redis — key: {}, email: {}", key, email);
        
        return tempCode;
    }

    public AuthResponse exchangeCodeForTokens(String tempCode) {
        logger.info("[OAUTH-EXCHANGE] ===== exchangeCodeForTokens() CALLED =====");
        logger.info("[OAUTH-EXCHANGE] tempCode: {}", tempCode);
        
        String key = "oauth:code:" + tempCode;
        Object rawData = redisTemplate.opsForValue().get(key);
        
        if (rawData == null) {
            logger.error("[OAUTH-EXCHANGE] Redis key NOT FOUND or EXPIRED: {}", key);
            throw new RuntimeException("Error: Invalid or expired authorization code.");
        }
        
        logger.info("[OAUTH-EXCHANGE] Redis rawData class: {}", rawData.getClass().getName());
        logger.info("[OAUTH-EXCHANGE] Redis rawData content: {}", rawData);
        
        // Handling conversion properly as Redis json serializer might return LinkedHashMap
        OAuthSessionData sessionData;
        if (rawData instanceof OAuthSessionData) {
            sessionData = (OAuthSessionData) rawData;
            logger.info("[OAUTH-EXCHANGE] Deserialized directly as OAuthSessionData");
        } else {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            sessionData = mapper.convertValue(rawData, OAuthSessionData.class);
            logger.info("[OAUTH-EXCHANGE] Converted from {} to OAuthSessionData via ObjectMapper", rawData.getClass().getSimpleName());
        }
        
        logger.info("[OAUTH-EXCHANGE] Session email: '{}'", sessionData.getEmail());
        logger.info("[OAUTH-EXCHANGE] Session username: '{}'", sessionData.getUsername());
        logger.info("[OAUTH-EXCHANGE] Session provider: '{}'", sessionData.getProvider());
        logger.info("[OAUTH-EXCHANGE] Session providerId: '{}'", sessionData.getProviderId());
        
        redisTemplate.delete(key);
        
        // DIAGNOSTIC: Check total user count and list recent users
        long totalUsers = userRepository.count();
        logger.info("[OAUTH-EXCHANGE] Total users in database: {}", totalUsers);
        
        boolean emailExists = userRepository.existsByEmail(sessionData.getEmail());
        logger.info("[OAUTH-EXCHANGE] existsByEmail('{}') = {}", sessionData.getEmail(), emailExists);
        
        User user = userRepository.findByEmail(sessionData.getEmail())
                .orElseThrow(() -> {
                    logger.error("[OAUTH-EXCHANGE] ===== CRITICAL FAILURE =====");
                    logger.error("[OAUTH-EXCHANGE] findByEmail('{}') returned EMPTY", sessionData.getEmail());
                    logger.error("[OAUTH-EXCHANGE] But Redis had the session data. User was never persisted to MySQL.");
                    logger.error("[OAUTH-EXCHANGE] Total users in DB: {}", totalUsers);
                    return new RuntimeException("Error: User not found during token exchange. Email looked up: " + sessionData.getEmail());
                });
                
        logger.info("[OAUTH-EXCHANGE] User FOUND — dbId: {}, email: {}, authProvider: {}", 
                user.getId(), user.getEmail(), user.getAuthProvider());
                
        String accessToken = jwtUtil.generateToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        
        logger.info("[OAUTH-EXCHANGE] JWT tokens generated successfully for: {}", user.getEmail());
        logger.info("[OAUTH-EXCHANGE] ===== exchangeCodeForTokens() COMPLETED =====");
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .username(user.getUsername())
                .build();
    }
}
