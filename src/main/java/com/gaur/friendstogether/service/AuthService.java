package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.LoginRequest;
import com.gaur.friendstogether.dto.request.RegisterRequest;
import com.gaur.friendstogether.dto.request.VerifyOtpRequest;
import com.gaur.friendstogether.dto.response.AuthResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.entity.UserProfile;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.util.JwtUtil;
import com.gaur.friendstogether.util.UniqueIdGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final UniqueIdGenerator idGenerator;

    @Transactional
    public String register(RegisterRequest request) {
        logger.info("Register attempt for Email: {}", request.getEmail());

        // LOGIC: Pehle check karo ki user pehle se exist karta hai ya nahi
        Optional<User> existingUser = userRepository.findByEmail(request.getEmail());

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            // Case 1: Agar user verified hai, toh duplicate error do
            if (user.isEnabled()) {
                return "Error: This email is already registered and verified. Please login.";
            }

            // Case 2: Agar user verified NAHI hai (OTP expire ho gaya hoga), toh naya OTP bhej do
            logger.info("User exists but not verified. Updating and resending OTP...");
            return refreshAndResendOtp(user, request);
        }

        // Case 3: Agar email naya hai lekin username kisi aur ne le rakha hai
        if (userRepository.existsByUsername(request.getUsername())) {
            return "Error: This username is already taken.";
        }

        // Case 4: Bilkul naya registration
        return createNewRegistration(request);
    }

    // Helper: Purane unverified user ko naya OTP bhejne ke liye
    private String refreshAndResendOtp(User user, RegisterRequest request) {
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));

        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10)); // Naya 10 mins start

        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), otp);
        return "Previous registration was incomplete. A new OTP has been sent to your email!";
    }

    // Helper: Naya user create karne ke liye
    private String createNewRegistration(RegisterRequest request) {
        String otp = String.format("%06d", new SecureRandom().nextInt(999999));
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .otp(otp)
                .otpExpiry(LocalDateTime.now().plusMinutes(10))
                .isEnabled(false)
                .build();

        UserProfile profile = UserProfile.builder()
                .nickname(request.getUsername())
                .level(1)
                .user(user)
                .build();
        user.setProfile(profile);

        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), otp);
        return "Registration successful! OTP sent to your email.";
    }

    @Transactional
    public String verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: User not found"));

        if (user.isEnabled()) return "Account is already verified.";

        // OTP Match Logic
        if (user.getOtp() == null || !user.getOtp().equals(request.getOtp())) {
            return "Error: Invalid OTP code.";
        }

        // EXPIRY CHECK LOGIC: Ye check karta hai ki 10 min ho gaye ya nahi
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            return "Error: OTP has expired. Please register again to get a new code.";
        }

        // Update user status and assign 9-digit Unique ID
        user.setEnabled(true);
        user.setOtp(null);
        user.setOtpExpiry(null);

        // Assigning Unique User ID only after successful verification
        if (user.getUserId() == null) {
            user.setUserId(idGenerator.generateUniqueUserId());
            logger.info("Email verified! 9-digit userId generated for {}: {}", user.getEmail(), user.getUserId());
        }

        userRepository.save(user);

        return "Account verified successfully! Your unique ID is: " + user.getUserId();
    }

    // AuthService.java ka login method replace karein:
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: User not found"));

        if (!user.isEnabled()) {
            throw new RuntimeException("Error: Please verify your email first.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Error: Invalid credentials.");
        }

        // Generate both tokens
        String accessToken = jwtUtil.generateToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .username(user.getUsername())
                .build();
    }
}