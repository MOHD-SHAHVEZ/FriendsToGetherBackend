package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id"}) // Unique Room ID for 1-on-1
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder // // @Builder is used to create objects in a clean, readable, and flexible way without using complex constructors
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private String roomId; // Format: "minUserId_maxUserId"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id")
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id")
    private User user2;

    private String lastMessage;
    private LocalDateTime lastMessageTime;

    // --- Future Ready Fields ---
    private boolean isGroupChat = false; // Later for Group features
    private String groupName;
    private String groupAvatar;
}