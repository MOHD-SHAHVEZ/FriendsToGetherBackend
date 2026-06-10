package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * WalletTransaction: User ke coins ka poora audit trail.
 * Har ek coin ka aana-jaana yahan record hoga.
 */
@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_user", columnList = "user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kis user ke wallet ka transaction hai
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Transaction amount (coins ki sankhya)
    @Column(nullable = false)
    private long amount;

    // CREDIT (Paisa aaya) ya DEBIT (Paisa gaya)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    // Transaction ki wajah (e.g., Daily Bonus, Gift Sent)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionReason reason;

    /**
     * Important: Ye transaction kis specific event (jaise Gift) se juda hai uski ID.
     */
    private Long referenceId;

    // Transaction ka current status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    /**
     * Audit Field: Transaction hone ke baad user ka total balance kitna bacha.
     * Isse coins ka lafda (fraud) pakadne mein asani hoti hai.
     */
    @Column(nullable = false)
    private long balanceAfterTransaction;

    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        // Default success agar set nahi kiya gaya
        if (status == null) status = TransactionStatus.SUCCESS;
    }
}