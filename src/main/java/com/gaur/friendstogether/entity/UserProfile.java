package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
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

    @Column(columnDefinition = "int default 1")
    private int level = 1;

    // RELATION: User table se link karne ke liye
    @com.fasterxml.jackson.annotation.JsonBackReference
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
}