package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * RoomMediaQueue: Handles real-time music & video sync.
 * logic: FIFO (First-In-First-Out)
 */
@Entity
@Table(
        name = "room_media_queue",
        indexes = {
                @Index(name = "idx_room_media", columnList = "room_id"), // Room ke liye fast search
                @Index(name = "idx_media_order", columnList = "room_id, queueOrder") // Next song dhoondne ke liye
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"room_id", "queueOrder"}) // Ek position par do gaane nahi ho sakte
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomMediaQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -------------------------------------------------------------------------
    // RELATIONS (Kiske liye aur Kisne?)
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom room; // Wo Voice Room jisme ye media bajega

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by", nullable = false)
    private User addedBy; // Wo User jisne ye gaana queue mein dala

    // -------------------------------------------------------------------------
    // MEDIA DETAILS (Kya baj raha hai?)
    // -------------------------------------------------------------------------

    @Column(nullable = false)
    private String mediaUrl; // YouTube ID ya Audio link (Primary Source)

    private String mediaTitle; // UI par dikhane ke liye Title

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType; // YOUTUBE or AUDIO

    private long duration; // Gaane ki total length (Seconds mein)

    @Builder.Default
    private int playCount = 0; // Analytics: Ye gaana kitni baar baj chuka hai

    // -------------------------------------------------------------------------
    // QUEUE & SYNC CONTROL (Kaise baj raha hai?)
    // -------------------------------------------------------------------------

    @Column(nullable = false)
    private int queueOrder; // Line number (1, 2, 3...)

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MediaStatus status = MediaStatus.PENDING; // Current State

    @Builder.Default
    private long currentSeekTime = 0; // Backup Seek Position (Live sync Redis se hoga)

    @Builder.Default
    private boolean isActive = true; // Soft Delete: Queue cleanup ke liye

    // -------------------------------------------------------------------------
    // AUDIT (Kab bana?)
    // -------------------------------------------------------------------------

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.mediaType == null) this.mediaType = MediaType.YOUTUBE;
    }
}