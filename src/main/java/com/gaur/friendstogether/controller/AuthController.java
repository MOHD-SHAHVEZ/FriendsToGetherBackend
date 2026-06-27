package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.LoginRequest;
import com.gaur.friendstogether.dto.request.RegisterRequest;
import com.gaur.friendstogether.dto.request.VerifyOtpRequest;
import com.gaur.friendstogether.dto.response.AuthResponse;
import com.gaur.friendstogether.dto.response.AuthTokenPair;
import com.gaur.friendstogether.service.AuthService;
import com.gaur.friendstogether.util.CookieUtil;
import com.gaur.friendstogether.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor

public class AuthController {

    // AuthController.java mein ye change karein
    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        // @Valid lagane se Spring check karega ki Email mein @ hai ya nahi
        String response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        System.out.println("Verify OTP Request: " + request);
        // 1. Ab ye dummy message nahi dega, balki asli logic call karega
        String response = authService.verifyOtp(request);

        // 2. Agar logic mein "Error" return hua (matlab OTP galat hai)
        if (response.contains("Error")) {
            System.out.println("Error: " + response);
            return ResponseEntity.badRequest().body(response);
        }

        // 3. Agar sab sahi hai
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        try {
            // Extract device info and IP for refresh token tracking
            String deviceInfo = httpRequest.getHeader("User-Agent");
            String ipAddress = httpRequest.getRemoteAddr();

            // 1. AuthService returns both tokens in AuthTokenPair (internal DTO)
            AuthTokenPair tokenPair = authService.login(request, deviceInfo, ipAddress);

            // 2. Set refresh token as HttpOnly cookie
            ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(
                    tokenPair.getRawRefreshToken(),
                    jwtUtil.getRefreshTokenExpirationSeconds()
            );

            // 3. Return ONLY the access token in the response body
            AuthResponse response = AuthResponse.builder()
                    .accessToken(tokenPair.getAccessToken())
                    .tokenType("Bearer")
                    .username(tokenPair.getUsername())
                    .expiresIn(tokenPair.getAccessTokenExpiresIn())
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}