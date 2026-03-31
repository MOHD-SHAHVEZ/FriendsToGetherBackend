package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.LoginRequest;
import com.gaur.friendstogether.dto.request.RegisterRequest;
import com.gaur.friendstogether.dto.request.VerifyOtpRequest;
import com.gaur.friendstogether.dto.response.AuthResponse;
import com.gaur.friendstogether.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor

public class AuthController {

    // AuthController.java mein ye change karein
    private final AuthService authService;

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
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 1. Asli logic call karo jo token generate karega
            AuthResponse response = authService.login(request);
            // 2. Agar success hai, toh asli JWT Token return hoga
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}