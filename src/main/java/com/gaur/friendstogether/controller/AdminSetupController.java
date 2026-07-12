package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.UserRole;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AdminSetupController
 *
 * Ye controller sirf admin banane ke liye hai.
 * Secret key se protected hai — sirf wahi banda admin ban sakta hai
 * jiske paas secret key ho.
 *
 * POST /api/auth/promote-admin
 *
 * Body:
 * {
 *     "email": "user@example.com",
 *     "secretKey": "your-secret-key"
 * }
 *
 * Secret key application.properties me define hoti hai:
 * app.admin.secret-key=your-secret-key
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AdminSetupController {

    private static final Logger logger = LoggerFactory.getLogger(AdminSetupController.class);

    private final UserRepository userRepository;

    @Value("${app.admin.secret-key:FriendsToGether@Admin#2026}")
    private String adminSecretKey;

    /**
     * POST /api/auth/promote-admin
     *
     * Existing user ko ADMIN role de do.
     * Secret key required hai — bina secret key ke koi admin nahi ban sakta.
     */
    @PostMapping("/promote-admin")
    public ResponseEntity<ApiResponse<Void>> promoteToAdmin(@RequestBody Map<String, String> request) {

        String email = request.get("email");
        String secretKey = request.get("secretKey");

        // Secret key validation
        if (secretKey == null || !secretKey.equals(adminSecretKey)) {
            logger.warn("Invalid admin secret key attempt for email: {}", email);
            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .success(false)
                    .message("Invalid secret key. Access denied.")
                    .build();
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        // Find user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        // Already admin check
        if (user.getRole() == UserRole.ADMIN) {
            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .success(true)
                    .message("User is already an ADMIN.")
                    .build();
            return ResponseEntity.ok(response);
        }

        // Promote to admin
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);

        logger.info("User promoted to ADMIN: email={}", email);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("User promoted to ADMIN successfully. Please login again to get new token.")
                .build();

        return ResponseEntity.ok(response);
    }
}
