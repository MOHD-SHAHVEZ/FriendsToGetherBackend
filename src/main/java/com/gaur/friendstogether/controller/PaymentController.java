package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.PaymentInitiateRequest;
import com.gaur.friendstogether.dto.request.PaymentVerifyRequest;
import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.dto.response.PaymentInitiateResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    /**
     * INITIATE PAYMENT
     * 
     * Called when user clicks "Pay Now" on a created order.
     */
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiatePayment(
            Authentication authentication,
            @Valid @RequestBody PaymentInitiateRequest request) {

        Long userId = extractUserId(authentication);
        PaymentInitiateResponse response = paymentService.initiatePayment(userId, request);

        ApiResponse<PaymentInitiateResponse> apiResponse = ApiResponse.<PaymentInitiateResponse>builder()
                .success(true)
                .message("Payment initiated successfully")
                .data(response)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    /**
     * VERIFY PAYMENT
     * 
     * Called by frontend after Razorpay checkout returns success.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPayment(
            Authentication authentication,
            @Valid @RequestBody PaymentVerifyRequest request) {

        Long userId = extractUserId(authentication);
        paymentService.verifyPayment(userId, request);

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(true)
                .message("Payment verified and coins credited successfully")
                .data(null)
                .build();

        return ResponseEntity.ok(apiResponse);
    }
    
    /**
     * RAZORPAY WEBHOOK
     * 
     * Handles asynchronous server-to-server webhook callbacks.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestBody String payload) {

        // Note: Full webhook implementation (parsing events, logging to PaymentWebhookLog)
        // is typically handled in PaymentService. We just acknowledge Razorpay quickly here.
        paymentService.handleWebhook(payload, signature);

        return ResponseEntity.ok("OK");
    }

    /**
     * Extract user database ID from JWT Authentication.
     */
    private Long extractUserId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("User not authenticated");
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + email));

        return user.getId();
    }
}
