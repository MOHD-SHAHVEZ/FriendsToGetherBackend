package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * VoiceRoomMessage:
 * Voice Room ki dedicated group chat.
 * Isme Reply, Edit aur Soft Delete features included hain.
 */

@Entity
@Table(
        name = "voice_room_messages",
        indexes = {
                @Index(name = "idx_v_room_msg_room", columnList = "room_id"),   // Room wise chat loading fast karne ke liye
                @Index(name = "idx_v_room_msg_time", columnList = "createdAt"), // Sorting (Latest first) ke liye
                @Index(name = "idx_v_room_msg_sender", columnList = "sender_id") // Analytics: Kaun kitna active hai
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VoiceRoomMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -------------------------------------------------------------------------
    // RELATIONS (Kahan, Kisne aur Kise Reply kiya?)
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom voiceRoom; // Kis Voice Room ka message hai

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender; // Message bhejne wala (System messages ke liye 'System' user use karein)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to")
    private VoiceRoomMessage replyTo; // Threading: Kis message ka reply diya gaya hai

    // -------------------------------------------------------------------------
    // CONTENT (Kya baat hui?)
    // -------------------------------------------------------------------------

    @Column(columnDefinition = "TEXT")
    private String content; // Message text content

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoiceRoomMessageType type; // TEXT, IMAGE, SYSTEM, etc.

    private String mediaUrl; // Images ya files ka link

    // -------------------------------------------------------------------------
    // STATUS & CONTROLS (Edit, Delete aur State)
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VoiceRoomMessageStatus status = VoiceRoomMessageStatus.SENT;

    @Builder.Default
    private boolean isEdited = false; // Kya message edit hua hai?
    private LocalDateTime editedAt;

    @Builder.Default
    private boolean isDeletedForAll = false; // Admin ya user ne sabke liye delete kiya?
    private LocalDateTime deletedAt;

    // -------------------------------------------------------------------------
    // TIMESTAMPS
    // -------------------------------------------------------------------------

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}