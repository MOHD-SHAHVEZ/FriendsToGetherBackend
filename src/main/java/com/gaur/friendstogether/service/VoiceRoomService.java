package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.RoleChangeRequest;
import com.gaur.friendstogether.dto.request.RoomPasswordRequest;
import com.gaur.friendstogether.dto.request.RoomSettingsRequest;
import com.gaur.friendstogether.dto.request.VoiceRoomRequest;
import com.gaur.friendstogether.dto.response.RoomMemberResponse;
import com.gaur.friendstogether.dto.response.VoiceRoomResponse;
import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.exception.BusinessLogicException;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.RoomMemberRepository;
import com.gaur.friendstogether.repository.RoomBlacklistRepository;
import com.gaur.friendstogether.repository.VoiceRoomRepository;
import com.gaur.friendstogether.repository.RoomModerationLogRepository;
import com.gaur.friendstogether.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.gaur.friendstogether.dto.response.ModerationLogResponse;
import com.gaur.friendstogether.util.AppUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import com.gaur.friendstogether.entity.RoomRole;

import java.time.LocalDateTime;
import java.util.*;

/**
 * VoiceRoomService: Room creation aur management ka logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceRoomService {

    private final VoiceRoomRepository voiceRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoomBlacklistRepository roomBlacklistRepository;
    private final UserRepository userRepository;
    private final RoomModerationLogRepository roomModerationLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Naya Voice Room create karne ka liye.
     */
    @Transactional
    public VoiceRoom createRoom(VoiceRoomRequest request, User owner) {
        log.info("Starting Room Creation: Title='{}' by User='{}'", request.getTitle(), owner.getUsername());

        // 1. UNIQUE 8-DIGIT ID GENERATION (With Collision Check)
        String roomId = AppUtils.generateRoomId();
        int attempts = 0;

        while (voiceRoomRepository.existsByRoomId(roomId)) {
            if (attempts++ > 5) {
                log.error("Failed to generate unique Room ID after 5 attempts");
                throw new BusinessLogicException("Server Busy: Could not generate unique Room ID", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            roomId = AppUtils.generateRoomId();
        }

//        // 2. VALIDATE MAX USERS (Strict Limit: 10)
//        int maxUsers = (request.getMaxUsers() > 0 && request.getMaxUsers() <= 10)
//                ? request.getMaxUsers()
//                : 10;

        // 2. VALIDATE MAX SEATS (Limit: 10 seats for talking, unlimited for audience)
        int maxSeat = (request.getMaxSeat() > 0 && request.getMaxSeat() <= 10)
                ? request.getMaxSeat()
                : 10;

        //  ==================================================================
        // VALIDATE PERSONAL ROOM LIMIT (Strict: 1 User = 1 Personal Room)
        // Ye check karega ki agar naya room 'Personal' hai, toh owner ke paas pehle se koi true personal room toh nahi
        // 2.5 VALIDATE PERSONAL ROOM LIMIT (With Room Details Fetch)
        if (request.isPersonal()) {
            // Check karo agar room exists karta hai toh pura object fetch kar lo
            Optional<VoiceRoom> existingRoom = voiceRoomRepository.findByOwnerAndIsPersonalTrue(owner);

            if (existingRoom.isPresent()) {
                String existingId = existingRoom.get().getRoomId();
                log.warn("Room creation denied: User '{}' already owns Room '{}'", owner.getUsername(), existingId);

                // Error message mein details bhej rahe hain
                throw new BusinessLogicException(
                        "You already have a personal room (ID: " + existingId + "). Please join your existing room.",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        // 3. PASSWORD HASHING (Schema Field: passwordHash)
        String passwordHash = null;
        if (request.isLocked()) {
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BusinessLogicException("Password is required for locked rooms", HttpStatus.BAD_REQUEST);
            }
            passwordHash = passwordEncoder.encode(request.getPassword());
            log.debug("Room password hashed successfully for ID: {}", roomId);
        }

        // 4. SAVE VOICE ROOM (Entity Field Alignment)
        VoiceRoom room = VoiceRoom.builder()
                .roomId(roomId)
                .title(request.getTitle())
                .passwordHash(passwordHash) // Aligned with Schema
                .isLocked(request.isLocked())
                .isPersonal(request.isPersonal())
//                .maxUsers(maxUsers)
                .maxSeat(maxSeat)
                .owner(owner)
                .ownerUserId(owner.getUserId()) // GetUser-by-Game_ID(user_id)
                .status(RoomStatus.ACTIVE) // Aligned: Status instead of isActive
                .build();

        VoiceRoom savedRoom = voiceRoomRepository.save(room);
        log.info("VoiceRoom entity saved successfully with Numeric ID: {}", roomId);

        // 5. ADD OWNER AS FIRST MEMBER (Mapping Alignment)
        RoomMember ownerMember = RoomMember.builder()
                .room(savedRoom) // Align with voice room
                .user(owner)
                .userIdString(owner.getUserId()) // Ye tumhari game ID hai
                .roomIdString(savedRoom.getRoomId()) // Ye generated roomId hai
                .role(RoomRole.OWNER)
                .isActive(true)
                .build();
        roomMemberRepository.save(ownerMember);
        log.info("User '{}' assigned as OWNER for Room ID: {}", owner.getUsername(), roomId);

        return savedRoom;
    }

    /**
     * Kisi room mein join hone ke liye.
     */
    @Transactional
    public Map<String, Object> joinRoom(String roomId, String password, User user) {
        log.info("Join Request: User '{}' joining Room '{}'", user.getUserId(), roomId);

        // 1. Find Room
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found!", HttpStatus.NOT_FOUND));

        // 1.5 NAYA CHECK: Block users ko entry mat do
        if (roomBlacklistRepository.existsByRoomAndBlockedUser(room, user)) {
            throw new BusinessLogicException("Aapko is room se BAN kiya gaya hai!", HttpStatus.FORBIDDEN);
        }

        // 2. OWNER BYPASS: Agar user hi room ka owner hai, toh password check mat karo
        boolean isOwner = room.getOwner().getId().equals(user.getId());

        // 3. Password Check (Sirf tab jab user owner NAHI hai aur room locked hai)
        if (!isOwner && room.isLocked()) {
            if (password == null || !passwordEncoder.matches(password, room.getPasswordHash())) {
                throw new BusinessLogicException("Invalid password!", HttpStatus.UNAUTHORIZED);
            }
        }

        // 4. 🔥 CONCURRENCY SAFE: Member status check with PESSIMISTIC LOCK
        // Pehle hum lock ke saath member uthayenge taaki race condition na ho
        Optional<RoomMember> existingMember = roomMemberRepository.findByRoomAndUserWithLock(room, user);

        RoomMember member;
        boolean isStateChanging = false; // Flag to track if count should increment

        if (existingMember.isPresent()) {
            member = existingMember.get();
            // Agar banda pehle INACTIVE tha aur ab JOIN kar raha hai, tabhi state change maani jayegi
            if (!member.isActive()) {
                isStateChanging = true;
                member.setActive(true);
                member.setJoinedAt(LocalDateTime.now());
            }
        } else {
            // Naya member hai toh 100% state change hai
            isStateChanging = true;
            member = RoomMember.builder()
                    .room(room)
                    .user(user)
                    .role(isOwner ? RoomRole.OWNER : RoomRole.MEMBER)
                    .userIdString(user.getUserId())
                    .roomIdString(room.getRoomId())
                    .isActive(true)
                    .joinedAt(LocalDateTime.now())
                    .build();
        }

        roomMemberRepository.save(member);

        // 4.5 🔥 ATOMIC COUNT SYNC: Sirf tabhi increment jab state actually CHANGE hui ho
        if (isStateChanging) {
            voiceRoomRepository.updateMemberCount(room.getId(), 1);
            log.info("COUNT_INC -> Room: {}, User: {}", roomId, user.getUserId());
        }
        
        if (room.getStatus() == RoomStatus.CLOSED) {
            room.setStatus(RoomStatus.ACTIVE);
            voiceRoomRepository.save(room);
            log.info("Room '{}' became ACTIVE upon user join", roomId);
        }

// ============================================================================================
//        4. Capacity Check -> ya code maximum peoples 10 aa sakta hai room ma uska liya restriction tha iski jarurt nahi
//        room ma kitna hi peoples aa sakte hai only baat karna ka liya seat pe baitana hoga or seat max 10 hai.
//        long currentMembers = roomMemberRepository.countByRoomAndIsActiveTrue(room);
//        if (currentMembers >= room.getMaxUsers()) {
//            throw new BusinessLogicException("Room is full!", HttpStatus.BAD_REQUEST);
//        }
// ============================================================================================

        // 5. CUSTOM RESPONSE: Jisme Room aur Member dono ki detail ho
        Map<String, Object> response = new LinkedHashMap<>(); // LinkedHashMap se order sahi rehta hai
        response.put("roomDetails", room);
        response.put("memberDetails", member); // Isme join hone wale ki poori detail hogi

        return response;
    }

    /**
     * kisi Room Ko Leave Karne ke Liya.
     */
    @Transactional
    public Map<String, Object> leaveRoom(String roomId, User user) {
        log.info("Leave Request: User '{}' leaving Room '{}'", user.getUserId(), roomId);

        // 1. Fetch Room (Only Active rooms can be left)
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found!", HttpStatus.NOT_FOUND));

        // 2. CONCURRENCY SAFE: Fetch Member record with PESSIMISTIC LOCK
        // Lock lagana zaroori hai taaki state change (Active -> Inactive) atomic ho
        RoomMember member = roomMemberRepository.findByRoomAndUserWithLock(room, user)
                .orElseThrow(() -> new BusinessLogicException("You were never a member of this room!", HttpStatus.BAD_REQUEST));

        // 3. IDEMPOTENCY: Agar user pehle hi ja chuka hai, toh chill maaro (Success return karo)
        if (!member.isActive()) {
            log.info("Idempotent Leave: User '{}' had already left room '{}'", user.getUserId(), roomId);
            return buildLeaveResponse(roomId, "ALREADY_LEFT", room.getStatus(), user, 0, null);
        }

        // 4. Current User ka Exit mark karo
        LocalDateTime now = LocalDateTime.now();
        member.setActive(false);
        member.setLeftAt(now);
        roomMemberRepository.save(member);

        // 4.5 ATOMIC COUNT SYNC: Count tabhi minus hoga jab banda actually active tha
        // GREATEST(0, currentMemberCount - 1) wala logic repository query handle karegi
        voiceRoomRepository.updateMemberCount(room.getId(), -1);
        log.info("COUNT_DEC -> Room: {}, User: {}", roomId, user.getUserId());

        // 5. OWNER & ROOM LOGIC
        boolean isOwner = room.getOwner() != null && java.util.Objects.equals(room.getOwner().getId(), user.getId());
        String newHostUserId = null;
        String newHostUsername = null;

        if (isOwner) {
            if (!room.isPersonal()) {
                // PUBLIC/TEMPORARY ROOM: Owner leaving -> ALWAYS destroy the room
                // No ownership transfer — public rooms are temporary, creator leaving = room dies
                room.setStatus(RoomStatus.CLOSED);
                room.setCurrentMemberCount(0);
                voiceRoomRepository.save(room);

                // Sabhi remaining members ko ek saath bahar nikalo (Bulk Update)
                roomMemberRepository.deactivateAllByRoom(room, now);
                log.info("Owner left: Public Room '{}' destroyed and all members deactivated.", roomId);
            } else {
                // PERSONAL ROOM: Owner leaving -> Check if any OWNER/ADMIN remains
                boolean hasLeadership = roomMemberRepository.existsActiveOwnerOrAdmin(room, user);

                if (hasLeadership) {
                    // Rule 1: At least one Owner/Admin remains — room stays ACTIVE
                    log.info("Owner left: Personal Room '{}' stays ACTIVE — Owner/Admin still present.", roomId);
                    // Room status remains ACTIVE, no member deactivation needed
                } else {
                    // Rule 2: No Owner/Admin remains — close the room and remove everyone
                    room.setStatus(RoomStatus.CLOSED);
                    room.setCurrentMemberCount(0);
                    voiceRoomRepository.save(room);

                    // Sabhi members ko ek saath bahar nikalo (Bulk Update)
                    roomMemberRepository.deactivateAllByRoom(room, now);
                    log.info("Owner left: Personal Room '{}' went offline and all members deactivated (no Owner/Admin remaining).", roomId);
                }
            }
        }

        // Audit log for leave
        logModerationAction(room, user, user, ModerationAction.LEAVE, "User left the room");

        // 6. Get remaining active member count for broadcast
        long remainingPlayers = roomMemberRepository.countByRoomAndIsActiveTrue(room);

        log.info("User '{}' successfully left room '{}'", user.getUserId(), roomId);

        // 7. WEBSOCKET BROADCASTS (after transaction completes, best-effort)
        try {
            // 7a. Broadcast ROOM_PLAYER_LEFT to all remaining members
            Map<String, Object> playerLeftPayload = new LinkedHashMap<>();
            playerLeftPayload.put("event", "ROOM_PLAYER_LEFT");
            playerLeftPayload.put("roomId", roomId);
            playerLeftPayload.put("userId", user.getUserId());
            playerLeftPayload.put("username", user.getUsername());
            playerLeftPayload.put("remainingPlayers", remainingPlayers);
            messagingTemplate.convertAndSend("/topic/room." + roomId, (Object) playerLeftPayload);
            log.info("WS_BROADCAST -> ROOM_PLAYER_LEFT for room '{}', user '{}'", roomId, user.getUserId());

            // 7b. If host changed, broadcast ROOM_HOST_CHANGED
            if (newHostUserId != null) {
                Map<String, Object> hostChangedPayload = new LinkedHashMap<>();
                hostChangedPayload.put("event", "ROOM_HOST_CHANGED");
                hostChangedPayload.put("roomId", roomId);
                hostChangedPayload.put("newHostUserId", newHostUserId);
                hostChangedPayload.put("newHostUsername", newHostUsername);
                messagingTemplate.convertAndSend("/topic/room." + roomId, (Object) hostChangedPayload);
                log.info("WS_BROADCAST -> ROOM_HOST_CHANGED for room '{}', newHost '{}'", roomId, newHostUserId);
            }

            // 7c. If room closed, broadcast ROOM_CLOSED
            if (room.getStatus() == RoomStatus.CLOSED) {
                Map<String, Object> closedPayload = new LinkedHashMap<>();
                closedPayload.put("event", "ROOM_CLOSED");
                closedPayload.put("roomId", roomId);
                closedPayload.put("reason", isOwner ? "Owner left the room" : "Room closed");
                messagingTemplate.convertAndSend("/topic/room." + roomId, (Object) closedPayload);
                log.info("WS_BROADCAST -> ROOM_CLOSED for room '{}'", roomId);
            }
        } catch (Exception e) {
            // WebSocket broadcast failure should NOT fail the leave operation
            log.error("WS_BROADCAST_FAILED -> Room: {}, Error: {}", roomId, e.getMessage(), e);
        }

        // 8. Return Clean Response
        return buildLeaveResponse(roomId, "LEFT_SUCCESSFULLY", room.getStatus(), user, remainingPlayers, newHostUserId);
    }

    // Helper method taaki code clean rahe
    private Map<String, Object> buildLeaveResponse(String roomId, String status, RoomStatus roomStatus,
                                                    User user, long remainingPlayers, String newHostUserId) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("roomId", roomId);
        response.put("status", status);
        response.put("roomStatus", roomStatus);
        response.put("userId", user.getUserId());
        response.put("username", user.getUsername());
        response.put("remainingPlayers", remainingPlayers);
        if (newHostUserId != null) {
            response.put("newHostUserId", newHostUserId);
        }
        return response;
    }

    /**
     * =============================================================
     * KICK MEMBER: Safely removes a user from the room.
     * Prevents self-kick, owner-kick, and Admin-vs-Admin kick.
     * =============================================================
     */
    @Transactional
    public Map<String, Object> kickMember(String roomId, String targetUserId, User actor) {
        log.info("Kick Action: Actor '{}' -> Target '{}' [Room: {}]", actor.getUserId(), targetUserId, roomId);

        // 1. Fetch Room & Target User
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found", HttpStatus.NOT_FOUND));

        User targetUser = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new BusinessLogicException("Target user not found", HttpStatus.NOT_FOUND));

        // 2. Actor Validation (Must be active and have permissions)
        RoomMember actorMember = roomMemberRepository.findByRoomAndUser(room, actor)
                .orElseThrow(() -> new BusinessLogicException("You are not a member of this room!", HttpStatus.FORBIDDEN));

        if (!actorMember.isActive()) {
            throw new BusinessLogicException("You are not active in this room!", HttpStatus.FORBIDDEN);
        }

        boolean isOwner = Objects.equals(room.getOwner().getId(), actor.getId());
        boolean isAdmin = actorMember.getRole() == RoomRole.ADMIN;

        if (!(isOwner || isAdmin)) {
            throw new BusinessLogicException("Permission denied: Only Owner or Admin can kick!", HttpStatus.FORBIDDEN);
        }

        // 3. Safeguards (Self, Owner, Hierarchy)
        if (targetUser.getId().equals(actor.getId())) {
            throw new BusinessLogicException("You cannot kick yourself!", HttpStatus.BAD_REQUEST);
        }

        if (targetUser.getId().equals(room.getOwner().getId())) {
            throw new BusinessLogicException("Owner cannot be kicked!", HttpStatus.FORBIDDEN);
        }

        // Fetch target member with lock to avoid race conditions
        RoomMember targetMember = roomMemberRepository.findByRoomAndUser(room, targetUser)
                .orElseThrow(() -> new BusinessLogicException("User is not a member of this room", HttpStatus.BAD_REQUEST));

        // Admin vs Admin protection
        if (targetMember.getRole() == RoomRole.ADMIN && !isOwner) {
            throw new BusinessLogicException("Only the Owner can kick another Admin!", HttpStatus.FORBIDDEN);
        }

        if (!targetMember.isActive()) {
            throw new BusinessLogicException("User is already inactive!", HttpStatus.BAD_REQUEST);
        }

        // 4. Execute Kick
        targetMember.setActive(false);
        targetMember.setLeftAt(LocalDateTime.now());
        roomMemberRepository.save(targetMember);

        // 4.5 ATOMIC COUNT SYNC: Kicked member ka count bhi minus hona chahiye
        voiceRoomRepository.updateMemberCount(room.getId(), -1);
        log.info("COUNT_DEC (KICK) -> Room: {}, KickedUser: {}", roomId, targetUserId);

        log.info("Successfully kicked user '{}' from room '{}'", targetUserId, roomId);
        // Kick Logs Store in a Table (Kick -> store in RoomModerationLog)
        saveModerationLog(room, actor, targetUser, ModerationAction.KICK, "Kicked by moderator");

        // Return this reponse for clearity kisna kisko konsa room se niakla hai
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", roomId);
        result.put("kickedUserId", targetUserId);
        result.put("kickedBy", actor.getUserId());
        result.put("action", "KICK");

        return result;
    }

    /**
     * BAN MEMBER: Permanently blacklists a user from the room.
     * Works even if the user has already left.
     */
    @Transactional
    public Map<String, Object> banMember(String roomId, String targetUserId, String reason, User actor) {
        log.info("Ban Action: Actor '{}' -> Target '{}' [Room: {}]", actor.getUserId(), targetUserId, roomId);

        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found", HttpStatus.NOT_FOUND));

        User targetUser = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new BusinessLogicException("Target user not found", HttpStatus.NOT_FOUND));

        // 1. Idempotency Check (DB Unique Constraint will also back this up)
        if (roomBlacklistRepository.existsByRoomAndBlockedUser(room, targetUser)) {
            throw new BusinessLogicException("User is already banned from this room!", HttpStatus.BAD_REQUEST);
        }

        // 2. Pro-Fix: Kick only if user is currently active
        roomMemberRepository.findByRoomAndUser(room, targetUser)
                .ifPresent(member -> {
                    if (member.isActive()) {
                        kickMember(roomId, targetUserId, actor);
                    }
                });

        // 3. Clean and Save Blacklist Entry
        String finalReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Banned by moderator";

        RoomBlacklist banEntry = RoomBlacklist.builder()
                .room(room)
                .blockedUser(targetUser)
                .blockedBy(actor)
                .reason(finalReason)
                .build();

        roomBlacklistRepository.save(banEntry);
        log.info("Successfully banned user '{}' from room '{}'", targetUserId, roomId);
        // Ban(Block -> store in RoomModerationLog)
        saveModerationLog(room, actor, targetUser, ModerationAction.BAN, finalReason);

        // Return this reponse for clearity kisna kisko konsa room se Block kardiya hai.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", roomId);
        result.put("bannedUserId", targetUserId);
        result.put("bannedBy", actor.getUserId());
        result.put("reason", reason != null ? reason : "Banned by moderator");
        result.put("action", "BAN");

        return result;
    }

    private void saveModerationLog(VoiceRoom room, User actor, User target, ModerationAction action, String reason) {
        RoomModerationLog logEntry = RoomModerationLog.builder()
                .room(room)
                .actor(actor)
                .target(target)
                .action(action)
                .reason(reason)
                .build();
        roomModerationLogRepository.save(logEntry);
    }

    /**
     * block user ki history track or store karne ke liya
     * @param roomId
     * @param page
     * @param size
     * @return
     */
    public Page<ModerationLogResponse> getRoomModerationHistory(String roomId, int page, int size) {
        // 1. Pehle check karo room exist karta hai ya nahi
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found", HttpStatus.NOT_FOUND));

        // 2. Pagination aur Sorting setup (Latest logs hamesha upar dikhenge)
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());

        // 3. Repository se logs uthao aur DTO mein convert karo (Mapping starts here)
//        return roomModerationLogRepository.findLogsByRoom(room, pageable)
//                .map(log -> {
//                    // Actor ka naam nikaalo (Nickname preferred, else Username)
//                    String actorName = (log.getActor().getProfile() != null && log.getActor().getProfile().getNickname() != null)
//                            ? log.getActor().getProfile().getNickname()
//                            : log.getActor().getUsername();
//
//                    // Target ka naam nikaalo
//                    String targetName = (log.getTarget().getProfile() != null && log.getTarget().getProfile().getNickname() != null)
//                            ? log.getTarget().getProfile().getNickname()
//                            : log.getTarget().getUsername();

//                    return ModerationLogResponse.builder()
//                            .id(log.getId())
//                            .roomId(log.getRoom().getRoomId())
//                            .actorId(log.getActor().getUserId())
//                            .actorName(actorName)
//                            .targetId(log.getTarget().getUserId())
//                            .targetName(targetName)
//                            .action(log.getAction())
//                            .reason(log.getReason())
//                            .metadata(log.getMetadata())
//                            .createdAt(log.getCreatedAt())
//                            .build();
//                });

        return roomModerationLogRepository.findLogsByRoom(room, pageable);

    }

    // VoiceRoom ModerationLogs Fetch and return as pagination default 15 records page 0
    public Page<ModerationLogResponse> getRoomLogs(VoiceRoom room, Pageable pageable) {
        return roomModerationLogRepository.findLogsByRoom(room, pageable);
    }

    /**
     * UNBAN MEMBER: Implementation
     * Handles Idempotency, Race Conditions, and Role-Based Access.
     */
    @Transactional
    public Map<String, Object> unbanMember(String roomId, String targetUserId, User actor) {
        log.info("Unban Request: Actor '{}' -> Target '{}' in Room '{}'", actor.getUserId(), targetUserId, roomId);

        // 1. Basic Existence Checks
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found", HttpStatus.NOT_FOUND));

        User targetUser = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new BusinessLogicException("Target user not found", HttpStatus.NOT_FOUND));

        // 2. Edge Case: Self-Unban Check
        if (actor.getId().equals(targetUser.getId())) {
            throw new BusinessLogicException("You cannot unban yourself!", HttpStatus.BAD_REQUEST);
        }

        // 3. Authorization: Flexible Role-Based Permission
        RoomMember actorMember = roomMemberRepository.findByRoomAndUser(room, actor)
                .orElseThrow(() -> new BusinessLogicException("You are not a member of this room!", HttpStatus.FORBIDDEN));

        boolean isOwner = Objects.equals(room.getOwner().getId(), actor.getId());
        boolean isAdmin = actorMember.getRole() == RoomRole.ADMIN;

        if (!(isOwner || isAdmin)) {
            throw new BusinessLogicException("Permission denied: Only Owner or Admin can unban users!", HttpStatus.FORBIDDEN);
        }

        // 4. Idempotency Check: Prevent duplicate processing
        Optional<RoomBlacklist> banEntryOpt = roomBlacklistRepository.findByRoomAndBlockedUser(room, targetUser);

        if (banEntryOpt.isEmpty()) {
            log.warn("Idempotent Trigger: User '{}' was already unbanned from Room '{}'", targetUserId, roomId);
            // Silent Success: Data return kar rahe hain bina delete kiye
            return createUnbanResponse(roomId, targetUser, actor);
        }

        // 5. Atomic Delete: Handles potential race conditions
        roomBlacklistRepository.delete(banEntryOpt.get());

        // 6. Enhanced Audit Logging
        String auditReason = String.format("User unbanned by %s (ID: %s)",
                actor.getUsername(),
                actor.getUserId());

        logModerationAction(room, actor, targetUser, ModerationAction.UNBAN, auditReason);

        log.info("Unban Successful: User '{}' removed from blacklist by '{}'", targetUserId, actor.getUserId());

        return createUnbanResponse(roomId, targetUser, actor);
    }

    /**
     * HELPER 1: UnbanMember() -> function helper function return response
     *           Audit Log Save karne ke liye
     */
    private void logModerationAction(VoiceRoom room, User actor, User target, ModerationAction action, String reason) {
        RoomModerationLog logEntry = RoomModerationLog.builder()
                .room(room)
                .actor(actor)
                .target(target)
                .action(action)
                .reason(reason != null && !reason.isBlank() ? reason.trim() : "Action performed")
                .build();
        roomModerationLogRepository.save(logEntry);
    }

    /**
     * HELPER 2: UnbanMember() -> function helper function return response
     *           Controller ke liye Response Map banane ke liye
     */
    private Map<String, Object> createUnbanResponse(String roomId, User target, User actor) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("roomId", roomId);
        response.put("unbannedUserId", target.getUserId());
        response.put("unbannedUserName", target.getUsername());
        response.put("actorId", actor.getUserId());
        response.put("actorName", actor.getUsername());
        response.put("action", "UNBAN");
        return response;
    }

    /**
     * =====================================================
     * Room ma kon kon Members hai unki List nikalna ka liya
     * ======================================================
     */
    public Page<RoomMemberResponse> getRoomMembers(String roomId, int page, int size) {
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found", HttpStatus.NOT_FOUND));

        // Status Check: Agar room CLOSED hai toh aage badhne ka fayda hi nahi
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessLogicException("Cannot fetch members: This room is already closed.", HttpStatus.GONE);
        }

        Pageable pageable = PageRequest.of(page, size);

        return roomMemberRepository.findActiveMembersByRoom(room, pageable)
                .map(member -> {
                    User user = member.getUser();
                    UserProfile profile = user.getProfile();

                    // Fallback Logic: Pehle Nickname, fir Username
                    String displayName = (profile != null && profile.getNickname() != null)
                            ? profile.getNickname()
                            : user.getUsername();

                    return RoomMemberResponse.builder()
                            .userId(user.getUserId())
                            .nickname(displayName)
                            .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                            .role(member.getRole()) // Direct Enum passing
                            .isMuted(member.isMuted()) // UI control
                            .joinedAt(member.getJoinedAt())
                            .build();
                });
    }

    /**
     * PROMOTE TO ADMIN: Member -> Admin
     * Logic: Strict Hierarchy + Atomic Concurrency Lock + Level Limits
     */
    @Transactional
    public Map<String, Object> promoteToAdmin(RoleChangeRequest request, User actor) {
        String roomId = request.getRoomId().trim();
        String targetUserId = request.getTargetUserId().trim();

        log.info("PROMOTE_ACTION_START -> Actor: {}, Target: {}, Room: {}", actor.getUserId(), targetUserId, roomId);

        // 1. Core Fetching
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found!", HttpStatus.NOT_FOUND));

        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessLogicException("Action failed: Room is closed!", HttpStatus.GONE);
        }

        // 2. Self-Action Check (Safety)
        if (actor.getUserId().equals(targetUserId)) {
            throw new BusinessLogicException("You cannot promote yourself!", HttpStatus.BAD_REQUEST);
        }

        // 3. CRITICAL: Actor Validation & Lock
        // Same order of locking (Actor first) to prevent deadlocks
        RoomMember actorMember = roomMemberRepository.findActiveMemberWithLock(room, actor)
                .orElseThrow(() -> new BusinessLogicException("Actor is not an active member of this room!", HttpStatus.FORBIDDEN));

        if (actorMember.getRole() != RoomRole.OWNER) {
            throw new BusinessLogicException("Access Denied: Only the OWNER can promote members!", HttpStatus.FORBIDDEN);
        }

        // 4. Target Fetch & Lock
        User targetUser = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        RoomMember targetMember = roomMemberRepository.findActiveMemberWithLock(room, targetUser)
                .orElseThrow(() -> new BusinessLogicException("Target is not an active member of this room!", HttpStatus.BAD_REQUEST));

        // 5. Hierarchy Guard
        if (targetMember.getRole() == RoomRole.OWNER) {
            throw new BusinessLogicException("Owner's role cannot be modified!", HttpStatus.BAD_REQUEST);
        }

        // 6. Idempotency (Already Admin?)
        if (targetMember.getRole() == RoomRole.ADMIN) {
            return createRoleChangeResponse(roomId, targetUser, actor, RoomRole.ADMIN, targetMember.getRole(), "User is already an Admin");
        }

        // 7. ADMIN LIMIT CHECK (Atomic Row Locking)
        List<RoomMember> activeAdmins = roomMemberRepository.findActiveAdminsWithLock(room, RoomRole.ADMIN);

        int maxAllowed = getAdminLimitByLevel(room.getRoomLevel());
        if (activeAdmins.size() >= maxAllowed) {
            throw new BusinessLogicException("Admin limit reached for Level " + room.getRoomLevel() + " (Max: " + maxAllowed + ")", HttpStatus.CONFLICT);
        }

        // 8. Execute Role Change
        RoomRole oldRole = targetMember.getRole();
        targetMember.setRole(RoomRole.ADMIN);
        // Dirty checking will update DB automatically

        // 9. Audit Log Entry
        logModerationAction(room, actor, targetUser, ModerationAction.PROMOTE, "Promoted to ADMIN by Owner");

        log.info("PROMOTE_SUCCESS -> Target {} is now ADMIN in Room {}", targetUserId, roomId);

        return createRoleChangeResponse(roomId, targetUser, actor, RoomRole.ADMIN, oldRole, "User successfully promoted to Admin");
    }

    /**
     * DEMOTE TO MEMBER: Admin -> Member
     * Logic: Mirror of Promote but without limit check
     */
    @Transactional
    public Map<String, Object> demoteToMember(RoleChangeRequest request, User actor) {
        String roomId = request.getRoomId().trim();
        String targetUserId = request.getTargetUserId().trim();

        log.info("DEMOTE_ACTION_START -> Actor: {}, Target: {}, Room: {}", actor.getUserId(), targetUserId, roomId);

        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found!", HttpStatus.NOT_FOUND));

        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessLogicException("Action failed: Room is closed!", HttpStatus.GONE);
        }

        if (actor.getUserId().equals(targetUserId)) {
            throw new BusinessLogicException("You cannot demote yourself!", HttpStatus.BAD_REQUEST);
        }

        // Actor Lock (Order: 1)
        RoomMember actorMember = roomMemberRepository.findActiveMemberWithLock(room, actor)
                .orElseThrow(() -> new BusinessLogicException("Actor is not an active member!", HttpStatus.FORBIDDEN));

        if (actorMember.getRole() != RoomRole.OWNER) {
            throw new BusinessLogicException("Access Denied: Only the OWNER can demote admins!", HttpStatus.FORBIDDEN);
        }

        User targetUser = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        // Target Lock (Order: 2)
        RoomMember targetMember = roomMemberRepository.findActiveMemberWithLock(room, targetUser)
                .orElseThrow(() -> new BusinessLogicException("Target is not an active member!", HttpStatus.BAD_REQUEST));

        if (targetMember.getRole() == RoomRole.OWNER) {
            throw new BusinessLogicException("Owner's role cannot be modified!", HttpStatus.BAD_REQUEST);
        }

        // Idempotency (Already Member?)
        if (targetMember.getRole() == RoomRole.MEMBER) {
            return createRoleChangeResponse(roomId, targetUser, actor, RoomRole.MEMBER, targetMember.getRole(), "User is already a Member");
        }

        // Execute Role Change
        RoomRole oldRole = targetMember.getRole();
        targetMember.setRole(RoomRole.MEMBER);

        logModerationAction(room, actor, targetUser, ModerationAction.DEMOTE, "Admin demoted to Member by Owner");

        log.info("DEMOTE_SUCCESS -> Target {} is now MEMBER in Room {}", targetUserId, roomId);

        return createRoleChangeResponse(roomId, targetUser, actor, RoomRole.MEMBER, oldRole, "User successfully demoted to Member");
    }

    /**
     * Helper: promoteToAdmin Function ko Admin ki limit return karega
     * Admin Limits by Level (Fixed Range Logic)
     */
    private int getAdminLimitByLevel(int level) {
        if (level <= 1) return 4;
        if (level == 2) return 8;
        return 15; // Level 3 and above
    }

    /**
     * Helper: promoteToAdmin or demoteToMember ko Role return karega
     * Standard Structured Response
     */
    private Map<String, Object> createRoleChangeResponse(String roomId, User target, User actor, RoomRole newRole, RoomRole oldRole, String message) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("roomId", roomId);
        data.put("targetUserId", target.getUserId());
        data.put("targetUserName", target.getUsername());
        data.put("oldRole", oldRole);
        data.put("newRole", newRole);
        data.put("updatedBy", actor.getUserId());
        data.put("timestamp", java.time.LocalDateTime.now());

        response.put("data", data);
        return response;
    }

    /**
     * DISCOVERY: Get rooms with Category filter and Pagination
     * Team Lead Suggestion: Mapping inside Service to avoid LazyInitializationException
     */
    @Transactional(readOnly = true)
    public Page<VoiceRoomResponse> getDiscoveryRooms(RoomCategory category, Pageable pageable) {
        log.info("SERVICE_DISCOVERY -> Category: {}, Page: {}", category, pageable.getPageNumber());

        Page<VoiceRoom> rooms;
        if (category != null && category != RoomCategory.GENERAL) {
            rooms = voiceRoomRepository.findDiscoveryRoomsByCategory(RoomStatus.ACTIVE, category, pageable);
        } else {
            rooms = voiceRoomRepository.findDiscoveryRooms(RoomStatus.ACTIVE, pageable);
        }

        // Mapping Entity to DTO here (Inside Transaction)
        return rooms.map(this::convertToDto);
    }

    /**
     * SEARCH: Room ID se exact match (With Trim & Safety)
     * BUG FIX: Personal rooms CLOSED state mein bhi search ho sakti hain Room ID se.
     * Public rooms ko CLOSED state mein search nahi karna (temporary rooms hain).
     */
    @Transactional(readOnly = true)
    public VoiceRoomResponse searchRoomById(String roomId) {
        if (roomId == null) throw new BusinessLogicException("Room ID cannot be null", HttpStatus.BAD_REQUEST);

        String cleanRoomId = roomId.trim(); // Team Lead Suggestion: Trim spaces
        log.info("SERVICE_SEARCH -> RoomID: {}", cleanRoomId);

        VoiceRoom room = voiceRoomRepository.findByRoomId(cleanRoomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found!", HttpStatus.NOT_FOUND));

        // Public rooms jo CLOSED hain wo search mein nahi dikhni chahiye (temporary rooms hain)
        if (!room.isPersonal() && room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessLogicException("Room not found or is closed!", HttpStatus.NOT_FOUND);
        }

        return convertToDto(room);
    }

    /**
     * PRIVATE HELPER: Entity to DTO Mapping (Centralized)
     */
    private VoiceRoomResponse convertToDto(VoiceRoom room) {
        String ownerName = "Unknown";
        String ownerUserId = null;

        if (room.getOwner() != null) {
            ownerUserId = room.getOwner().getUserId();
            // Local variable optimization for better readability
            var profile = room.getOwner().getProfile();
            if (profile != null && profile.getNickname() != null) {
                ownerName = profile.getNickname();
            } else {
                ownerName = room.getOwner().getUsername();
            }
        }

        return VoiceRoomResponse.builder()
                .roomId(room.getRoomId())
                .title(room.getTitle())
                .ownerName(ownerName)
                .ownerUserId(ownerUserId)
                .memberCount(room.getCurrentMemberCount())
                .roomLevel(room.getRoomLevel())
                .isLocked(room.isLocked())
                .category(room.getCategory())
                .createdAt(room.getCreatedAt())
                .isPersonal(room.isPersonal())
                .maxSeat(room.getMaxSeat())
                .status(room.getStatus())
                .build();
    }

    @org.springframework.transaction.annotation.Transactional
    public VoiceRoomResponse getPersonalRoom(com.gaur.friendstogether.entity.User owner) {
        return voiceRoomRepository.findByOwnerAndIsPersonalTrue(owner)
                .map(this::convertToDto)
                .orElseGet(() -> {
                    log.info("No personal room found for User '{}'. Auto-allocating one now.", owner.getUsername());
                    VoiceRoom newRoom = allocatePersonalRoom(owner);
                    return convertToDto(newRoom);
                });
    }

    /**
     * Helper: Allocates a private room for a user in 'CLOSED' (Offline) state.
     * This is used during auto-allocation (Registration) or Lazy initialization.
     */
    @org.springframework.transaction.annotation.Transactional
    public VoiceRoom allocatePersonalRoom(com.gaur.friendstogether.entity.User owner) {
        // Double check to prevent duplicates
        return voiceRoomRepository.findByOwnerAndIsPersonalTrue(owner).orElseGet(() -> {
            String roomId = AppUtils.generateRoomId();
            int attempts = 0;
            while (voiceRoomRepository.existsByRoomId(roomId)) {
                if (attempts++ > 5) {
                    throw new BusinessLogicException("Server Busy: Could not generate unique Room ID", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
                }
                roomId = AppUtils.generateRoomId();
            }

            VoiceRoom room = VoiceRoom.builder()
                    .roomId(roomId)
                    .title(owner.getUsername() + "'s Private Room")
                    .isLocked(false)
                    .isPersonal(true)
                    .maxSeat(10)
                    .owner(owner)
                    .ownerUserId(owner.getUserId())
                    .status(RoomStatus.CLOSED) // Starts offline
                    .currentMemberCount(0)
                    .build();

            VoiceRoom savedRoom = voiceRoomRepository.save(room);

            // Add owner as member but INACTIVE (since they haven't joined yet)
            com.gaur.friendstogether.entity.RoomMember ownerMember = com.gaur.friendstogether.entity.RoomMember.builder()
                    .room(savedRoom)
                    .user(owner)
                    .userIdString(owner.getUserId())
                    .roomIdString(savedRoom.getRoomId())
                    .role(com.gaur.friendstogether.entity.RoomRole.OWNER)
                    .isActive(false)
                    .build();
            roomMemberRepository.save(ownerMember);

            log.info("Successfully allocated private offline room '{}' for user '{}'", roomId, owner.getUsername());
            return savedRoom;
        });
    }

    // =====================================================================================
    // FEATURE 1: Owner Controlled Password Management
    // =====================================================================================

    /**
     * Room ka password enable/disable/update karna.
     * Sirf Owner ke paas ye power hai.
     */
    @Transactional
    public Map<String, Object> updateRoomPassword(RoomPasswordRequest request, User actor) {
        String roomId = request.getRoomId().trim();
        log.info("PASSWORD_UPDATE -> Actor: {}, Room: {}, Enabled: {}", actor.getUserId(), roomId, request.isEnabled());

        // 1. Room fetch karo
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found!", HttpStatus.NOT_FOUND));

        // 2. Owner check karo (Sirf owner hi password manage karega)
        if (!Objects.equals(room.getOwner().getId(), actor.getId())) {
            throw new BusinessLogicException("Permission denied: Only the room owner can manage passwords!", HttpStatus.FORBIDDEN);
        }

        // 3. Room ACTIVE hona chahiye
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessLogicException("Cannot update password: Room is closed!", HttpStatus.GONE);
        }

        // 4. Password logic
        if (request.isEnabled()) {
            // ENABLE or UPDATE password
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BusinessLogicException("Password is required when enabling password protection!", HttpStatus.BAD_REQUEST);
            }
            room.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            room.setLocked(true);
            log.info("PASSWORD_ENABLED -> Room: {}", roomId);
        } else {
            // DISABLE password
            room.setPasswordHash(null);
            room.setLocked(false);
            log.info("PASSWORD_DISABLED -> Room: {}", roomId);
        }

        voiceRoomRepository.save(room);

        // 5. Audit log
        String action = request.isEnabled() ? "Password enabled/updated" : "Password disabled";
        logModerationAction(room, actor, actor, ModerationAction.PASSWORD_UPDATE, action);

        // 6. Response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("roomId", roomId);
        response.put("isLocked", room.isLocked());
        response.put("action", request.isEnabled() ? "PASSWORD_ENABLED" : "PASSWORD_DISABLED");
        response.put("updatedBy", actor.getUserId());
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    // =====================================================================================
    // FEATURE 5: Room Settings Update
    // =====================================================================================

    /**
     * Room ki settings update karna (Partial Update supported).
     * Sirf Owner ke paas ye power hai.
     */
    @Transactional
    public Map<String, Object> updateRoomSettings(RoomSettingsRequest request, User actor) {
        String roomId = request.getRoomId().trim();
        log.info("SETTINGS_UPDATE -> Actor: {}, Room: {}", actor.getUserId(), roomId);

        // 1. Room fetch karo
        VoiceRoom room = voiceRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessLogicException("Room not found!", HttpStatus.NOT_FOUND));

        // 2. Owner check karo
        if (!Objects.equals(room.getOwner().getId(), actor.getId())) {
            throw new BusinessLogicException("Permission denied: Only the room owner can update settings!", HttpStatus.FORBIDDEN);
        }

        // 3. Room ACTIVE hona chahiye
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessLogicException("Cannot update settings: Room is closed!", HttpStatus.GONE);
        }

        // 4. Partial Update: Sirf provided fields update honge
        List<String> updatedFields = new ArrayList<>();

        // 4a. Title update
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            room.setTitle(request.getTitle().trim());
            updatedFields.add("title");
        }

        // 4b. MaxSeat update
        if (request.getMaxSeat() != null) {
            room.setMaxSeat(request.getMaxSeat());
            updatedFields.add("maxSeat");
        }

        // 4c. Category update
        if (request.getCategory() != null) {
            room.setCategory(request.getCategory());
            updatedFields.add("category");
        }

        // 4d. Password/Lock update (Complex logic)
        if (request.getIsLocked() != null) {
            if (request.getIsLocked()) {
                // Enable password protection
                if (request.getPassword() != null && !request.getPassword().isBlank()) {
                    room.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                } else if (room.getPasswordHash() == null || room.getPasswordHash().isBlank()) {
                    // No existing password and no new password provided
                    throw new BusinessLogicException("Password is required when enabling lock!", HttpStatus.BAD_REQUEST);
                }
                // If password already exists and no new one provided, keep existing password
                room.setLocked(true);
                updatedFields.add("isLocked");
            } else {
                // Disable password protection
                room.setPasswordHash(null);
                room.setLocked(false);
                updatedFields.add("isLocked");
            }
        } else if (request.getPassword() != null && !request.getPassword().isBlank()) {
            // Password provided without explicit lock change -> update password if room is already locked
            if (room.isLocked()) {
                room.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                updatedFields.add("password");
            } else {
                throw new BusinessLogicException("Room is not locked. Set isLocked=true to enable password protection.", HttpStatus.BAD_REQUEST);
            }
        }

        // 5. Kuch toh update hua hona chahiye
        if (updatedFields.isEmpty()) {
            throw new BusinessLogicException("No valid fields provided for update!", HttpStatus.BAD_REQUEST);
        }

        voiceRoomRepository.save(room);

        // 6. Audit log
        String reason = "Settings updated: " + String.join(", ", updatedFields);
        logModerationAction(room, actor, actor, ModerationAction.SETTINGS_UPDATE, reason);

        log.info("SETTINGS_UPDATED -> Room: {}, Fields: {}", roomId, updatedFields);

        // 7. Response with updated room data
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("roomId", roomId);
        response.put("updatedFields", updatedFields);
        response.put("roomDetails", convertToDto(room));
        response.put("updatedBy", actor.getUserId());
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}