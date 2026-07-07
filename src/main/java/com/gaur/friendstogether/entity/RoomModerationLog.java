package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "room_moderation_logs",
        indexes = {
                @Index(name = "idx_mod_room", columnList = "room_id"),
                @Index(name = "idx_mod_actor", columnList = "actor_id"),
                @Index(name = "idx_mod_target", columnList = "target_id"),
                @Index(name = "idx_mod_time", columnList = "created_at")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // ya Lazy the Earger kiya hai for testing only
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ModerationAction action;

    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON format mein extra info ke liye

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}