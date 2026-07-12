package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * =============================================================================
 * UserWallet
 * =============================================================================
 *
 * UserWallet table user ka current wallet state maintain karti hai.
 *
 * IMPORTANT
 * ---------------------------------------------------------------------------
 * WalletTransaction table sirf history (ledger) maintain karti hai.
 *
 * Current balance kabhi bhi WalletTransaction ka SUM() karke nahi nikala jayega,
 * kyunki Voice Room me ek second me hundreds of gift transactions ho sakte hain.
 *
 * Isliye current balance hamesha isi table se read kiya jayega.
 *
 * Flow
 * ---------------------------------------------------------------------------
 *
 * Payment Success
 *        │
 *        ▼
 * UserWallet.currentBalance += Coins
 *        │
 *        ▼
 * WalletTransaction Entry
 *
 * --------------------------------------------------------------
 *
 * Gift Send
 *        │
 *        ▼
 * Check UserWallet.currentBalance
 *        │
 *        ▼
 * Deduct Balance
 *        │
 *        ▼
 * WalletTransaction Entry
 *
 * =============================================================================
 */

@Entity
@Table(
        name = "user_wallets",
        indexes = {

                @Index(
                        name = "idx_wallet_user",
                        columnList = "user_id",
                        unique = true
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWallet {

    /**
     * Internal Database ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Wallet Owner
     *
     * Har user ka sirf ek wallet hoga.
     */
    @com.fasterxml.jackson.annotation.JsonBackReference("user-wallet")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Current Available Coins
     *
     * Gift bhejne se pehle isi field ko check kiya jayega.
     *
     * Example:
     * 5200 Coins
     */
    @Builder.Default
    @Column(nullable = false)
    private Long currentBalance = 0L;

    /**
     * Lifetime Purchased Coins
     *
     * Sirf purchase kiye gaye coins.
     *
     * Useful For:
     * - VIP Level
     * - Analytics
     * - Badges
     * - User Achievements
     */
    @Builder.Default
    @Column(nullable = false)
    private Long lifetimeRechargedCoins = 0L;

    /**
     * Wallet Current Status
     *
     * ACTIVE
     * FROZEN
     * BLOCKED
     * CLOSED
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private WalletStatus status = WalletStatus.ACTIVE;

    /**
     * Optimistic Locking
     *
     * Prevents data corruption when multiple transactions
     * update the same wallet simultaneously.
     *
     * Example:
     * Two Gifts Sent at Same Time
     */
    @Version
    private Long version;

    /**
     * Wallet Creation Time
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Wallet Last Updated Time
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Last Successful Wallet Transaction Time
     *
     * Helpful for:
     * - Analytics
     * - Fraud Detection
     * - User Activity
     */
    private LocalDateTime lastTransactionAt;

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }

}
