package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.UpdateProfileRequest;
import com.gaur.friendstogether.dto.response.UserProfileResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    // YE LINE MISSING THI - FileService ko yahan declare karna zaroori hai
    private final FileService fileService;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String email) {
        logger.info("Fetching profile for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.getProfile() == null) {
            logger.error("Profile missing for verified user: {}", email);
            throw new RuntimeException("User profile data is corrupted or missing.");
        }

        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getProfile().getNickname())
                .level(user.getProfile().getLevel())
                .avatarUrl(user.getProfile().getAvatarUrl())
                .titleBadge(user.getProfile().getTitleBadge())
                .dob(user.getProfile().getDob())
                .gender(user.getProfile().getGender())
                .region(user.getProfile().getRegion())
                .signature(user.getProfile().getSignature())
                .build();
    }

    @Transactional
    public String updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var profile = user.getProfile();
        if (profile == null) throw new RuntimeException("Profile missing!");

        // Ek-ek karke check karo aur update karo
        if (request.getNickname() != null) profile.setNickname(request.getNickname());
        if (request.getAvatarUrl() != null) profile.setAvatarUrl(request.getAvatarUrl());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getDob() != null) profile.setDob(request.getDob());
        if (request.getRegion() != null) profile.setRegion(request.getRegion());
        if (request.getSignature() != null) profile.setSignature(request.getSignature());
        if(request.getTitleBadge()!=null) profile.setTitleBadge(request.getTitleBadge());

        userRepository.save(user);
        return "Profile updated successfully!";
    }

    @Transactional
    public String updateAvatar(String email, String imageUrl) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getProfile() == null) {
            throw new RuntimeException("User profile not initialized!");
        }

        String oldImageUrl = user.getProfile().getAvatarUrl();

        // Ab 'fileService' yahan sahi se kaam karega kyunki humne upar inject kar diya hai
        if (oldImageUrl != null && !oldImageUrl.isEmpty() && oldImageUrl.contains("amazonaws.com")) {
            logger.info("Deleting old avatar from S3...");
            fileService.deleteFile(oldImageUrl);
        }

        user.getProfile().setAvatarUrl(imageUrl);
        userRepository.save(user);

        return "Profile picture updated in database!";
    }

    // remove all profile image from s3 bucket if user remove profile
    @Transactional
    public String removeCurrentAvatar(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getProfile() == null) {
            throw new RuntimeException("Profile not found!");
        }

        String currentImageUrl = user.getProfile().getAvatarUrl();

        // 1. Agar DB mein URL hai, toh hi delete process shuru karo
        if (currentImageUrl != null && !currentImageUrl.isEmpty()) {

            // 2. S3 se file delete karo (Security check ke saath)
            if (currentImageUrl.contains("amazonaws.com")) {
                logger.info("Removing avatar from S3 for user: {}", email);
                fileService.deleteFile(currentImageUrl);
            }

            // 3. Database mein URL ko null set karo
            user.getProfile().setAvatarUrl(null);
            userRepository.save(user);

            return "Profile picture removed successfully!";
        }

        return "No profile picture found to remove.";
    }

}