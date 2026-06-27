package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.OAuthSessionData;
import com.gaur.friendstogether.dto.response.AuthTokenPair;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OAuthAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthAuthenticationService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public String generateAndStoreTempCode(String email, String username, String providerId, String provider) {
        String tempCode = UUID.randomUUID().toString();
        String key = "oauth:code:" + tempCode;
        
        OAuthSessionData sessionData = new OAuthSessionData(email, username, providerId, provider);
        redisTemplate.opsForValue().set(key, sessionData, 60, TimeUnit.SECONDS);
        logger.info("[OAUTH-EXCHANGE] Stored TEMP_CODE in Redis — key: {}, email: {}", key, email);
        
        return tempCode;
    }

    /**
     * Exchange the temp code for tokens.
     * Returns AuthTokenPair so the controller can set the refresh token as HttpOnly cookie.
     */
    public AuthTokenPair exchangeCodeForTokens(String tempCode, String deviceInfo, String ipAddress) {
        logger.info("[OAUTH-EXCHANGE] ===== exchangeCodeForTokens() CALLED =====");
        logger.info("[OAUTH-EXCHANGE] tempCode: {}", tempCode);
        
        String key = "oauth:code:" + tempCode;
        Object rawData = redisTemplate.opsForValue().get(key);
        
        if (rawData == null) {
            logger.error("[OAUTH-EXCHANGE] Redis key NOT FOUND or EXPIRED: {}", key);
            throw new RuntimeException("Error: Invalid or expired authorization code.");
        }
        
        logger.info("[OAUTH-EXCHANGE] Redis rawData class: {}", rawData.getClass().getName());
        
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
        
        redisTemplate.delete(key);
        
        User user = userRepository.findByEmail(sessionData.getEmail())
                .orElseThrow(() -> {
                    logger.error("[OAUTH-EXCHANGE] ===== CRITICAL FAILURE =====");
                    logger.error("[OAUTH-EXCHANGE] findByEmail('{}') returned EMPTY", sessionData.getEmail());
                    return new RuntimeException("Error: User not found during token exchange. Email looked up: " + sessionData.getEmail());
                });
                
        logger.info("[OAUTH-EXCHANGE] User FOUND — dbId: {}, email: {}, authProvider: {}", 
                user.getId(), user.getEmail(), user.getAuthProvider());
                
        // Generate access token
        String accessToken = jwtUtil.generateAccessToken(user.getEmail());

        // Generate + persist refresh token
        String rawRefreshToken = refreshTokenService.createRefreshToken(user, deviceInfo, ipAddress);
        
        logger.info("[OAUTH-EXCHANGE] Tokens generated successfully for: {}", user.getEmail());
        
        return AuthTokenPair.builder()
                .accessToken(accessToken)
                .rawRefreshToken(rawRefreshToken)
                .username(user.getUsername())
                .accessTokenExpiresIn(jwtUtil.getAccessTokenExpirationSeconds())
                .build();
    }
}
