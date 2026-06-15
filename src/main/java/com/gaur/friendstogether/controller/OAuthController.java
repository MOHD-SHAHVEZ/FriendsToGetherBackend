package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.OAuthExchangeRequest;
import com.gaur.friendstogether.dto.response.AuthResponse;
import com.gaur.friendstogether.service.OAuthAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthAuthenticationService oAuthAuthenticationService;

    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCode(@RequestBody OAuthExchangeRequest request) {
        try {
            AuthResponse response = oAuthAuthenticationService.exchangeCodeForTokens(request.getCode());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
