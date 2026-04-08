package com.gaur.friendstogether.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private String roomId;
    private String content;
    private String timestamp;
    private String type;
    private String status;
    private UserData sender;
    private UserData receiver;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserData {
        private String userId;
        private String username;
        private String email;
    }
}
