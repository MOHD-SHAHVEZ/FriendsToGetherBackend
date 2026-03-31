package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.response.SocialUserResponse;
import com.gaur.friendstogether.entity.Friendship;
import com.gaur.friendstogether.entity.FriendshipStatus;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.FriendshipRepository;
import com.gaur.friendstogether.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public SocialUserResponse searchByUserId(String targetUserId, String currentUserEmail) {
        log.debug("Entering searchByUserId. targetUserId: {}, currentUserEmail: {}", targetUserId, currentUserEmail);

        // 1. Current user ko dhoondho (jo search kar raha hai)
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> {
                    log.error("Current user not found for email: {}", currentUserEmail);
                    return new RuntimeException("Current user not found");
                });

        // 2. Target user ko dhoondho (jise search kiya ja raha hai)
        User targetUser = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> {
                    log.error("Target user not found for userId: {}", targetUserId);
                    return new RuntimeException("User with ID " + targetUserId + " not found");
                });

        // 3. Status decide karo
        String friendshipStatus = "STRANGER";

        if (currentUser.getUserId().equals(targetUserId)) {
            friendshipStatus = "SELF";
            log.debug("Target user is the current user. Status: {}", friendshipStatus);
        } else {
            // Check if any request exists (Either Sent or Received)
            Optional<Friendship> requestSent = friendshipRepository.findBySenderAndReceiver(currentUser, targetUser);
            Optional<Friendship> requestReceived = friendshipRepository.findBySenderAndReceiver(targetUser, currentUser);

            if (requestSent.isPresent()) {
                friendshipStatus = (requestSent.get().getStatus() == FriendshipStatus.ACCEPTED) ? "FRIENDS" : "PENDING_SENT";
                log.debug("Found sent request. Status resolved to: {}", friendshipStatus);
            } else if (requestReceived.isPresent()) {
                friendshipStatus = (requestReceived.get().getStatus() == FriendshipStatus.ACCEPTED) ? "FRIENDS" : "PENDING_RECEIVED";
                log.debug("Found received request. Status resolved to: {}", friendshipStatus);
            } else {
                log.debug("No existing relationship found. Status remains: {}", friendshipStatus);
            }
        }

        return SocialUserResponse.builder()
                .userId(targetUser.getUserId())
                .nickname(targetUser.getProfile().getNickname())
                .avatarUrl(targetUser.getProfile().getAvatarUrl())
                .level(targetUser.getProfile().getLevel())
                .status(friendshipStatus)
                .build();
    }

    // ==================
    // Send Friend Request
    // ==================
    @Transactional
    public String sendFriendRequest(String targetUserId, String currentUserEmail) {
        log.debug("Entering sendFriendRequest. targetUserId: {}, currentUserEmail: {}", targetUserId, currentUserEmail);

        User sender = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> {
                    log.error("Sender not found for email: {}", currentUserEmail);
                    return new RuntimeException("Sender not found");
                });

        User receiver = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> {
                    log.error("Receiver not found for userId: {}", targetUserId);
                    return new RuntimeException("Receiver not found");
                });

        // 1. Khud ko dosti nahi bhej sakte!
        if (sender.getUserId().equals(targetUserId)) {
            log.warn("User attempted to send a friend request to themselves: {}", currentUserEmail);
            return "Error: You cannot send a friend request to yourself.";
        }

        // 2. Check karo ki koi purani request toh nahi hai (Either direction)
        Optional<Friendship> existingRequest = friendshipRepository.findBySenderAndReceiver(sender, receiver);
        Optional<Friendship> reverseRequest = friendshipRepository.findBySenderAndReceiver(receiver, sender);

        if (existingRequest.isPresent() || reverseRequest.isPresent()) {
            log.warn("A friend request already exists between sender: {} and receiver: {}", currentUserEmail, targetUserId);
            return "Error: A friend request or friendship already exists between you two.";
        }

        // 3. Sab theek hai, toh naya record insert karo
        Friendship friendship = Friendship.builder()
                .sender(sender)
                .receiver(receiver)
                .status(FriendshipStatus.PENDING)
                .build();

        friendshipRepository.save(friendship);
        log.info("Successfully sent friend request from {} to {}", currentUserEmail, targetUserId);
        return "Friend request sent successfully!";
    }

}