package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.dto.response.VoiceRoomResponse;
import com.gaur.friendstogether.entity.RoomCategory;
import com.gaur.friendstogether.service.VoiceRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/discovery")
@RequiredArgsConstructor
@Slf4j
public class DiscoveryController {

    private final VoiceRoomService voiceRoomService;

    /**
     * DISCOVERY: Get active rooms with pagination & safety limits
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VoiceRoomResponse>>> getDiscovery(
            @RequestParam(required = false) RoomCategory category,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 1. Safety Check: Max size limit 50 (Server protection)
        int pageSize = Math.min(pageable.getPageSize(), 50);
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageSize, pageable.getSort());

        log.info("API_DISCOVERY -> Category: {}, Page: {}, Size: {}",
                category, safePageable.getPageNumber(), safePageable.getPageSize());

        Page<VoiceRoomResponse> response = voiceRoomService.getDiscoveryRooms(category, safePageable);

        String message = response.isEmpty() ? "No rooms available right now" : "Discovery rooms fetched successfully";

        // Pro-Tip: Builder use karke response bhejna
        return ResponseEntity.ok(
                ApiResponse.<Page<VoiceRoomResponse>>builder()
                        .success(true)
                        .message(message)
                        .data(response)
                        .build() // Timestamp @Builder.Default se auto-set ho jayega
        );
    }

    /**
     * SEARCH: Room ID search with proper trimming and validation
     */
    @GetMapping("/search/{roomId}")
    public ResponseEntity<ApiResponse<VoiceRoomResponse>> searchRoom(@PathVariable String roomId) {
        // 2. Trim check: Edge case handle karne ke liye
        if (roomId == null || roomId.trim().isBlank()) {
            log.warn("API_SEARCH_FAILED -> Invalid RoomID");
            return ResponseEntity.badRequest().body(
                    ApiResponse.<VoiceRoomResponse>builder()
                            .success(false)
                            .message("Invalid Room ID")
                            .build()
            );
        }

        String trimmedId = roomId.trim();
        log.info("API_SEARCH -> RoomID: {}", trimmedId);

        VoiceRoomResponse response = voiceRoomService.searchRoomById(trimmedId);

        return ResponseEntity.ok(
                ApiResponse.<VoiceRoomResponse>builder()
                        .success(true)
                        .message("Room found")
                        .data(response)
                        .build()
        );
    }
}