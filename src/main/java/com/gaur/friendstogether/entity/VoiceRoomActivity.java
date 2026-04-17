package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 📊 VoiceRoomActivity:
 * Room ki saari history aur events ka record.
 */
@Entity
@Table(name = "voice_room_activities", indexes = {
        @Index(name = "idx_v_room_activity", columnList = "room_id"),
        @Index(name = "idx_activity_time", columnList = "createdAt") // Latest activity fast fetch karne ke liye
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VoiceRoomActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kis Voice Room ka event hai
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom voiceRoom;

    // Event karne wala User (e.g., Jisne gift bheja)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoiceRoomActivityType activityType;

    // Simple description text
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * JSON metadata: Extra info store karne ke liye (Gift ID, Coins count, etc.)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}