package com.gaur.friendstogether.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @GetMapping("/history/{roomId}")
    public ResponseEntity<String> getChatHistory(@PathVariable Long roomId) {
        return ResponseEntity.ok("Returning 50 latest messages for Room: " + roomId);
    }
}