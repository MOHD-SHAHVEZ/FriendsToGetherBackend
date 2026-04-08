package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.ChatMessageResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    // --- [REST API]: Fetch Offline/History messages jab user chat open karta hai ---
    @GetMapping("/history/{otherUserId}")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @PathVariable String otherUserId,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        // Token mein email hoti hai (Principal name = email)
        String myEmail = principal.getName();
        
        // Database se apna user ID nikalna
        User myUser = userRepository.findByEmail(myEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + myEmail));

        // Service ko bulao jo pure pichle messages chronologically degi
        List<ChatMessageResponse> history = chatService.getChatHistory(myUser.getUserId(), otherUserId, limit);

        return ResponseEntity.ok(history);
    }
}
