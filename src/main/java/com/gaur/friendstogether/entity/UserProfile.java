package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;
    private String avatarUrl;
    private String gender;
    private LocalDate dob;
    private String region;
    private String signature;
    private String titleBadge;

    // ==========================================
    // SOCIAL ECONOMY & REWARDS
    // ==========================================

    @Builder.Default
    private long coinsBalance = 0;   // Daily bonus aur purchase ke liye

    @Builder.Default
    private long charmPoints = 0;    // Gifts receive karne se (Popularity)

    @Builder.Default
    private int loginStreak = 0;     // Consecutive login tracking

    private LocalDate lastLoginDate; // Streak reset logic ke liye

    // ==========================================
    // LEVELING & TIERS
    // ==========================================

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserTier tier = UserTier.SILVER;

    @Column(columnDefinition = "int default 1")
    @Builder.Default
    private int level = 1;

    // ==========================================
    // RELATION: User table se link karne ke liye
    // ==========================================
    @com.fasterxml.jackson.annotation.JsonBackReference
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
}