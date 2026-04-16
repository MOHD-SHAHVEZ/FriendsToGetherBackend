package com.gaur.friendstogether.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpUserRegistry simpUserRegistry;

    private static final String ONLINE_USERS_KEY = "online_users";

    public void userOnline(String email) {
        log.info("[PRESENCE] User ONLINE: {}", email);
        try {
            stringRedisTemplate.opsForSet().add(ONLINE_USERS_KEY, email);
            log.debug("[REDIS] SADD {} → {}", ONLINE_USERS_KEY, email);
        } catch (Exception e) {
            log.warn("[REDIS] Failure during userOnline for '{}'. Falling back to in-memory. Error: {}", email, e.getMessage());
        }
    }

    public void userOffline(String email) {
        log.info("[PRESENCE] User OFFLINE: {}", email);
        try {
            stringRedisTemplate.opsForSet().remove(ONLINE_USERS_KEY, email);
            log.debug("[REDIS] SREM {} → {}", ONLINE_USERS_KEY, email);
        } catch (Exception e) {
            log.warn("[REDIS] Failure during userOffline for '{}'. Error: {}", email, e.getMessage());
        }
    }

    public boolean isOnline(String email) {
        try {
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, email);
            boolean result = Boolean.TRUE.equals(isMember);
            log.debug("[REDIS] SISMEMBER {} {} → {}", ONLINE_USERS_KEY, email, result);
            return result;
        } catch (Exception e) {
            log.warn("[REDIS] Unreachable. Falling back to SimpUserRegistry for '{}'. Error: {}", email, e.getMessage());
            return simpUserRegistry.getUser(email) != null;
        }
    }

    /**
     * Returns all currently online user emails.
     * Useful for admin dashboards and broadcast targeting.
     */
    public Set<String> getOnlineUsers() {
        try {
            Set<String> users = stringRedisTemplate.opsForSet().members(ONLINE_USERS_KEY);
            log.debug("[REDIS] SMEMBERS {} → {} users", ONLINE_USERS_KEY, users != null ? users.size() : 0);
            return users != null ? users : Collections.emptySet();
        } catch (Exception e) {
            log.warn("[REDIS] Failure fetching online users. Error: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
