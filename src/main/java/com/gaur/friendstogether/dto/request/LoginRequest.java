package com.gaur.friendstogether.dto.request;

import lombok.Data;

@Data // <-- Ye annotation bohot zaroori hai Getters/Setters ke liye
public class LoginRequest {
    private String email;    // <-- Check karein kya ye 'email' hi hai ya 'username'
    private String password;
}