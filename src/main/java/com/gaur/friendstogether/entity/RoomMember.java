package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "room_members",
        uniqueConstraints = {
                // Ensures a user is not added multiple times to the same room
                @UniqueConstraint(columnNames = {"room_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_member_room", columnList = "room_id"),
                @Index(name = "idx_member_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Linked to the VoiceRoom entity (Proper Foreign Key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom room;

    // Linked to the User who is inside the room
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private RoomRole role;

    @Builder.Default
    private boolean isMuted = false;

    // Tracks if user is currently inside or has left
    @Builder.Default
    private boolean isActive = true;

    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
    }
}