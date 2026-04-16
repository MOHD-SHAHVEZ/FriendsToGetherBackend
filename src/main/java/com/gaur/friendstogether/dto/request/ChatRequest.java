package com.gaur.friendstogether.dto.request;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatRequest {
    private String receiverId;
    private String content;
    private String type; // TEXT, IMAGE, etc.
}