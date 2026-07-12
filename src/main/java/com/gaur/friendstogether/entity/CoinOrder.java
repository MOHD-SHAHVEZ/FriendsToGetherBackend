package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * CoinOrder
 * ============================================================================
 *
 * CoinOrder user ke purchase request ka permanent record hai.
 *
 * Flow:
 *
 * User
 *   ↓
 * Select Coin Plan
 *   ↓
 * CoinOrder Created
 *   ↓
 * Payment Created
 *   ↓
 * Payment Success
 *   ↓
 * WalletTransaction (Coins Credit)
 *
 * IMPORTANT
 * --------------------------------------------------------------------------
 * CoinOrder sirf order ko represent karta hai.
 *
 * Ye Wallet manage nahi karta.
 * Ye Coins Credit nahi karta.
 * Ye Razorpay Verification nahi karta.
 *
 * Payment aur Wallet alag tables handle karengi.
 * ============================================================================
 */

@Entity
@Table(
        name = "coin_orders",
        indexes = {

                @Index(name = "idx_coin_order_user", columnList = "user_id"),

                @Index(name = "idx_coin_order_number", columnList = "order_number", unique = true),

                @Index(name = "idx_coin_order_status", columnList = "status"),

                /**
                 * Scheduler: find expired CREATED orders efficiently.
                 */
                @Index(name = "idx_coin_order_expires", columnList = "status, expires_at"),

                /**
                 * Pending order check: find active orders per user quickly.
                 */
                @Index(name = "idx_coin_order_user_status", columnList = "user_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinOrder {

    /**
     * Internal Database ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Business Order Number
     *
     * Example:
     *
     * ORD2026000001
     * ORD2026000002
     */
    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    /**
     * Kis user ne order place kiya.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Original Coin Plan.
     *
     * Reporting ke liye useful hai.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable =false)
    private CoinPlan plan;

    // ===========================================================
    // PLAN SNAPSHOT
    // ===========================================================

    /**
     * Purchase ke time Plan Code.
     *
     * CoinPlan future me change ho sakta hai,
     * isliye snapshot save karna zaruri hai.
     */
    @Column(nullable = false)
    private String planCode;

    /**
     * Purchase ke time Plan Name.
     */
    @Column(nullable = false)
    private String planName;

    /**
     * Purchase ke time Base Coins.
     */
    @Column(nullable = false)
    private Integer baseCoins;

    /**
     * Purchase ke time Bonus Coins.
     */
    @Column(nullable = false)
    private Integer bonusCoins;

    /**
     * Total Coins
     *
     * Base + Bonus
     */
    @Column(nullable = false)
    private Integer totalCoins;

    /**
     * Purchase Amount Snapshot.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal orderAmount;

    /**
     * Currency Snapshot.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyType currency;

    // ===========================================================
    // ORDER STATUS
    // ===========================================================

    /**
     * Order ki current state.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private CoinOrderStatus status = CoinOrderStatus.CREATED;

    /**
     * Optional Notes
     *
     * Admin/Internal use.
     */
    @Column(length = 500)
    private String remarks;

    // ===========================================================
    // ORDER EXPIRY
    // ===========================================================

    /**
     * When this order auto-expires.
     *
     * Default: 30 minutes after creation.
     *
     * Scheduler runs every 5 minutes and marks
     * CREATED orders as EXPIRED if expiresAt has passed.
     *
     * This matches Razorpay's default order validity.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // ===========================================================
    // OPTIMISTIC LOCKING
    // ===========================================================

    /**
     * Prevents concurrent status update corruption.
     *
     * Example:
     * Webhook says PAYMENT_SUCCESS at the same time
     * scheduler tries to mark EXPIRED.
     *
     * @Version ensures only one update wins.
     * The other gets OptimisticLockException and retries.
     */
    @Version
    private Long version;

    /**
     * Record Creation Time
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last Update Time
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
