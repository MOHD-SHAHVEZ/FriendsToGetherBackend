package com.gaur.friendstogether.service;

import com.gaur.friendstogether.entity.AuthProvider;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.UserProfile;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.security.oauth2.OAuth2UserInfo;
import com.gaur.friendstogether.util.UniqueIdGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserCreationService {

    private static final Logger logger = LoggerFactory.getLogger(UserCreationService.class);

    private final UserRepository userRepository;
    private final UniqueIdGenerator idGenerator;

    @Transactional
    public User createLocalUser(String username, String email, String encodedPassword, String otp) {
        User user = User.builder()
                .username(username)
                .email(email)
                .password(encodedPassword)
                .otp(otp)
                .otpExpiry(LocalDateTime.now().plusMinutes(10))
                .isEnabled(false)
                .authProvider(AuthProvider.LOCAL)
                .build();

        UserProfile profile = UserProfile.builder()
                .nickname(username)
                .level(1)
                .user(user)
                .build();
        user.setProfile(profile);

        return userRepository.save(user);
    }

    @Transactional
    public User createOAuthUser(OAuth2UserInfo userInfo, AuthProvider provider) {
        logger.info("[OAUTH-CREATE] ===== createOAuthUser() ENTERED =====");
        logger.info("[OAUTH-CREATE] Email: {}", userInfo.getEmail());
        logger.info("[OAUTH-CREATE] Name: {}", userInfo.getName());
        logger.info("[OAUTH-CREATE] Provider: {}", provider);
        logger.info("[OAUTH-CREATE] ProviderId: {}", userInfo.getId());

        // Ensure unique username
        String baseUsername = userInfo.getName() != null ? userInfo.getName().replaceAll("\\s+", "") : "User";
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }
        logger.info("[OAUTH-CREATE] Final username resolved: {}", username);

        String generatedUserId = idGenerator.generateUniqueUserId();
        logger.info("[OAUTH-CREATE] Generated 9-digit userId: {}", generatedUserId);

        User user = User.builder()
                .username(username)
                .email(userInfo.getEmail())
                .password(null)
                .isEnabled(true)
                .authProvider(provider)
                .providerId(userInfo.getId())
                .userId(generatedUserId)
                .build();

        UserProfile profile = UserProfile.builder()
                .nickname(userInfo.getName())
                .level(1)
                .user(user)
                .build();
        user.setProfile(profile);

        logger.info("[OAUTH-CREATE] Calling userRepository.saveAndFlush() — forcing immediate SQL INSERT...");
        User savedUser = userRepository.saveAndFlush(user);
        logger.info("[OAUTH-CREATE] saveAndFlush() COMPLETED — DB id: {}, email: {}, authProvider: {}", 
                savedUser.getId(), savedUser.getEmail(), savedUser.getAuthProvider());
        logger.info("[OAUTH-CREATE] ===== createOAuthUser() EXITING SUCCESSFULLY =====");

        return savedUser;
    }
}
