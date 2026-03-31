package com.gaur.friendstogether.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @PostMapping("/create")
    public ResponseEntity<String> createRoom(@RequestParam String roomName) {
        return ResponseEntity.ok("Room '" + roomName + "' created successfully");
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<String> joinRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok("Joined Room ID: " + roomId);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<String> getRoomInfo(@PathVariable Long roomId) {
        return ResponseEntity.ok("Details for Room: " + roomId);
    }
}