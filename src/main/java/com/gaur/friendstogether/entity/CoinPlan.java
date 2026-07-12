package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CoinPlan
 *
 * Ye table app ke sabhi Coin Packages ko manage karti hai.
 * User isi table se packages dekhega aur purchase karega.
 *
 * Example:
 * --------------------------------------------
 * Starter Pack   -> ₹99   -> 100 Coins
 * VIP Pack       -> ₹499  -> 600 Coins
 * Mega Pack      -> ₹999  -> 1300 Coins
 * --------------------------------------------
 */
@Entity
@Table(name = "coin_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinPlan {

    /**
     * Database Primary Key
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique Business Code
     *
     * Ye backend aur payment system ke liye use hoga.
     * Frontend ya Razorpay integration me ID ki jagah ye code use karna better hota hai.
     *
     * Example:
     * STARTER_99
     * VIP_499
     * MEGA_999
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Package ka Display Name
     *
     * Example:
     * Starter Pack
     * VIP Pack
     * Mega Pack
     */
    @Column(nullable = false)
    private String name;

    /**
     * Package ka short description.
     *
     * Example:
     * Best For Beginners
     * Most Popular Choice
     * Limited Time Offer
     */
    @Column(length = 500)
    private String description;

    /**
     * Package ki image ya icon ka URL.
     *
     * Example:
     * https://cdn.app.com/images/starter-pack.png
     */
    private String imageUrl;

    /**
     * User ko milne wale actual Coins.
     */
    @Column(nullable = false)
    private Integer coins;

    /**
     * Extra Promotional Bonus Coins.
     *
     * Example:
     * 100 Coins + 20 Bonus Coins
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer bonusCoins = 0;

    /**
     * Package ki Price.
     *
     * Example:
     * ₹99
     * ₹499
     * ₹999
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

   /**
    * Package ki Currency.
    *
    * Abhi default INR rahegi.
    * Future me same application multiple countries support kar sakegi.
    */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 5)
    private CurrencyType currency = CurrencyType.INR;


    /**
     * Package Badge
     *
     * Frontend iske basis par tag dikhayega.
     *
     * Example:
     * POPULAR
     * HOT
     * BEST_VALUE
     * NEW
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private CoinPlanBadge badge = CoinPlanBadge.NONE;

    /**
     * Package ki current availability.
     *
     * ACTIVE
     * INACTIVE
     * COMING_SOON
     * EXPIRED
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private CoinPlanStatus status = CoinPlanStatus.ACTIVE;

    /**
     * Frontend me kis order me packages show honge.
     *
     * Small value = Pehle dikhega.
     */
    @Column(nullable = false)
    private Integer displayOrder;

    /**
     * Offer kab start hogi.
     * Null means hamesha available.
     */
    private LocalDateTime offerStartAt;

    /**
     * Offer kab khatam hogi.
     * Null means expiry nahi hai.
     */
    private LocalDateTime offerEndAt;

    /**
     * Maximum kitni baar user is package ko purchase kar sakta hai.
     *
     * Null = Unlimited
     * 1 = One Time Purchase
     * 5 = Maximum 5 Times
     */
    private Integer purchaseLimit;

    /**
     * Future changes ke liye.
     *
     * Agar kabhi package update hota hai to version increase kar sakte hain.
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    /**
     * Record Creation Time
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last Updated Time
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
