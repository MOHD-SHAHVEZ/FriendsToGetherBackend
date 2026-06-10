package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.RoleChangeRequest;
import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.dto.request.VoiceRoomRequest;
import com.gaur.friendstogether.dto.response.ModerationLogResponse;
import com.gaur.friendstogether.dto.response.RoomMemberResponse;
import com.gaur.friendstogether.dto.response.VoiceRoomResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.VoiceRoom;
import com.gaur.friendstogether.exception.BusinessLogicException;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.VoiceRoomService;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/voice-rooms")
@RequiredArgsConstructor
public class VoiceRoomController {

    private final VoiceRoomService voiceRoomService;
    private final UserRepository userRepository;

    /**
     * POST /api/v1/voice-rooms
     * Naya room create karna (REST Standard: POST on collection)
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VoiceRoom>> createRoom(
            @Valid @RequestBody VoiceRoomRequest request,
            Authentication authentication
    ) {
        // 1. Security Check
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("User not authenticated");
        }

        String currentUserEmail = authentication.getName();

        log.info("Create Room Request → user: {}, title: {}, locked: {}",
                currentUserEmail, request.getTitle(), request.isLocked());

        // 2. Fetch User
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserEmail));

        // 3. Service Call
        VoiceRoom room = voiceRoomService.createRoom(request, currentUser);

        log.info("Room Created Successfully → roomId: {}, owner: {}",
                room.getRoomId(), currentUserEmail);

        // 4. Return 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<VoiceRoom>builder()
                        .success(true)
                        .message("Room created successfully!")
                        .data(room)
                        .build()
        );
    }

    /**
     * Endpoint to join an existing voice room.
     * * @param roomId 8-digit unique room identifier
     * @param password Optional password for locked rooms (Owners bypass this)
     * @return Map containing roomDetails and memberDetails
     */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinRoom(
            @RequestParam String roomId,
            @RequestParam(required = false) String password,
            Authentication authentication
    ) {
        // 1. Authenticated User ko fetch karna
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        log.info("API Call: User '{}' is joining room '{}'", user.getUserId(), roomId);

        // 2. Service call (Ab response mein Room + Member dono aayenge)
        Map<String, Object> result = voiceRoomService.joinRoom(roomId, password, user);

        // 3. Proper API Response return karna
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .success(true)
                        .message("Joined successfully!")
                        .data(result)
                        .build()
        );
    }

    /**
     * Room (Leave) karne ke liya:
     * @param roomId
     * @param authentication
     * @return
     */
    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<Map<String, Object>>> leaveRoom(
            @RequestParam String roomId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Map<String, Object> result = voiceRoomService.leaveRoom(roomId, user);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .success(true)
                        .message(result.get("status").equals("ALREADY_LEFT") ? "Already left" : "Left successfully")
                        .data(result)
                        .build()
        );
    }

    /**
     * @param roomId
     *  actor // wo user jo block karega member ko jaisa admin, ya, owner:
     * @param targetUserId
     * @param authentication
     * @return
     */
    @PostMapping("/kick")
    public ResponseEntity<ApiResponse<Map<String, Object>>> kickMember(
            @RequestParam String roomId,
            @RequestParam String targetUserId,
            Authentication authentication
    ) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Actor not found"));

        Map<String, Object> result = voiceRoomService.kickMember(roomId, targetUserId, actor);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .success(true)
                        .message("User kicked successfully!")
                        .data(result) // yaha se wo extra data aaiga (kisne , kisko, konsa Room -> se niak diya)
                        .build()
        );
    }

    @PostMapping("/ban")
    public ResponseEntity<ApiResponse<Map<String, Object>>> banMember(
            @RequestParam String roomId,
            @RequestParam String targetUserId,
            @RequestParam(required = false) String reason,
            Authentication authentication
    ) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Actor not found"));

        Map<String, Object> result = voiceRoomService.banMember(roomId, targetUserId, reason, actor);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .success(true)
                        .message("User banned successfully!")
                        .data(result) // yaha se wo extra data aaiga (kisne , kisko, konsa Room -> se block kar diya)
                        .build()
        );
    }

    /**
     * MODERATION HISTORY API
     * Get logs of all kicks, bans, and other actions in the room.
     * URL: GET /api/v1/voice-rooms/{roomId}/moderation-history?page=0&size=10
     */
    @GetMapping("/{roomId}/moderation-history")
    public ResponseEntity<ApiResponse<Page<ModerationLogResponse>>> getRoomModerationHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        log.info("API Call: Fetching moderation history for Room '{}', Page: {}, Size: {}", roomId, page, size);

        // Service se paginated data fetch karna
        Page<ModerationLogResponse> history = voiceRoomService.getRoomModerationHistory(roomId, page, size);

        // Standard ApiResponse mein wrap karke bhejna
        return ResponseEntity.ok(
                ApiResponse.<Page<ModerationLogResponse>>builder()
                        .success(true)
                        .message("Moderation history fetched successfully")
                        .data(history)
                        .build()
        );
    }

    /**
     * UNBAN MEMBER API
     * Owner or Admin can unban a blacklisted user.
     */
    @PostMapping("/unban")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unbanMember(
            @RequestParam @jakarta.validation.constraints.NotBlank(message = "Room ID is required") String roomId,
            @RequestParam @jakarta.validation.constraints.NotBlank(message = "Target User ID is required") String targetUserId,
            Authentication authentication
    ) {
        log.info("API Hit: Unban user request received");

        // Actor fetch karna (Jo unban kar raha hai)
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Actor not found"));

        // Service call and get response data
        Map<String, Object> result = voiceRoomService.unbanMember(roomId, targetUserId, actor);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .success(true)
                        .message("User unbanned successfully!")
                        .data(result)
                        .build()
        );
    }

    /**
     * Get All Members list those are present in Voice Room
     */
    @GetMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<Page<RoomMemberResponse>>> getMembers(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("API Hit: Fetching members for room '{}' (Page: {}, Size: {})", roomId, page, size);

        Page<RoomMemberResponse> members = voiceRoomService.getRoomMembers(roomId, page, size);

        return ResponseEntity.ok(
                ApiResponse.<Page<RoomMemberResponse>>builder()
                        .success(true)
                        .message("Member list fetched successfully")
                        .data(members)
                        .build()
        );
    }

    /**
     * PROMOTE MEMBER TO ADMIN
     * Security: Only Room Owner can promote.
     */
    @PostMapping("/promote")
    public ResponseEntity<ApiResponse<Map<String, Object>>> promoteUser(
            @Valid @RequestBody RoleChangeRequest request,
            @AuthenticationPrincipal User actor
    ) {
        // 1. Actor Null Safety check
        if (actor == null) {
            throw new BusinessLogicException("Unauthorized access!", HttpStatus.UNAUTHORIZED);
        }

        // 2. Structured Logging
        log.info("API_PROMOTE -> room={}, actor={}, target={}",
                request.getRoomId(), actor.getUserId(), request.getTargetUserId());

        // 3. Service Call
        Map<String, Object> result = voiceRoomService.promoteToAdmin(request, actor);

        // 4. Using Helper for Clean Response
        return buildResponse(result, "User promoted successfully");
    }

    /**
     * DEMOTE ADMIN TO MEMBER
     * Security: Only Room Owner can demote.
     */
    @PostMapping("/demote")
    public ResponseEntity<ApiResponse<Map<String, Object>>> demoteUser(
            @Valid @RequestBody RoleChangeRequest request,
            @AuthenticationPrincipal User actor
    ) {
        if (actor == null) {
            throw new BusinessLogicException("Unauthorized access!", HttpStatus.UNAUTHORIZED);
        }

        log.info("API_DEMOTE -> room={}, actor={}, target={}",
                request.getRoomId(), actor.getUserId(), request.getTargetUserId());

        Map<String, Object> result = voiceRoomService.demoteToMember(request, actor);

        return buildResponse(result, "User demoted successfully");
    }

    /**
     * Fetch the logged-in user's personal room. added by antigravity for personal room feature
     */
    @GetMapping("/my-room")
    public ResponseEntity<ApiResponse<VoiceRoomResponse>> getMyPersonalRoom(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        VoiceRoomResponse response = voiceRoomService.getPersonalRoom(user);

        return ResponseEntity.ok(
                ApiResponse.<VoiceRoomResponse>builder()
                        .success(true)
                        .message(response != null ? "Personal room found" : "No personal room found")
                        .data(response)
                        .build()
        );
    }

    /**
     * Helper Method: return Response (demoteUser & promoteUser) controller
     * DRY (Don't Repeat Yourself) logic to build consistent responses
     */
    private ResponseEntity<ApiResponse<Map<String, Object>>> buildResponse(Map<String, Object> result, String defaultMsg) {
        String message = (String) result.getOrDefault("message", defaultMsg);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .success(true)
                        .message(message)
                        .data(result)
                        .build()
        );
    }

}