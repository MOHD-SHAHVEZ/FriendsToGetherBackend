package com.gaur.friendstogether.controller;
// userContrller he Profile-Controller bhi hai so be focus.
import com.gaur.friendstogether.dto.request.UpdateProfileRequest;
import com.gaur.friendstogether.dto.response.UserProfileResponse;
import com.gaur.friendstogether.service.FileService;
import com.gaur.friendstogether.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    private final FileService fileService; // Missing service inject ki gayi hai

    /**
     * GET: Logged-in user ki profile fetch karna (Using JWT Token)
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            UserProfileResponse profile = userService.getMyProfile(email);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            logger.error("Error fetching profile: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * PUT: Profile update karna (Nickname, Avatar)
     */
    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            String response = userService.updateProfile(email, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating profile: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * POST: Profile Image S3 par upload karna aur URL return karna
     * Ye method missing tha!
     */
    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();

            // 1. S3 par upload karke URL lo
            String imageUrl = fileService.uploadProfileImage(file);

            // 2. IS URL KO DB MEIN SAVE KARO (Ye missing tha)
            userService.updateAvatar(email, imageUrl);

            // 3. Response mein URL aur success message bhejo
            return ResponseEntity.ok("Success! Image saved to DB. URL: " + imageUrl);

        } catch (Exception e) {
            logger.error("Upload/DB Save failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * DELETE: User apni profile pic remove kar sake
     */
    @DeleteMapping("/remove-avatar")
    public ResponseEntity<?> removeAvatar() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();

            String response = userService.removeCurrentAvatar(email);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error removing avatar: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

}