package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * GiftLog: Voice Room mein hone wale har ek gift ka record.
 * Isi se User ki Popularity (Charm) aur Room ka Level badhta hai.
 */
@Entity
@Table(name = "gift_logs", indexes = {
        @Index(name = "idx_gift_room", columnList = "room_id"),
        @Index(name = "idx_gift_sender", columnList = "sender_id"),
        @Index(name = "idx_gift_receiver", columnList = "receiver_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GiftLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kis room mein ye gift diya gaya (Relation to VoiceRoom)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom room;

    // Gift bhejne wala user (Sender)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // Gift receive karne wala user (Receiver)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // Gift ka type (ROSE, HEART, etc.)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GiftType giftType;

    // Gift ki original keemat coins mein (Cannot be null)
    @Column(nullable = false)
    private int coinsSpent;

    // Receiver ko milne wala surprise cashback (20-80 random coins)
    @Column(nullable = false)
    private int rewardCoinsGiven;

    // Receiver ke popularity points (Charm) mein kitna izafa hua
    @Column(nullable = false)
    private int charmAdded;

    // Is gift se Room ki EXP kitni badhi
    @Column(nullable = false)
    private long roomExpAdded;

    /**
     * PRO IMPROVEMENT: Direct link to the WalletTransaction table.
     * Isse hum ek gift ke piche ka poora paiso ka transaction turant dekh sakte hain.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private WalletTransaction transaction;

    /**
     * Logic check: Kya tier/level calculations poori ho chuki hain?
     */
    @Builder.Default
    private boolean isProcessed = true;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}