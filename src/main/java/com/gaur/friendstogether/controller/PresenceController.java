package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * PRODUCTION READY: Presence Controller to handle Redis-based online/offline status.
 * Handles dot (.) in email path variables using Regex.
 */
@RestController
@RequestMapping("/api/chat/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceController {

    private final PresenceService presenceService;

    /**
     * 1. SINGLE USER STATUS
     * FIX: Use {email:.+} regex to allow dots in email (e.g., test.user@gmail.com)
     */
    @GetMapping("/status/{email:.+}")
    public ResponseEntity<Boolean> getStatus(@PathVariable String email) {
        log.info("[PRESENCE-API] Checking online status for: {}", email);
        return ResponseEntity.ok(presenceService.isOnline(email));
    }

    /**
     * 2. BULK STATUS CHECK
     * High performance: Fetches status for multiple users in one call.
     */
    @PostMapping("/bulk-status")
    public ResponseEntity<Map<String, Boolean>> getBulkStatus(@RequestBody Set<String> emails) {
        log.info("[PRESENCE-API] Bulk status request for {} users", emails != null ? emails.size() : 0);
        return ResponseEntity.ok(presenceService.getBulkStatus(emails));
    }

    /**
     * 3. ALL ONLINE COUNT
     * Returns total number of unique active users in Redis.
     */
    @GetMapping("/online-count")
    public ResponseEntity<Integer> getOnlineCount() {
        int count = presenceService.getOnlineUsers().size();
        log.debug("[PRESENCE-API] Total online users count requested: {}", count);
        return ResponseEntity.ok(count);
    }

    /**
     * 4. GLOBAL ONLINE LIST
     * Use case: Admin dashboard or discovery feature.
     */
    @GetMapping("/list-all")
    public ResponseEntity<Set<String>> getAllOnlineUsers() {
        log.info("[PRESENCE-API] Fetching all active user emails");
        return ResponseEntity.ok(presenceService.getOnlineUsers());
    }
}