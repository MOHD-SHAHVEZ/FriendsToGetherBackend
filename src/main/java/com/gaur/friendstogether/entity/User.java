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

    @Column(nullable = false)
    private String password;

    private String otp;
    private LocalDateTime otpExpiry;
    private boolean isEnabled = false;

    // RELATION: One-to-One with UserProfile
    // mappedBy ka matlab hai ki relation ki asli malik 'UserProfile' table hai
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile profile;
}