[33mcommit 95287c159eb9cfee817db9df20e5cfacce1f59ae[m[33m ([m[1;36mHEAD[m[33m -> [m[1;32mOAuth2[m[33m)[m
Author: MOHD-SHAHVEZ <javedgaur57@gmail.com>
Date:   Thu Jun 11 23:43:55 2026 +0530

    feat(auth): harden user authentication model for future OAuth2 provider support

[1mdiff --git a/src/main/java/com/gaur/friendstogether/config/DatabaseMigrationRunner.java b/src/main/java/com/gaur/friendstogether/config/DatabaseMigrationRunner.java[m
[1mnew file mode 100644[m
[1mindex 0000000..5e1a77c[m
[1m--- /dev/null[m
[1m+++ b/src/main/java/com/gaur/friendstogether/config/DatabaseMigrationRunner.java[m
[36m@@ -0,0 +1,40 @@[m
[32m+[m[32mpackage com.gaur.friendstogether.config;[m
[32m+[m
[32m+[m[32mimport lombok.RequiredArgsConstructor;[m
[32m+[m[32mimport lombok.extern.slf4j.Slf4j;[m
[32m+[m[32mimport org.springframework.boot.CommandLineRunner;[m
[32m+[m[32mimport org.springframework.jdbc.core.JdbcTemplate;[m
[32m+[m[32mimport org.springframework.stereotype.Component;[m
[32m+[m
[32m+[m[32m/**[m
[32m+[m[32m * Migration runner that executes on application startup.[m
[32m+[m[32m * Safely migrates existing user rows that lack an auth_provider[m
[32m+[m[32m * value by setting them to 'LOCAL'.[m
[32m+[m[32m */[m
[32m+[m[32m@Component[m
[32m+[m[32m@RequiredArgsConstructor[m
[32m+[m[32m@Slf4j[m
[32m+[m[32mpublic class DatabaseMigrationRunner implements CommandLineRunner {[m
[32m+[m
[32m+[m[32m    private final JdbcTemplate jdbcTemplate;[m
[32m+[m
[32m+[m[32m    @Override[m
[32m+[m[32m    public void run(String... args) throws Exception {[m
[32m+[m[32m        log.info("Running database migrations for existing legacy users...");[m
[32m+[m[32m        try {[m
[32m+[m[32m            // Safe SQL update: only touches rows where auth_provider is missing[m
[32m+[m[32m            int updatedRows = jdbcTemplate.update([m
[32m+[m[32m                "UPDATE users SET auth_provider = 'LOCAL' WHERE auth_provider IS NULL"[m
[32m+[m[32m            );[m
[32m+[m[32m            if (updatedRows > 0) {[m
[32m+[m[32m                log.info("Successfully migrated {} legacy users to auth_provider = 'LOCAL'.", updatedRows);[m
[32m+[m[32m            } else {[m
[32m+[m[32m                log.info("No legacy users required auth_provider migration.");[m
[32m+[m[32m            }[m
[32m+[m[32m        } catch (Exception e) {[m
[32m+[m[32m            log.error("Failed to execute auth_provider migration: {}", e.getMessage());[m
[32m+[m[32m            // We do not throw to prevent application startup failure.[m[41m [m
[32m+[m[32m            // The @PostLoad fallback in User.java provides defensive runtime safety.[m
[32m+[m[32m        }[m
[32m+[m[32m    }[m
[32m+[m[32m}[m
[1mdiff --git a/src/main/java/com/gaur/friendstogether/entity/AuthProvider.java b/src/main/java/com/gaur/friendstogether/entity/AuthProvider.java[m
[1mnew file mode 100644[m
[1mindex 0000000..b32525f[m
[1m--- /dev/null[m
[1m+++ b/src/main/java/com/gaur/friendstogether/entity/AuthProvider.java[m
[36m@@ -0,0 +1,9 @@[m
[32m+[m[32mpackage com.gaur.friendstogether.entity;[m
[32m+[m
[32m+[m[32mpublic enum AuthProvider {[m
[32m+[m[32m    LOCAL,[m
[32m+[m[32m    GOOGLE,[m
[32m+[m[32m    APPLE,[m
[32m+[m[32m    FACEBOOK,[m
[32m+[m[32m    GITHUB[m
[32m+[m[32m}[m
[1mdiff --git a/src/main/java/com/gaur/friendstogether/entity/User.java b/src/main/java/com/gaur/friendstogether/entity/User.java[m
[1mindex 0ff882f..c3691a5 100644[m
[1m--- a/src/main/java/com/gaur/friendstogether/entity/User.java[m
[1m+++ b/src/main/java/com/gaur/friendstogether/entity/User.java[m
[36m@@ -29,7 +29,7 @@[m [mpublic class User {[m
     private String email;[m
 [m
     @com.fasterxml.jackson.annotation.JsonIgnore[m
[31m-    @Column(nullable = false)[m
[32m+[m[32m    @Column(nullable = true)[m
     private String password;[m
 [m
     @com.fasterxml.jackson.annotation.JsonIgnore[m
[36m@@ -40,6 +40,25 @@[m [mpublic class User {[m
     [m
     private boolean isEnabled = false;[m
 [m
[32m+[m[32m    // ==========================================[m
[32m+[m[32m    // OAuth 2.0 Integration Fields[m
[32m+[m[32m    // ==========================================[m
[32m+[m[32m    @Enumerated(EnumType.STRING)[m
[32m+[m[32m    @Column(name = "auth_provider", nullable = false)[m
[32m+[m[32m    @Builder.Default[m
[32m+[m[32m    private AuthProvider authProvider = AuthProvider.LOCAL;[m
[32m+[m
[32m+[m[32m    @Column(name = "provider_id")[m
[32m+[m[32m    private String providerId;[m
[32m+[m
[32m+[m[32m    @PrePersist[m
[32m+[m[32m    @PostLoad[m
[32m+[m[32m    protected void ensureDefaultAuthProvider() {[m
[32m+[m[32m        if (this.authProvider == null) {[m
[32m+[m[32m            this.authProvider = AuthProvider.LOCAL;[m
[32m+[m[32m        }[m
[32m+[m[32m    }[m
[32m+[m
     // RELATION: One-to-One with UserProfile[m
     // mappedBy ka matlab hai ki relation ki asli malik 'UserProfile' table hai[m
     @com.fasterxml.jackson.annotation.JsonManagedReference[m
[1mdiff --git a/src/main/java/com/gaur/friendstogether/security/config/ApplicationConfig.java b/src/main/java/com/gaur/friendstogether/security/config/ApplicationConfig.java[m
[1mindex adc2be8..4b75365 100644[m
[1m--- a/src/main/java/com/gaur/friendstogether/security/config/ApplicationConfig.java[m
[1m+++ b/src/main/java/com/gaur/friendstogether/security/config/ApplicationConfig.java[m
[36m@@ -23,7 +23,7 @@[m [mpublic class ApplicationConfig {[m
 [m
             return org.springframework.security.core.userdetails.User[m
                     .withUsername(user.getEmail()) // Username as Email[m
[31m-                    .password(user.getPassword())[m
[32m+[m[32m                    .password(user.getPassword() != null ? user.getPassword() : "")[m
                     .authorities("USER")[m
                     .disabled(!user.isEnabled())[m
                     .build();[m
[1mdiff --git a/src/main/java/com/gaur/friendstogether/service/AuthService.java b/src/main/java/com/gaur/friendstogether/service/AuthService.java[m
[1mindex 8e41092..400b7b2 100644[m
[1m--- a/src/main/java/com/gaur/friendstogether/service/AuthService.java[m
[1m+++ b/src/main/java/com/gaur/friendstogether/service/AuthService.java[m
[36m@@ -4,6 +4,7 @@[m [mimport com.gaur.friendstogether.dto.request.LoginRequest;[m
 import com.gaur.friendstogether.dto.request.RegisterRequest;[m
 import com.gaur.friendstogether.dto.request.VerifyOtpRequest;[m
 import com.gaur.friendstogether.dto.response.AuthResponse;[m
[32m+[m[32mimport com.gaur.friendstogether.entity.AuthProvider;[m
 import com.gaur.friendstogether.entity.User;[m
 import com.gaur.friendstogether.entity.UserProfile;[m
 import com.gaur.friendstogether.repository.UserRepository;[m
[36m@@ -140,7 +141,11 @@[m [mpublic class AuthService {[m
             throw new RuntimeException("Error: Please verify your email first.");[m
         }[m
 [m
[31m-        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {[m
[32m+[m[32m        if (user.getAuthProvider() != AuthProvider.LOCAL) {[m
[32m+[m[32m            throw new RuntimeException("Error: Please continue using your " + user.getAuthProvider() + " account.");[m
[32m+[m[32m        }[m
[32m+[m
[32m+[m[32m        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {[m
             throw new RuntimeException("Error: Invalid credentials.");[m
         }[m
 [m
