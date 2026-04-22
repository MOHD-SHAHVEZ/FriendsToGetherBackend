package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.RoleChangeRequest;
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
            return buildLeaveResponse(roomId, "ALREADY_LEFT", room.getStatus());
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

        // 5. OWNER & TEMP ROOM LOGIC
        boolean isOwner = room.getOwner() != null && java.util.Objects.equals(room.getOwner().getId(), user.getId());

        if (isOwner && !room.isPersonal()) {
            // Temporary room hai aur Owner chhod raha hai -> Room band karo
            room.setStatus(RoomStatus.CLOSED);

            // Room band hote waqt count seedha 0 kar do kyunki sab nikal rahe hain
            room.setCurrentMemberCount(0);
            voiceRoomRepository.save(room);

            // Sabhi members ko ek saath bahar nikalo (Bulk Update)
            roomMemberRepository.deactivateAllByRoom(room, now);
            log.info("Owner left: Temporary Room '{}' closed and all members deactivated.", roomId);
        }

        log.info("User '{}' successfully left room '{}'", user.getUserId(), roomId);

        // 6. Return Clean Response
        return buildLeaveResponse(roomId, "LEFT_SUCCESSFULLY", room.getStatus());
    }

    // Helper method taaki code clean rahe
    private Map<String, Object> buildLeaveResponse(String roomId, String status, RoomStatus roomStatus) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("roomId", roomId);
        response.put("status", status);
        response.put("roomStatus", roomStatus);
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
     */
    @Transactional(readOnly = true)
    public VoiceRoomResponse searchRoomById(String roomId) {
        if (roomId == null) throw new BusinessLogicException("Room ID cannot be null", HttpStatus.BAD_REQUEST);

        String cleanRoomId = roomId.trim(); // Team Lead Suggestion: Trim spaces
        log.info("SERVICE_SEARCH -> RoomID: {}", cleanRoomId);

        VoiceRoom room = voiceRoomRepository.findByRoomIdAndStatus(cleanRoomId, RoomStatus.ACTIVE)
                .orElseThrow(() -> new BusinessLogicException("Room not found or is closed!", HttpStatus.NOT_FOUND));

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
                .build();
    }

}