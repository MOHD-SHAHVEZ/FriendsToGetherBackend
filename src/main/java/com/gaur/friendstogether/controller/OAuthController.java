package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.OAuthExchangeRequest;
import com.gaur.friendstogether.dto.response.AuthResponse;
import com.gaur.friendstogether.dto.response.AuthTokenPair;
import com.gaur.friendstogether.service.OAuthAuthenticationService;
import com.gaur.friendstogether.util.CookieUtil;
import com.gaur.friendstogether.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthAuthenticationService oAuthAuthenticationService;
    private final CookieUtil cookieUtil;
    private final JwtUtil jwtUtil;

    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCode(@RequestBody OAuthExchangeRequest request,
                                          HttpServletRequest httpRequest) {
        try {
            // Extract device info and IP for refresh token tracking
            String deviceInfo = httpRequest.getHeader("User-Agent");
            String ipAddress = httpRequest.getRemoteAddr();

            // 1. Exchange code for tokens (AuthTokenPair carries both)
            AuthTokenPair tokenPair = oAuthAuthenticationService.exchangeCodeForTokens(
                    request.getCode(), deviceInfo, ipAddress);

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
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
