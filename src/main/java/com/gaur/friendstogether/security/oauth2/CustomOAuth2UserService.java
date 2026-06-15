package com.gaur.friendstogether.security.oauth2;

import com.gaur.friendstogether.entity.AuthProvider;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.UserCreationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserRepository userRepository;
    private final UserCreationService userCreationService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        logger.info("[OAUTH] ===== OAuth2 authentication started =====");
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception ex) {
            // CRITICAL: Log the FULL exception — do NOT swallow it
            logger.error("[OAUTH] ===== EXCEPTION during OAuth2 user processing =====", ex);
            logger.error("[OAUTH] Exception class: {}", ex.getClass().getName());
            logger.error("[OAUTH] Exception message: {}", ex.getMessage());
            // Spring Security requires OAuth2AuthenticationException to trigger FailureHandler properly
            throw new OAuth2AuthenticationException(new OAuth2Error("oauth2_error"), ex.getMessage(), ex);
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        logger.info("[OAUTH] Provider registrationId: {}", registrationId);

        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oAuth2User.getAttributes());

        String email = oAuth2UserInfo.getEmail();
        logger.info("[OAUTH] Google email received: {}", email);
        logger.info("[OAUTH] Google name received: {}", oAuth2UserInfo.getName());
        logger.info("[OAUTH] Google providerId (sub): {}", oAuth2UserInfo.getId());

        if (email == null || email.isEmpty()) {
            logger.error("[OAUTH] Email is null or empty from OAuth2 provider!");
            throw new RuntimeException("Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        logger.info("[OAUTH] findByEmail result — user exists: {}", userOptional.isPresent());

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            logger.info("[OAUTH] Existing user found — id: {}, authProvider: {}, providerId: {}", 
                    user.getId(), user.getAuthProvider(), user.getProviderId());

            // Existing LOCAL User with same email -> Reject login.
            if (user.getAuthProvider() == AuthProvider.LOCAL) {
                logger.warn("[OAUTH] REJECTED: Email {} belongs to a LOCAL account", email);
                throw new RuntimeException("This email already belongs to a local account. Login using email/password and link your " + registrationId + " account from account settings.");
            }
            // Existing OAuth User with Provider mismatch
            if (!user.getAuthProvider().name().equalsIgnoreCase(registrationId)) {
                logger.warn("[OAUTH] REJECTED: Provider mismatch — user has {}, request is {}", user.getAuthProvider(), registrationId);
                throw new RuntimeException("Looks like you're signed up with " + user.getAuthProvider() + " account. Please use your " + user.getAuthProvider() + " account to login.");
            }
            // Existing OAuth User with Provider ID mismatch
            if (user.getProviderId() != null && !user.getProviderId().equals(oAuth2UserInfo.getId())) {
                logger.warn("[OAUTH] REJECTED: Provider ID mismatch — stored: {}, received: {}", user.getProviderId(), oAuth2UserInfo.getId());
                throw new RuntimeException("Provider ID mismatch. Suspicious activity detected.");
            }

            logger.info("[OAUTH] Existing OAuth user validated successfully for email: {}", email);
        } else {
            // New OAuth User
            logger.info("[OAUTH] ===== NEW OAUTH USER — entering creation branch =====");
            logger.info("[OAUTH] Resolving AuthProvider for registrationId: {}", registrationId);
            AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
            logger.info("[OAUTH] AuthProvider resolved: {}", provider);

            logger.info("[OAUTH] Calling UserCreationService.createOAuthUser()...");
            User createdUser = userCreationService.createOAuthUser(oAuth2UserInfo, provider);
            logger.info("[OAUTH] UserCreationService.createOAuthUser() returned successfully");
            logger.info("[OAUTH] Created user — dbId: {}, email: {}, userId: {}, authProvider: {}", 
                    createdUser.getId(), createdUser.getEmail(), createdUser.getUserId(), createdUser.getAuthProvider());

            // POST-SAVE VERIFICATION: Confirm the user actually exists in the database
            boolean existsAfterSave = userRepository.existsByEmail(email);
            logger.info("[OAUTH] POST-SAVE VERIFICATION — existsByEmail({}): {}", email, existsAfterSave);
            if (!existsAfterSave) {
                logger.error("[OAUTH] CRITICAL: User was NOT persisted to database after saveAndFlush!");
                throw new RuntimeException("OAuth user creation failed — user not found in database after save for email: " + email);
            }
        }

        logger.info("[OAUTH] ===== processOAuth2User completed successfully for: {} =====", email);
        return oAuth2User; 
    }
}
