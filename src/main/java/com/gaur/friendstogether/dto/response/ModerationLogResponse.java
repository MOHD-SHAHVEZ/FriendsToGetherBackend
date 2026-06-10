package com.gaur.friendstogether.dto.response;

import com.gaur.friendstogether.entity.ModerationAction;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationLogResponse {

    private Long id;
    private String roomId;

    // Actor Details (Jisne action liya)
    private String actorId;
    private String actorName;

    // Target Details (Jiske upar action hua)
    private String targetId;
    private String targetName;

    // Enum use kar rahe hain taaki frontend ko fix value mile
    private ModerationAction action;

    private String reason;
    private String metadata; // Extra info ke liye (Optional)

    private LocalDateTime createdAt;
}