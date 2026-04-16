// --------------------------------
// Redis Configuration — Production Setup
// Provides both StringRedisTemplate (for Presence) and generic RedisTemplate (for JSON objects)
// ---------------------------------

package com.gaur.friendstogether.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
public class RedisConfig {

    /**
     * Explicit StringRedisTemplate for PresenceService (online_users Set).
     * Eliminates type ambiguity between RedisTemplate<String, Object> and RedisTemplate<String, String>.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("[REDIS] Configuring StringRedisTemplate for Presence tracking");
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        log.info("[REDIS] StringRedisTemplate configured successfully");
        return template;
    }

    /**
     * Generic RedisTemplate for JSON-serialized objects (future use: caching, sessions, etc.)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("[REDIS] Configuring RedisTemplate<String, Object> for JSON payloads");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key Serializer
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // JSON Value Serializer
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        log.info("[REDIS] RedisTemplate configured successfully");
        return template;
    }
}