package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.SocialUserResponse;
import com.gaur.friendstogether.service.SocialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
@Slf4j
public class SocialController {

    private final SocialService socialService;

    // ==================
    // Search User based on {userId}
    // ==================
    @GetMapping("/search/{userId}")
    public ResponseEntity<?> searchUser(
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Search request received for target userId: {}", userId);

        // Agar token nahi mila ya invalid hai
        if (userDetails == null) {
            log.warn("Unauthorized search attempt. UserDetails is null.");
            return ResponseEntity.status(401).body("Error: Unauthorized. Please login first.");
        }

        log.info("Search request initiated by user: {}", userDetails.getUsername());
        return ResponseEntity.ok(socialService.searchByUserId(userId, userDetails.getUsername()));
    }

    // ===================
    // Send Request Endpoint POST request
    // ===================
    @PostMapping("/send-request/{userId}")
    public ResponseEntity<String> sendRequest(
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Send friend request received for target userId: {}", userId);

        if (userDetails == null) {
            log.warn("Unauthorized send request attempt. UserDetails is null.");
            return ResponseEntity.status(401).body("Error: Unauthorized. Please login first.");
        }

        log.info("Friend request initiated by user: {}", userDetails.getUsername());
        String response = socialService.sendFriendRequest(userId, userDetails.getUsername());

        if (response.startsWith("Error")) {
            log.error("Friend request failed: {}", response);
            return ResponseEntity.badRequest().body(response);
        }
        
        log.info("Friend request success: {}", response);
        return ResponseEntity.ok(response);
    }

    // ===================
    // GET: My Pending Friend Requests (Inbox)
    // ===================
    @GetMapping("/requests/pending")
    public ResponseEntity<?> getPendingRequests(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).body("Unauthorized");

        log.info("Fetching pending requests for user: {}", userDetails.getUsername());
        return ResponseEntity.ok(socialService.getPendingRequests(userDetails.getUsername()));
    }

    // ===================
    // PUT: Accept Friend Request
    // ===================
    @PutMapping("/accept-request/{senderUserId}")
    public ResponseEntity<String> acceptRequest(
            @PathVariable String senderUserId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.status(401).body("Unauthorized");

        log.info("Accepting friend request from: {} by: {}", senderUserId, userDetails.getUsername());
        String response = socialService.acceptFriendRequest(senderUserId, userDetails.getUsername());

        if (response.startsWith("Error")) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    // ===================
    // DELETE: Cancel Sent Request
    // ===================
    @DeleteMapping("/cancel-request/{userId}")
    public ResponseEntity<String> cancelRequest(
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Cancel request for: {} by: {}", userId, userDetails.getUsername());
        String response = socialService.cancelFriendRequest(userId, userDetails.getUsername());

        if (response.startsWith("Error")) return ResponseEntity.badRequest().body(response);
        return ResponseEntity.ok(response);
    }

    // ===================
    // DELETE: Reject Incoming Request
    // ===================
    @DeleteMapping("/reject-request/{senderUserId}")
    public ResponseEntity<String> rejectRequest(
            @PathVariable String senderUserId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Reject request from: {} by: {}", senderUserId, userDetails.getUsername());
        String response = socialService.rejectFriendRequest(senderUserId, userDetails.getUsername());

        if (response.startsWith("Error")) return ResponseEntity.badRequest().body(response);
        return ResponseEntity.ok(response);
    }

    // ===================
    // GET: My Friends List
    // ===================
    @GetMapping("/friends")
    public ResponseEntity<?> getFriendsList(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).body("Unauthorized");

        log.info("Fetching friends list for: {}", userDetails.getUsername());
        return ResponseEntity.ok(socialService.getMyFriends(userDetails.getUsername()));
    }

    // ===================
    // DELETE: Unfriend a user
    // ===================
    @DeleteMapping("/unfriend/{friendUserId}")
    public ResponseEntity<String> unfriendUser(
            @PathVariable String friendUserId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Unfriend request for: {} by: {}", friendUserId, userDetails.getUsername());
        String response = socialService.unfriend(friendUserId, userDetails.getUsername());

        if (response.startsWith("Error")) return ResponseEntity.badRequest().body(response);
        return ResponseEntity.ok(response);
    }


}