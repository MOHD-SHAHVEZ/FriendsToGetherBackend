package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "room_blacklist",
        indexes = {
                @Index(name = "idx_blacklist_room", columnList = "room_id"),
                @Index(name = "idx_blacklist_user", columnList = "blocked_user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Room from which the user is banned
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom room;

    // The user who is banned
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;

    // The Admin/Owner who executed the ban
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by", nullable = false)
    private User blockedBy;

    private String reason;

    private LocalDateTime blockedAt;

    @PrePersist
    protected void onCreate() {
        blockedAt = LocalDateTime.now();
    }
}