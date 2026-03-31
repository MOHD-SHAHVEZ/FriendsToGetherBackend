package com.gaur.friendstogether.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lock-auth-demo")
public class TestLockAuthController {
    @GetMapping("/secure-data")
    public String test() {
        return "Congrats! You accessed this data using a valid JWT Token.";
    }
}
