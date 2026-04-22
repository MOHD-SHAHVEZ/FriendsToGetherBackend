package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "room_blacklists",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_room_user_blacklist", columnNames = {"room_id", "blocked_user_id"})
        },
        indexes = {
                // composite index for fast searching.
                @Index(name = "idx_blacklist_room_user", columnList = "room_id, blocked_user_id"),
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

    // Jis room se ban kiya gaya hai
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom room;

    // Jis user ko ban kiya gaya hai
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;

    // Jisne ban kiya (Owner/Admin)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_id", nullable = false)
    private User blockedBy;

    private String reason;

    private LocalDateTime blockedAt;

    @PrePersist
    protected void onCreate() {
        this.blockedAt = LocalDateTime.now();
    }
}