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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional(readOnly = true)
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

        // --- [FRIENDS COUNT CHANGE]: Target user ke friends ki ginti nikalna ---
        long totalFriends = friendshipRepository.countAcceptedFriends(targetUser);

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
                .friendsCount(totalFriends) // --- [FRIENDS COUNT CHANGE]: Response mein set kar dena ---
                .email(targetUser.getEmail())
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

    // ===========================
    // Request Pending & Accept
    // ===========================

    // 1. Pending Requests nikalne ke liye
    @Transactional(readOnly = true)
    public List<SocialUserResponse> getPendingRequests(String currentUserEmail) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Hamari FriendshipRepository ka use karke sirf PENDING status wali requests nikalna
        return friendshipRepository.findByReceiverAndStatus(currentUser, FriendshipStatus.PENDING)
                .stream()
                .map(friendship -> {
                    User sender = friendship.getSender();
                    return SocialUserResponse.builder()
                            .userId(sender.getUserId())
                            .nickname(sender.getProfile().getNickname())
                            .avatarUrl(sender.getProfile().getAvatarUrl())
                            .level(sender.getProfile().getLevel())
                            .status("PENDING_RECEIVED")
                            .email(sender.getEmail())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ===============================
// 2. Request Accept karne ke liye (Bi-directional Fix)
// ===============================
    @Transactional
    public String acceptFriendRequest(String senderUserId, String currentUserEmail) {
        // 1. Receiver (User B) aur Sender (User A) dhoondo
        User receiver = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        User sender = userRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        // 2. Original Pending Request (A -> B) dhoondo
        Friendship friendship = friendshipRepository.findBySenderAndReceiver(sender, receiver)
                .orElseThrow(() -> new RuntimeException("Error: No pending request found from this user."));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            return "Error: Request is already " + friendship.getStatus();
        }

        // 3. Pehli Side UPDATE: (A -> B) Status = ACCEPTED
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);

        // 4. REVERSE ENTRY: (B -> A) Create karo taaki 2-way friendship ho
        // Pehle check karo kahi reverse entry pehle se toh nahi hai (safety check)
        boolean reverseExists = friendshipRepository.existsBySenderAndReceiver(receiver, sender);

        if (!reverseExists) {
            Friendship reverseFriendship = Friendship.builder()
                    .sender(receiver)    // Ab B sender ban gaya
                    .receiver(sender)    // A receiver ban gaya
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            friendshipRepository.save(reverseFriendship);
            log.info("BIDIRECTIONAL_SYNC -> Reverse friendship created for {} and {}", receiver.getUserId(), sender.getUserId());
        }

        return "Success: You and " + sender.getProfile().getNickname() + " are now friends!";
    }
    // ============================================
    // 1. Apni bheji hui request wapas lena (Cancel)
    // ============================================
    @Transactional
    public String cancelFriendRequest(String targetUserId, String currentUserEmail) {
        User sender = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        Friendship friendship = friendshipRepository.findBySenderAndReceiver(sender, receiver)
                .orElseThrow(() -> new RuntimeException("Error: No pending request found to cancel."));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            return "Error: Cannot cancel a request that is already " + friendship.getStatus();
        }

        friendshipRepository.delete(friendship);
        return "Friend request cancelled successfully!";
    }

    // 2. Aayi hui request ko thukrana (Reject)
    @Transactional
    public String rejectFriendRequest(String senderUserId, String currentUserEmail) {
        User receiver = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));
        User sender = userRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        Friendship friendship = friendshipRepository.findBySenderAndReceiver(sender, receiver)
                .orElseThrow(() -> new RuntimeException("Error: No pending request found to reject."));

        // Reject mein hum ya toh status badalte hain ya delete kar dete hain.
        // Delete karna best hai taaki user firse request bhej sake baad mein.
        friendshipRepository.delete(friendship);
        return "Friend request rejected.";
    }

    // ==========================================
    // Fetch list of my All Friends
    // 1. Mere saare Friends ki list nikalna
    // ==========================================
    @Transactional(readOnly = true)
    public List<SocialUserResponse> getMyFriends(String currentUserEmail) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Friendship> friendships = friendshipRepository.findAllAcceptedFriends(currentUser);

        return friendships.stream().map(f -> {
            // Agar main sender hoon, toh friend receiver hai. Agar main receiver hoon, toh friend sender hai.
            User friend = f.getSender().equals(currentUser) ? f.getReceiver() : f.getSender();

            return SocialUserResponse.builder()
                    .userId(friend.getUserId())
                    .nickname(friend.getProfile().getNickname())
                    .avatarUrl(friend.getProfile().getAvatarUrl())
                    .level(friend.getProfile().getLevel())
                    .status("FRIENDS")
                    .email(friend.getEmail())
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================================
    // 2. Unfriend karna (Dosti khatam)
    // ==================================
    @Transactional
    public String unfriend(String friendUserId, String currentUserEmail) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User friend = userRepository.findByUserId(friendUserId)
                .orElseThrow(() -> new RuntimeException("Friend not found"));

        // Dono taraf se check karna padega (A to B ya B to A)
        Optional<Friendship> direction1 = friendshipRepository.findBySenderAndReceiver(currentUser, friend);
        Optional<Friendship> direction2 = friendshipRepository.findBySenderAndReceiver(friend, currentUser);

        boolean found = false;

        // Delete both directions (acceptFriendRequest creates 2 rows)
        if (direction1.isPresent() && direction1.get().getStatus() == FriendshipStatus.ACCEPTED) {
            friendshipRepository.delete(direction1.get());
            found = true;
        }
        if (direction2.isPresent() && direction2.get().getStatus() == FriendshipStatus.ACCEPTED) {
            friendshipRepository.delete(direction2.get());
            found = true;
        }

        if (found) {
            return "Unfriended successfully.";
        }

        return "Error: You are not friends with this user.";
    }

}