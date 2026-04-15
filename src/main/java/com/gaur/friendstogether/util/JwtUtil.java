package com.gaur.friendstogether.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    // Fixed static key so tokens don't become invalid on server restart
    private final String secretString = "FriendsTogetherSuperSecretKey1234567890FriendsTogetherSuperSecretKey";
    private final SecretKey key = Keys.hmacShaKeyFor(secretString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final int expirationMs = 86400000; // 24 Hours

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email) // .setSubject ki jagah sirf .subject()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser() // parserBuilder() ki jagah sirf parser()
                .verifyWith(key) // setSigningKey ki jagah verifyWith
                .build()
                .parseSignedClaims(token) // parseClaimsJws ki jagah parseSignedClaims
                .getPayload() // getBody ki jagah getPayload
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

//    Refresh JWT Token function that function run after token expire.
public String generateRefreshToken(String email) {
    return Jwts.builder()
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 604800000)) // 7 Days validity
            .signWith(key)
            .compact();
}
}