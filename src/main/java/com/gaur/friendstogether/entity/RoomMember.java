package com.gaur.friendstogether.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    @JsonIgnore // Ye MUST hai recursive loop rokne ke liye
    private VoiceRoom room;

    // Linked to the User who is inside the room
    @ManyToOne(fetch = FetchType.EAGER) // User details chahiye response mein jo room ma join kiya session close se phela.
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"}) // room ke ander users ka password ignore karo.
    private User user;

    @Enumerated(EnumType.STRING)
    private RoomRole role;

    @Column(name = "user_id_string") // jo Id hamne generate or allocate kari hai.
    private String userIdString;

    @Column(name = "room_id_string")
    private String roomIdString;

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