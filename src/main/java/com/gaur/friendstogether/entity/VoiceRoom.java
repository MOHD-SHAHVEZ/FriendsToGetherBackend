package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * VoiceRoom Entity: Ye table "Normal Rooms" aur "Personal Rooms" dono ka data store karegi.
 * Personal rooms permanent hote hain jinme Levels, EXP aur Tiers ka logic chalta hai.
 */
@Entity
@Table(name = "voice_rooms", indexes = {
        // Indexing se room search (by roomId) bahut fast ho jati hai
        @Index(name = "idx_room_id", columnList = "roomId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entry ke liye unique code.
     * Example: User ko invite bhejne ke liye "ROOM12345" jaisa code use hoga.
     */
    @Column(unique = true, nullable = false)
    private String roomId;

    /**
     * Room ka display name (e.g., "Mohd Shahvez's Chilling Zone")
     */
    private String title;

    /**
     * Agar room private hai, toh uska encrypted password yahan store hoga.
     */
    private String passwordHash;

    /**
     * TRUE ka matlab hai room locked hai aur entry ke liye password chahiye.
     */
    @Builder.Default
    private boolean isLocked = false;

    /**
     * CORE LOGIC:
     * TRUE = "Apna Ghar" (Permanent room, jisme EXP aur Level up hoga).
     * FALSE = "Rent ka Ghar" (Temporary room, kaam khatam toh room khatam).
     */
    @Builder.Default
    private boolean isPersonal = false;

    /**
     * Room mein ek saath maximum kitne log baith sakte hain (Default 10).
     */
    @Builder.Default
    private int maxUsers = 10;

    /**
     * Personal Room ka current level (Gifting aur time spent se badhta hai).
     */
    @Builder.Default
    private int roomLevel = 1;

    /**
     * Room ki total kamayi (EXP). Gifting hone par ye points badhte hain.
     */
    @Builder.Default
    private long roomExp = 0;

    /**
     * Room ki current halat: ACTIVE (chalu hai) ya CLOSED (band ho chuka hai).
     */
    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    /**
     * Room ka asli malik (Creator). Saari administrative powers iske paas hongi.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /**
     * Audit fields: Room kab bana aur kab update hua.
     */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Room bante waqt automatically timestamps aur default status set karne ke liye.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = RoomStatus.ACTIVE;
    }

    /**
     * Jab bhi room ki koi setting (Title, Password) change ho, updatedAt update ho jayega.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}