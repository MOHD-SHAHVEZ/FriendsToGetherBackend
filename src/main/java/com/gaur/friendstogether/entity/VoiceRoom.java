package com.gaur.friendstogether.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        @Index(name = "idx_room_id", columnList = "roomId"),
        @Index(name = "idx_owner_user_id", columnList = "ownerUserId")
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
     * DISCOVERY LOGIC: Room mein abhi kitne log baithay hain (Sorting ke liye).
     */
    @Builder.Default
    @Column(name = "current_member_count", nullable = false)
    private int currentMemberCount = 0;

    /**
     * CATEGORY LOGIC: Enum use kar rahe hain taaki data consistent rahe.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "category", nullable = false)
    private RoomCategory category = RoomCategory.GENERAL;

    /**
     * Entry unique code: User ko invite bhejne ke liye "ROOM12345" jaisa code use hoga.
     */
    @Column(unique = true, nullable = false)
    private String roomId;

//     * Room ka display name (e.g., "Mohd Shahvez's Chilling Zone")
    private String title;

    /**
     * Agar room private hai, toh uska encrypted password yahan store hoga.
     * @JsonIgnore: Ye password ko API response mein jane se rokega (Security)
     */
//    @JsonIgnore
    @Column(length = 255)
    private String passwordHash;

    /**
     * TRUE ka matlab hai room locked hai aur entry ke liye password chahiye.
     * @JsonProperty: Taaki JSON response mein "locked" ki jagah "isLocked" hi dikhe.
     */
    @JsonProperty("isLocked")
    @Builder.Default
    private boolean isLocked = false;

    /**
     * CORE LOGIC:
     * TRUE = "Apna Ghar" (Permanent room, jisme EXP aur Level up hoga).
     * FALSE = "Rent ka Ghar" (Temporary room, kaam khatam toh room khatam).
     */
    @JsonProperty("isPersonal")
    @Builder.Default
    private boolean isPersonal = false;

//     * Room mein ek saath maximum kitne log baith sakte hain (Default 10).
//    @Builder.Default
//    private int maxUsers = 10;
    @Builder.Default
    private int maxSeat = 10;

//     * Personal Room ka current level (Gifting aur time spent se badhta hai).
    @Builder.Default
    private int roomLevel = 1;

//     * Room ki total kamayi (EXP). Gifting hone par ye points badhte hain.
    @Builder.Default
    private long roomExp = 0;

//     * Room ki current halat: ACTIVE (chalu hai) ya CLOSED (band ho chuka hai).
    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    /**
     * @JsonIgnoreProperties: Taaki Owner ki poori detail na dikhe (sirf basic info jaye)
     * Optional: @JsonIgnore use kar sakte ho agar response light rakhna hai.
     */

//     * Room ka asli malik (Creator). Saari administrative powers iske paas hongi.
//    @ManyToOne(fetch = FetchType.LAZY)
    @ManyToOne(fetch = FetchType.EAGER) // Testing ke liye EAGER sahi hai taaki owner dikhe
    @JoinColumn(name = "owner_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"}) // Password aur extra hibernate data ignore karo
    private User owner;

    // Store user Game_id jo ham generate karra (Searching user_id se bhi kar paoge)
    @Column(name = "owner_user_id")
    private String ownerUserId;

//     * Audit fields: Room kab bana aur kab update hua.
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

//     * Room bante waqt automatically timestamps aur default status set karne ke liye.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = RoomStatus.ACTIVE;
    }

//     * Jab bhi room ki koi setting (Title, Password) change ho, updatedAt update ho jayega.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}