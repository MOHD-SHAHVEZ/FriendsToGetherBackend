package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 🔔 UserNotification:
 * User ko aane wale personal alerts aur invites.
 */
@Entity
@Table(name = "user_notifications", indexes = {
        @Index(name = "idx_notif_user", columnList = "user_id"),
        @Index(name = "idx_notif_time", columnList = "createdAt") // Inbox fast load karne ke liye
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Notification kise mili
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User receiver;

    // Notification bhejne wala (e.g., Room Owner)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private String title;
    private String message;

    // Link: Click karne par kahan le jana hai
    private String deepLink;

    /**
     * JSON metadata: Analytics aur dynamic UI ke liye
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Builder.Default
    private boolean isRead = false; // Notification status (Unread/Read)

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}