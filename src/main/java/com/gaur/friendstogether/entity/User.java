package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_user_id", columnList = "user_id") // Search fast karne ke liye
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Ye database ki internal ID (1, 2, 3...) rahegi

    // ==========================================
    // Aapka 9-Digit Unique Number (Social Search ke liye)
    // ==========================================
    @Column(name = "user_id", unique = true, nullable = true, length = 9)
    private String userId;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = true)
    private String password;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String otp;
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    private LocalDateTime otpExpiry;
    
    private boolean isEnabled = false;

    // ==========================================
    // OAuth 2.0 Integration Fields
    // ==========================================
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "provider_id")
    private String providerId;

    @PrePersist
    @PostLoad
    protected void ensureDefaultAuthProvider() {
        if (this.authProvider == null) {
            this.authProvider = AuthProvider.LOCAL;
        }
    }

    // ==========================================
    // User Role (ADMIN / USER)
    // ==========================================
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    // RELATION: One-to-One with UserProfile
    // mappedBy ka matlab hai ki relation ki asli malik 'UserProfile' table hai
    @com.fasterxml.jackson.annotation.JsonManagedReference("user-profile")
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile profile;

    // RELATION: One-to-One with UserWallet
    // Har user ka sirf ek wallet hota hai — registration ke waqt auto-create hota hai
    @com.fasterxml.jackson.annotation.JsonManagedReference("user-wallet")
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserWallet wallet;
}