# 🔐 FriendsTogether — OAuth 2.0 Complete Implementation Guide

> **Project:** FriendsTogether Backend (Spring Boot 4 + Spring Security 7)  
> **Author:** Mohd Shahvez  
> **Date:** June 2026  
> **Status:** ✅ Production Ready

---

## 📑 Table of Contents

1. [What is OAuth 2.0?](#1-what-is-oauth-20)
2. [FriendsTogether Authentication Architecture](#2-friendstogether-authentication-architecture)
3. [Google OAuth2 — Complete Flow](#3-google-oauth2--complete-flow)
4. [File Architecture & Responsibilities](#4-file-architecture--responsibilities)
5. [Implementation — Step by Step](#5-implementation--step-by-step)
6. [Code Walkthrough — Every File Explained](#6-code-walkthrough--every-file-explained)
7. [How Each Component Calls The Next](#7-how-each-component-calls-the-next)
8. [Database Schema & Entity Design](#8-database-schema--entity-design)
9. [Redis TEMP_CODE Architecture](#9-redis-temp_code-architecture)
10. [Security Configuration Deep Dive](#10-security-configuration-deep-dive)
11. [Challenges Faced & Solutions](#11-challenges-faced--solutions)
12. [Testing Scenarios](#12-testing-scenarios)
13. [Production Checklist](#13-production-checklist)

---

## 1. What is OAuth 2.0?

OAuth 2.0 ek **authorization framework** hai jo third-party applications ko user ke data ka limited access deta hai — **bina user ka password share kiye**.

### Simple Analogy

```
Tum ek hotel mein gaye. Reception pe tumne ID dikhaya.
Reception ne tumhe ek key card diya (access token).
Ab tum key card se apna room khol sakte ho.
Tumne hotel ko apna ghar ki chaabi nahi di — sirf ID dikhayi.

OAuth 2.0 mein:
- Tum = User
- Hotel = FriendsTogether App
- Reception = Google
- ID = Google Account
- Key Card = Access Token
```

### OAuth 2.0 Roles

```mermaid
flowchart LR
    A["👤 Resource Owner<br/>(User)"] --> B["📱 Client<br/>(FriendsTogether App)"]
    B --> C["🔑 Authorization Server<br/>(Google)"]
    C --> D["📦 Resource Server<br/>(Google APIs)"]
    
    style A fill:#3498db,color:#fff
    style B fill:#2ecc71,color:#fff
    style C fill:#e74c3c,color:#fff
    style D fill:#9b59b6,color:#fff
```

| Role | Kaun Hai | Kya Karta Hai |
|------|----------|---------------|
| **Resource Owner** | User (Mohd Shahvez) | Apna Google account allow karta hai |
| **Client** | FriendsTogether Backend | Google se user info maangta hai |
| **Authorization Server** | Google OAuth Server | User ko verify karta hai, token deta hai |
| **Resource Server** | Google APIs | User info (email, name) provide karta hai |

### OAuth 2.0 Grant Types

| Grant Type | Use Case | FriendsTogether mein? |
|------------|----------|----------------------|
| **Authorization Code** | Server-side apps (most secure) | ✅ **YES — This one!** |
| Client Credentials | Machine-to-machine | ❌ No |
| Implicit | Single-page apps (deprecated) | ❌ No |
| Resource Owner Password | Direct password (not recommended) | ❌ No |

---

## 2. FriendsTogether Authentication Architecture

FriendsTogether **hybrid authentication** support karta hai — dono LOCAL aur GOOGLE login:

```mermaid
flowchart TD
    subgraph LOCAL["🔑 LOCAL Authentication"]
        L1["POST /api/auth/register"] --> L2["User Created<br/>isEnabled=false"]
        L2 --> L3["OTP Email Sent"]
        L3 --> L4["POST /api/auth/verify-otp"]
        L4 --> L5["User Enabled<br/>userId Generated"]
        L5 --> L6["POST /api/auth/login"]
        L6 --> L7["JWT Access + Refresh Tokens"]
    end

    subgraph GOOGLE["🌐 GOOGLE OAuth Authentication"]
        G1["GET /oauth2/authorization/google"] --> G2["Google Login Page"]
        G2 --> G3["Spring Security Callback"]
        G3 --> G4["CustomOAuth2UserService<br/>User Created in MySQL"]
        G4 --> G5["OAuth2SuccessHandler<br/>TEMP_CODE → Redis"]
        G5 --> G6["Redirect to Frontend<br/>?code=UUID"]
        G6 --> G7["POST /api/auth/oauth/exchange"]
        G7 --> G8["JWT Access + Refresh Tokens"]
    end

    style LOCAL fill:#2c3e50,color:#fff
    style GOOGLE fill:#1a5276,color:#fff
    style L7 fill:#27ae60,color:#fff
    style G8 fill:#27ae60,color:#fff
```

### Key Difference

| Feature | LOCAL | GOOGLE |
|---------|-------|--------|
| Password | Stored (BCrypt hashed) | NULL (no password) |
| Email Verification | OTP required | Google verified |
| isEnabled | false → true (after OTP) | true (immediately) |
| authProvider | `LOCAL` | `GOOGLE` |
| userId (9-digit) | Generated after OTP verify | Generated at creation |
| UserProfile | Created at registration | Created at OAuth login |

---

## 3. Google OAuth2 — Complete Flow

### Phase 1: Authorization Request

```mermaid
sequenceDiagram
    participant U as 👤 User Browser
    participant FE as 📱 Frontend (Vite)
    participant BE as 🖥️ Backend (Spring Boot)
    participant G as 🔑 Google

    U->>FE: Click "Continue with Google"
    FE->>BE: GET /oauth2/authorization/google
    
    Note over BE: Spring Security intercepts this URL<br/>OAuth2AuthorizationRequestRedirectFilter
    
    BE->>BE: Generate state parameter (CSRF protection)
    BE->>U: 302 Redirect to Google
    
    Note over U,G: URL: https://accounts.google.com/o/oauth2/v2/auth<br/>?client_id=718590628279-xxx.apps.googleusercontent.com<br/>&redirect_uri=http://localhost:8080/login/oauth2/code/google<br/>&response_type=code<br/>&scope=email profile<br/>&state=random_csrf_token
    
    U->>G: User sees Google consent screen
    U->>G: Selects Google account & grants permission
```

### Phase 2: Authorization Code Exchange

```mermaid
sequenceDiagram
    participant U as 👤 User Browser
    participant BE as 🖥️ Backend
    participant G as 🔑 Google
    participant COS as CustomOAuth2UserService
    participant DB as 🗄️ MySQL

    G->>U: 302 Redirect back to backend
    Note over G,U: URL: http://localhost:8080/login/oauth2/code/google<br/>?code=4/0AQSTgQH...<br/>&state=random_csrf_token
    
    U->>BE: Browser follows redirect automatically
    
    Note over BE: OAuth2LoginAuthenticationFilter intercepts
    
    BE->>G: Exchange auth code for access token
    Note over BE,G: POST https://oauth2.googleapis.com/token<br/>client_id, client_secret, code, redirect_uri
    
    G-->>BE: Access Token + User Info
    
    BE->>COS: loadUser(OAuth2UserRequest)
    COS->>G: GET https://www.googleapis.com/oauth2/v3/userinfo
    G-->>COS: {email, name, sub, picture}
    
    COS->>DB: findByEmail(email)
    
    alt New User
        COS->>DB: INSERT user (authProvider=GOOGLE, password=NULL)
    else Existing Google User
        COS->>COS: Validate provider match
    else Existing LOCAL User
        COS->>COS: REJECT login
    end
    
    COS-->>BE: Return OAuth2User
```

### Phase 3: TEMP_CODE + JWT Exchange

```mermaid
sequenceDiagram
    participant BE as 🖥️ Backend
    participant SH as SuccessHandler
    participant R as 🔴 Redis
    participant FE as 📱 Frontend
    participant OC as OAuthController
    participant JWT as JwtUtil

    BE->>SH: onAuthenticationSuccess()
    SH->>SH: Extract email, name, providerId from OAuth2User
    SH->>R: SET oauth:code:UUID → {email, name, providerId, provider}<br/>TTL = 60 seconds
    SH->>FE: 302 Redirect → /oauth2/callback?code=UUID
    
    Note over FE: Frontend JavaScript reads ?code= parameter
    
    FE->>OC: POST /api/auth/oauth/exchange<br/>{code: "UUID"}
    OC->>R: GET oauth:code:UUID
    R-->>OC: OAuthSessionData {email, name, providerId, provider}
    OC->>R: DELETE oauth:code:UUID (one-time use!)
    
    OC->>OC: findByEmail(email) → User found ✅
    OC->>JWT: generateToken(email)
    OC->>JWT: generateRefreshToken(email)
    
    OC-->>FE: 200 OK
    Note over FE: Response:<br/>{<br/>  accessToken: "eyJhbG...",<br/>  refreshToken: "eyJhbG...",<br/>  tokenType: "Bearer",<br/>  username: "MohdShahvez"<br/>}
    
    Note over FE: 🎉 User is logged in!<br/>Store tokens in localStorage
```

### Why TEMP_CODE? Why Not Direct JWT?

```mermaid
flowchart TD
    Q["❓ Why not return JWT directly<br/>in the redirect URL?"] --> A["🔒 Security Reasons"]
    
    A --> R1["1️⃣ URLs are logged in browser history<br/>JWT in URL = token leaked"]
    A --> R2["2️⃣ URLs are logged in server access logs<br/>JWT in logs = token leaked"]
    A --> R3["3️⃣ URLs have length limits<br/>JWT can be 1000+ chars"]
    A --> R4["4️⃣ Referrer headers can leak URL<br/>Next site sees your JWT"]
    
    R1 --> S["✅ Solution: TEMP_CODE Pattern"]
    R2 --> S
    R3 --> S
    R4 --> S
    
    S --> S1["Short UUID in URL (safe to log)"]
    S --> S2["60-second TTL (expires quickly)"]
    S --> S3["One-time use (deleted after exchange)"]
    S --> S4["JWT sent in POST response body (not URL)"]
    
    style Q fill:#3498db,color:#fff
    style A fill:#e74c3c,color:#fff
    style S fill:#27ae60,color:#fff
```

---

## 4. File Architecture & Responsibilities

```mermaid
flowchart TD
    subgraph CONFIG["⚙️ Configuration Layer"]
        SC["SecurityConfig.java<br/>Filter chain, OAuth2 setup"]
        AC["ApplicationConfig.java<br/>UserDetailsService, PasswordEncoder"]
        RC["RedisConfig.java<br/>Redis templates"]
        AP["application.properties<br/>Google client ID/secret, scopes"]
    end

    subgraph OAUTH["🔐 OAuth2 Security Layer"]
        COS["CustomOAuth2UserService.java<br/>User lookup/creation on Google login"]
        SH["OAuth2SuccessHandler.java<br/>Generate TEMP_CODE, redirect"]
        FH["OAuth2FailureHandler.java<br/>Redirect with error"]
        UIF["OAuth2UserInfoFactory.java<br/>Create provider-specific UserInfo"]
        GUI["GoogleOAuth2UserInfo.java<br/>Extract email, name, sub from Google"]
        UI["OAuth2UserInfo.java<br/>Abstract base class"]
    end

    subgraph SERVICE["🔧 Service Layer"]
        UCS["UserCreationService.java<br/>Create user + profile in DB"]
        OAS["OAuthAuthenticationService.java<br/>Redis TEMP_CODE + JWT exchange"]
        AS["AuthService.java<br/>Local register/login/OTP"]
    end

    subgraph CONTROLLER["🌐 Controller Layer"]
        OC["OAuthController.java<br/>POST /api/auth/oauth/exchange"]
        AuC["AuthController.java<br/>register, verify-otp, login"]
    end

    subgraph DATA["📦 Data Layer"]
        U["User.java<br/>Entity with authProvider, providerId"]
        UP["UserProfile.java<br/>Profile linked to User"]
        UR["UserRepository.java<br/>findByEmail, existsByUsername"]
        OSD["OAuthSessionData.java<br/>DTO for Redis storage"]
        AP2["AuthProvider.java<br/>Enum: LOCAL, GOOGLE, etc."]
    end

    subgraph MIGRATION["🔄 Migration"]
        DMR["DatabaseMigrationRunner.java<br/>ALTER password NULL, etc."]
    end

    SC --> COS
    SC --> SH
    SC --> FH
    COS --> UIF
    UIF --> GUI
    COS --> UCS
    SH --> OAS
    OC --> OAS
    UCS --> UR
    OAS --> UR
    UR --> U
    U --> UP

    style CONFIG fill:#2c3e50,color:#fff
    style OAUTH fill:#8e44ad,color:#fff
    style SERVICE fill:#2980b9,color:#fff
    style CONTROLLER fill:#27ae60,color:#fff
    style DATA fill:#d35400,color:#fff
    style MIGRATION fill:#7f8c8d,color:#fff
```

---

## 5. Implementation — Step by Step

### Step 1: Google Cloud Console Setup

```
1. Go to console.cloud.google.com
2. Create project → "FriendsTogether"
3. APIs & Services → Credentials
4. Create OAuth 2.0 Client ID:
   - Application type: Web application
   - Authorized redirect URIs: 
     http://localhost:8080/login/oauth2/code/google
5. Copy: Client ID + Client Secret
```

### Step 2: Maven Dependencies (pom.xml)

```xml
<!-- Spring Security OAuth2 Client -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Redis (for TEMP_CODE) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
```

### Step 3: Application Properties

```properties
# Google OAuth2 Configuration
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.registration.google.scope=email,profile

# Frontend redirect URL (after OAuth success)
app.oauth.redirect-uri=http://localhost:5173/oauth2/callback

# IMPORTANT: Do NOT include 'openid' in scope!
# openid → Spring uses OidcUserService (ignores your CustomOAuth2UserService)
# email,profile → Spring uses DefaultOAuth2UserService (calls YOUR service)
```

### Step 4: Entity Design

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = true)      // ← NULL for OAuth users!
    private String password;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider;  // LOCAL, GOOGLE, APPLE...
    
    private String providerId;         // Google's "sub" ID
    
    @Column(unique = true, length = 9)
    private String userId;             // 9-digit unique ID
    
    private boolean isEnabled;         // true for OAuth, false until OTP for LOCAL
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;
}
```

### Step 5: Security Config

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> {})
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/ws/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(userInfo -> userInfo
                .userService(customOAuth2UserService)   // YOUR service
            )
            .successHandler(oAuth2AuthenticationSuccessHandler)
            .failureHandler(oAuth2AuthenticationFailureHandler)
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    
    return http.build();
}
```

### Step 6: Custom OAuth2 User Service

```java
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);  // Fetch from Google
        
        // Extract user info
        String email = oAuth2UserInfo.getEmail();
        
        // Check if user exists
        Optional<User> existing = userRepository.findByEmail(email);
        
        if (existing.isPresent()) {
            // Validate: same provider? same providerId?
        } else {
            // NEW USER → Create in database!
            userCreationService.createOAuthUser(userInfo, AuthProvider.GOOGLE);
        }
        
        return oAuth2User;
    }
}
```

### Step 7: User Creation Service

```java
@Transactional
public User createOAuthUser(OAuth2UserInfo userInfo, AuthProvider provider) {
    User user = User.builder()
        .email(userInfo.getEmail())
        .password(null)              // No password for OAuth!
        .isEnabled(true)             // Already verified by Google
        .authProvider(provider)      // GOOGLE
        .providerId(userInfo.getId()) // Google's "sub"
        .userId(generateUniqueId())  // 9-digit ID
        .build();
    
    UserProfile profile = UserProfile.builder()
        .nickname(userInfo.getName())
        .level(1)
        .user(user)
        .build();
    user.setProfile(profile);
    
    return userRepository.saveAndFlush(user);  // Immediate INSERT!
}
```

### Step 8: Success Handler + Redis

```java
public void onAuthenticationSuccess(request, response, authentication) {
    // Extract user info from OAuth2 token
    OAuth2User oAuth2User = oauthToken.getPrincipal();
    String email = userInfo.getEmail();
    
    // Generate one-time code and store in Redis (60 seconds TTL)
    String tempCode = UUID.randomUUID().toString();
    redis.set("oauth:code:" + tempCode, sessionData, 60, SECONDS);
    
    // Redirect frontend with the code
    redirect("http://localhost:5173/oauth2/callback?code=" + tempCode);
}
```

### Step 9: Token Exchange Endpoint

```java
@PostMapping("/api/auth/oauth/exchange")
public AuthResponse exchangeCode(String tempCode) {
    // Get session data from Redis
    OAuthSessionData session = redis.get("oauth:code:" + tempCode);
    redis.delete("oauth:code:" + tempCode);  // One-time use!
    
    // Find user in database
    User user = userRepository.findByEmail(session.getEmail());
    
    // Generate JWTs
    String accessToken = jwtUtil.generateToken(user.getEmail());
    String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
    
    return new AuthResponse(accessToken, refreshToken, "Bearer", user.getUsername());
}
```

---

## 6. Code Walkthrough — Every File Explained

### 📄 OAuth2UserInfo.java — Abstract Base

```java
// Ye abstract class har OAuth provider (Google, Apple, Facebook)
// ke liye common interface define karta hai
public abstract class OAuth2UserInfo {
    protected Map<String, Object> attributes;  // Raw data from provider
    
    public abstract String getId();     // Provider's unique ID
    public abstract String getName();   // User's display name
    public abstract String getEmail();  // User's email
}
```

### 📄 GoogleOAuth2UserInfo.java — Google-Specific

```java
// Google ke response mein specific keys hain:
// "sub" = unique ID, "name" = full name, "email" = email
public class GoogleOAuth2UserInfo extends OAuth2UserInfo {
    public String getId()    { return attributes.get("sub"); }
    public String getName()  { return attributes.get("name"); }
    public String getEmail() { return attributes.get("email"); }
}
```

### 📄 OAuth2UserInfoFactory.java — Factory Pattern

```java
// registrationId ke basis pe correct UserInfo class return karta hai
// Future mein Apple, Facebook add karna ho toh yahan add karo
public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map attributes) {
    if (registrationId.equalsIgnoreCase("google")) {
        return new GoogleOAuth2UserInfo(attributes);
    }
    // Future: else if ("apple") → return new AppleOAuth2UserInfo(attributes);
    throw new IllegalArgumentException("Login with " + registrationId + " not supported");
}
```

### 📄 OAuthSessionData.java — Redis DTO

```java
// Redis mein store hone wala temporary data
// Ye Google se mila data temporarily hold karta hai
// Token exchange ke time use hota hai
public class OAuthSessionData implements Serializable {
    private String email;       // mohd.shahvez.dev@gmail.com
    private String username;    // Mohd Shahvez
    private String providerId;  // 116319787545504381370
    private String provider;    // google
}
```

### 📄 AuthProvider.java — Enum

```java
// Ye define karta hai ki user ne kaunsa method use kiya login ke liye
public enum AuthProvider {
    LOCAL,      // Email + Password
    GOOGLE,     // Google OAuth2
    APPLE,      // Future
    FACEBOOK,   // Future
    GITHUB      // Future
}
```

---

## 7. How Each Component Calls The Next

### Call Chain — New Google User (First Login)

```
1. Browser
   │
   ├──▶ GET /oauth2/authorization/google
   │    │
   │    └──▶ [Spring Security: OAuth2AuthorizationRequestRedirectFilter]
   │         │
   │         └──▶ 302 Redirect → Google consent screen
   │
   ├──▶ Google callback: /login/oauth2/code/google?code=xxx
   │    │
   │    └──▶ [Spring Security: OAuth2LoginAuthenticationFilter]
   │         │
   │         └──▶ [OAuth2LoginAuthenticationProvider]
   │              │
   │              ├──▶ Exchange auth code for access token (Google)
   │              │
   │              └──▶ CustomOAuth2UserService.loadUser()
   │                   │
   │                   ├──▶ super.loadUser() → Fetch userinfo from Google
   │                   │    Returns: OAuth2User {email, name, sub, picture}
   │                   │
   │                   └──▶ processOAuth2User()
   │                        │
   │                        ├──▶ OAuth2UserInfoFactory.getOAuth2UserInfo("google", attributes)
   │                        │    Returns: GoogleOAuth2UserInfo
   │                        │
   │                        ├──▶ userRepository.findByEmail("mohd.shahvez.dev@gmail.com")
   │                        │    Returns: Optional.empty() (new user!)
   │                        │
   │                        ├──▶ AuthProvider.valueOf("GOOGLE")
   │                        │
   │                        └──▶ userCreationService.createOAuthUser(userInfo, GOOGLE)
   │                             │ [@Transactional]
   │                             │
   │                             ├──▶ userRepository.existsByUsername("MohdShahvez")
   │                             │    Returns: false
   │                             │
   │                             ├──▶ idGenerator.generateUniqueUserId()
   │                             │    │
   │                             │    └──▶ userRepository.existsByUserId("417294960")
   │                             │         Returns: false (unique!)
   │                             │
   │                             ├──▶ User.builder()...build()
   │                             │    + UserProfile.builder()...build()
   │                             │
   │                             └──▶ userRepository.saveAndFlush(user)
   │                                  │
   │                                  └──▶ MySQL: INSERT INTO users (...)
   │                                       MySQL: INSERT INTO user_profiles (...)
   │                                       Returns: User {id=42}
   │
   ├──▶ [Authentication SUCCESS]
   │    │
   │    └──▶ OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
   │         │
   │         ├──▶ Extract email from OAuth2User attributes
   │         │
   │         ├──▶ oAuthAuthenticationService.generateAndStoreTempCode()
   │         │    │
   │         │    ├──▶ UUID.randomUUID() → "2119de1f-7d50-42bc..."
   │         │    │
   │         │    └──▶ Redis: SET oauth:code:2119de1f... → {email, name, ...}
   │         │         TTL = 60 seconds
   │         │
   │         └──▶ 302 Redirect → http://localhost:5173/oauth2/callback?code=2119de1f...
   │
   ├──▶ Frontend receives ?code=2119de1f...
   │    │
   │    └──▶ POST /api/auth/oauth/exchange {code: "2119de1f..."}
   │         │
   │         └──▶ OAuthController.exchangeCode()
   │              │
   │              └──▶ oAuthAuthenticationService.exchangeCodeForTokens()
   │                   │
   │                   ├──▶ Redis: GET oauth:code:2119de1f...
   │                   │    Returns: OAuthSessionData {email: "mohd.shahvez.dev@gmail.com"}
   │                   │
   │                   ├──▶ Redis: DELETE oauth:code:2119de1f...
   │                   │
   │                   ├──▶ userRepository.findByEmail("mohd.shahvez.dev@gmail.com")
   │                   │    Returns: User {id=42, authProvider=GOOGLE}
   │                   │
   │                   ├──▶ jwtUtil.generateToken("mohd.shahvez.dev@gmail.com")
   │                   │    Returns: "eyJhbGciOiJIUzI1NiJ9..."
   │                   │
   │                   ├──▶ jwtUtil.generateRefreshToken("mohd.shahvez.dev@gmail.com")
   │                   │    Returns: "eyJhbGciOiJIUzI1NiJ9..."
   │                   │
   │                   └──▶ Return AuthResponse
   │                        {accessToken, refreshToken, "Bearer", "MohdShahvez"}
   │
   └──▶ 🎉 User logged in! Frontend stores tokens.
```

---

## 8. Database Schema & Entity Design

### Users Table

```sql
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       VARCHAR(9) UNIQUE,           -- 9-digit unique social ID
    username      VARCHAR(255) UNIQUE NOT NULL, -- Display name
    email         VARCHAR(255) UNIQUE NOT NULL, -- Login email
    password      VARCHAR(255) NULL,            -- NULL for OAuth users!
    otp           VARCHAR(255),                 -- OTP for local registration
    otp_expiry    DATETIME,                     -- OTP expiry time
    is_enabled    BOOLEAN DEFAULT FALSE,        -- Account active?
    auth_provider VARCHAR(20) NOT NULL,         -- LOCAL, GOOGLE, APPLE...
    provider_id   VARCHAR(255),                 -- Google's "sub" ID
    
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_user_id (user_id)
);
```

### User Profiles Table

```sql
CREATE TABLE user_profiles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nickname        VARCHAR(255),
    avatar_url      VARCHAR(255),
    gender          VARCHAR(50),
    dob             DATE,
    region          VARCHAR(100),
    signature       VARCHAR(500),
    title_badge     VARCHAR(100),
    coins_balance   BIGINT DEFAULT 0,
    charm_points    BIGINT DEFAULT 0,
    login_streak    INT DEFAULT 0,
    last_login_date DATE,
    tier            VARCHAR(20) DEFAULT 'SILVER',
    level           INT DEFAULT 1,
    user_id         BIGINT REFERENCES users(id)   -- FK to users table
);
```

### Entity Relationship

```mermaid
erDiagram
    USERS ||--|| USER_PROFILES : "has one"
    USERS {
        bigint id PK
        varchar email UK
        varchar username UK
        varchar password "NULL for OAuth"
        varchar user_id UK "9-digit"
        varchar auth_provider "LOCAL/GOOGLE"
        varchar provider_id "Google sub"
        boolean is_enabled
    }
    USER_PROFILES {
        bigint id PK
        varchar nickname
        varchar avatar_url
        bigint coins_balance
        bigint charm_points
        int level
        varchar tier
        bigint user_id FK
    }
```

### What a LOCAL User Looks Like in DB

```
id=1, email=test@gmail.com, username=TestUser, 
password=$2a$10$xKz...hashed..., auth_provider=LOCAL,
provider_id=NULL, user_id=583291746, is_enabled=true
```

### What a GOOGLE User Looks Like in DB

```
id=42, email=mohd.shahvez.dev@gmail.com, username=MohdShahvez,
password=NULL, auth_provider=GOOGLE,
provider_id=116319787545504381370, user_id=417294960, is_enabled=true
```

---

## 9. Redis TEMP_CODE Architecture

### Why Redis?

```mermaid
flowchart LR
    subgraph WITHOUT["❌ Without Redis"]
        A1["JWT in redirect URL"] --> A2["Token visible in<br/>browser history"]
        A2 --> A3["Security risk! 🔓"]
    end

    subgraph WITH["✅ With Redis TEMP_CODE"]
        B1["UUID in redirect URL"] --> B2["UUID is meaningless<br/>to attackers"]
        B2 --> B3["Exchange for JWT<br/>via POST body"]
        B3 --> B4["Secure! 🔒"]
    end

    style WITHOUT fill:#e74c3c,color:#fff
    style WITH fill:#27ae60,color:#fff
```

### Redis Data Flow

```
STEP 1: Success Handler stores data
┌─────────────────────────────────────────────────┐
│ Redis Key: "oauth:code:2119de1f-7d50-42bc-..."  │
│ Redis Value: {                                   │
│   "email": "mohd.shahvez.dev@gmail.com",         │
│   "username": "Mohd Shahvez",                    │
│   "providerId": "116319787545504381370",         │
│   "provider": "google"                           │
│ }                                                │
│ TTL: 60 seconds                                  │
└─────────────────────────────────────────────────┘

STEP 2: Exchange endpoint reads & deletes
┌─────────────────────────────────────────────────┐
│ GET oauth:code:2119de1f...  → OAuthSessionData  │
│ DELETE oauth:code:2119de1f... → one-time use!   │
└─────────────────────────────────────────────────┘

After 60 seconds OR after exchange:
┌─────────────────────────────────────────────────┐
│ Key does not exist anymore                      │
│ Any retry → "Invalid or expired code"           │
└─────────────────────────────────────────────────┘
```

### Redis Configuration

```java
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    
    // Keys: plain strings
    template.setKeySerializer(new StringRedisSerializer());
    
    // Values: JSON (so OAuthSessionData is serialized as JSON)
    template.setValueSerializer(RedisSerializer.json());
    
    return template;
}
```

---

## 10. Security Configuration Deep Dive

### Spring Security Filter Chain Order

```mermaid
flowchart TD
    REQ["🌐 HTTP Request"] --> F1
    
    F1["DisableEncodeUrlFilter"] --> F2["SecurityContextHolderFilter"]
    F2 --> F3["HeaderWriterFilter<br/>(CORS, Security Headers)"]
    F3 --> F4["CorsFilter"]
    F4 --> F5["LogoutFilter"]
    F5 --> F6["OAuth2AuthorizationRequestRedirectFilter<br/>Handles: /oauth2/authorization/google"]
    F6 --> F7["OAuth2LoginAuthenticationFilter<br/>Handles: /login/oauth2/code/google"]
    F7 --> F8["JwtAuthenticationFilter<br/>(YOUR custom filter)"]
    F8 --> F9["AuthorizationFilter<br/>(permitAll vs authenticated)"]
    F9 --> CTRL["🎯 Controller"]
    
    style F6 fill:#e74c3c,color:#fff
    style F7 fill:#e74c3c,color:#fff
    style F8 fill:#3498db,color:#fff
```

### What Each Filter Does

| Filter | Purpose | OAuth Role |
|--------|---------|------------|
| `OAuth2AuthorizationRequestRedirectFilter` | Intercepts `/oauth2/authorization/google` → redirects to Google | Starts OAuth flow |
| `OAuth2LoginAuthenticationFilter` | Intercepts `/login/oauth2/code/google` → exchanges code for token | Completes OAuth flow |
| `JwtAuthenticationFilter` | Checks `Authorization: Bearer <token>` header on all requests | Protects API endpoints |

### STATELESS Session Policy

```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

Matlab: Server koi HTTP session nahi banata. Har request mein JWT token hona chahiye. OAuth flow mein Spring Security temporarily session use karta hai (state parameter ke liye), lekin app ki normal requests STATELESS hain.

---

## 11. Challenges Faced & Solutions

### 🧅 Challenge 1: `CustomOAuth2UserService` Never Called

```mermaid
flowchart TD
    P["Problem:<br/>CustomOAuth2UserService.loadUser()<br/>was NEVER executed"] --> C["Cause:<br/>scope=openid,email,profile"]
    C --> E["Effect:<br/>Spring used OidcUserService<br/>(built-in, no user creation)"]
    E --> S["Solution:<br/>scope=email,profile<br/>(removed openid)"]
    
    style P fill:#e74c3c,color:#fff
    style C fill:#e67e22,color:#fff
    style S fill:#27ae60,color:#fff
```

**How I Discovered It:**  
Backend logs mein `[OAUTH]` markers absent the. Spring Security log mein `OIDC_USER` dikha — matlab OIDC mode active tha.

**Why `openid` Was Problematic:**

| Scope Config | Spring Security Uses | Your Code Called? |
|---|---|---|
| `openid,email,profile` | `OidcUserService` (built-in) | ❌ NO |
| `email,profile` | `DefaultOAuth2UserService` (your custom) | ✅ YES |

**Fix:** One line in `application.properties`:
```diff
- scope=openid,email,profile
+ scope=email,profile
```

---

### 🧅 Challenge 2: Exceptions Were Invisible

```mermaid
flowchart TD
    P["Problem:<br/>No error in logs even when<br/>database INSERT failed"] --> C["Cause:<br/>catch block swallowed exceptions"]
    C --> D["catch(Exception ex) {<br/>  throw new OAuth2AuthenticationException(<br/>    new OAuth2Error('oauth2_error'),<br/>    ex.getMessage()  // no stack trace!<br/>  ); // no logging!<br/>}"]
    D --> S["Solution:<br/>Added logger.error(..., ex)<br/>with full stack trace"]
    
    style P fill:#e74c3c,color:#fff
    style C fill:#e67e22,color:#fff
    style S fill:#27ae60,color:#fff
```

**How I Discovered It:**  
After fixing Challenge 1, the service was called but user still wasn't created. Logs showed entry into creation but no success/failure. Only after adding proper logging did the real error appear.

**Fix:**
```java
// BEFORE: Silent failure
catch (Exception ex) {
    throw new OAuth2AuthenticationException(new OAuth2Error("oauth2_error"), ex.getMessage());
}

// AFTER: Full visibility
catch (Exception ex) {
    logger.error("[OAUTH] EXCEPTION during OAuth2 processing", ex);  // FULL stack trace!
    throw new OAuth2AuthenticationException(new OAuth2Error("oauth2_error"), ex.getMessage(), ex);
}
```

---

### 🧅 Challenge 3: MySQL `password` Column NOT NULL

```mermaid
flowchart TD
    P["Problem:<br/>INSERT INTO users fails with<br/>Column 'password' cannot be null"] --> C["Cause:<br/>MySQL column was NOT NULL<br/>but OAuth users have password=NULL"]
    C --> W["Why Mismatch:<br/>ddl-auto=update does NOT<br/>modify existing column constraints"]
    W --> S["Solution:<br/>ALTER TABLE users<br/>MODIFY password VARCHAR(255) NULL"]
    
    style P fill:#e74c3c,color:#fff
    style C fill:#e67e22,color:#fff
    style W fill:#8e44ad,color:#fff
    style S fill:#27ae60,color:#fff
```

**How I Discovered It:**  
After fixing Challenges 1 and 2, the full stack trace finally appeared:
```
DataIntegrityViolationException: Column 'password' cannot be null
```

**Why It Happened:**
- `users` table was originally created for LOCAL users only → password was NOT NULL
- Later, OAuth was added → entity changed to `@Column(nullable = true)`  
- But `ddl-auto=update` **never** modifies existing column constraints!
- MySQL still had `password NOT NULL` while Java said `nullable = true`

**Fix:** Added migration in `DatabaseMigrationRunner.java`:
```java
jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NULL");
```

---

### 🧅 Challenge 4: `save()` vs `saveAndFlush()` in Security Filter Context

```mermaid
flowchart LR
    subgraph SAVE["save()"]
        S1["Adds to Hibernate<br/>persistence context"] --> S2["SQL deferred to<br/>transaction commit"] --> S3["Error appears<br/>far from source"]
    end
    
    subgraph FLUSH["saveAndFlush()"]
        F1["Adds to persistence<br/>context"] --> F2["SQL executed<br/>IMMEDIATELY"] --> F3["Error appears<br/>at exact line"]
    end
    
    style SAVE fill:#e74c3c,color:#fff
    style FLUSH fill:#27ae60,color:#fff
```

**Why `saveAndFlush()` Mattered:**
- `save()` Hibernate mein entity ko "managed" mark karta hai
- Actual SQL INSERT transaction commit pe hota hai
- Agar constraint fail hota, toh error **bahut baad mein** aata — confusing stack trace
- `saveAndFlush()` ne **turant** SQL execute karwaya → error **exact line** pe dikha

---

### Summary: Three Bugs Stacked Like an Onion

```mermaid
flowchart TD
    subgraph ONION["🧅 The Onion Bug"]
        L1["Layer 1: openid scope<br/>Code never executed"]
        L2["Layer 2: Exception swallowing<br/>Errors invisible"]
        L3["Layer 3: password NOT NULL<br/>INSERT rejected"]
        
        L1 --> |"Fix Layer 1<br/>Remove openid"| L2
        L2 --> |"Fix Layer 2<br/>Add logging"| L3
        L3 --> |"Fix Layer 3<br/>ALTER column"| DONE["✅ Working!"]
    end
    
    style L1 fill:#e74c3c,color:#fff
    style L2 fill:#e67e22,color:#fff
    style L3 fill:#f39c12,color:#fff
    style DONE fill:#27ae60,color:#fff
```

---

## 12. Testing Scenarios

### Scenario 1: New Google User ✅

| Step | Expected |
|------|----------|
| Click "Continue with Google" | Google consent screen opens |
| Select Google account | Redirect to frontend with `?code=UUID` |
| Frontend calls exchange API | JWT tokens returned |
| Check MySQL `users` table | Row exists: `auth_provider=GOOGLE`, `password=NULL`, `is_enabled=true` |
| Check MySQL `user_profiles` table | Row exists: `nickname=Google Name`, `level=1` |

### Scenario 2: Existing Google User (second login) ✅

| Step | Expected |
|------|----------|
| Login again with same Google account | No new row in `users` (no duplicate) |
| Logs show | `findByEmail — user exists: true` |
| Exchange API | JWT tokens returned |

### Scenario 3: LOCAL User With Same Email (rejection) ✅

| Step | Expected |
|------|----------|
| Register local account with email X | Local registration succeeds |
| Try Google login with same email X | **REJECTED** |
| Error message | "This email already belongs to a local account" |
| Frontend URL | `?error=This email already belongs to a local account...` |

### Scenario 4: Local Registration Still Works ✅

| Step | Expected |
|------|----------|
| POST /api/auth/register | "Registration successful! OTP sent." |
| POST /api/auth/verify-otp | "Account verified!" |
| POST /api/auth/login | JWT tokens returned |

---

## 13. Production Checklist

### Before Going Live

- [ ] Change `app.oauth.redirect-uri` to production frontend URL
- [ ] Set Google OAuth redirect URI in Google Cloud Console to production URL
- [ ] Remove debug logging:
  ```properties
  # Remove these after debugging:
  logging.level.org.hibernate.orm.jdbc.bind=TRACE
  logging.level.org.springframework.security.oauth2=DEBUG
  ```
- [ ] Ensure Redis is running and secured
- [ ] Ensure MySQL `password` column is nullable
- [ ] Set `spring.jpa.show-sql=false` in production
- [ ] Move secrets (client-secret, DB password) to environment variables
- [ ] Configure CORS for production domain only
- [ ] Enable HTTPS (OAuth2 requires it in production)

### Environment Variables to Set

```bash
# application.properties should use these:
FRONTEND_OAUTH_REDIRECT_URI=https://yourdomain.com/oauth2/callback
GOOGLE_CLIENT_ID=your-production-client-id
GOOGLE_CLIENT_SECRET=your-production-client-secret
DB_PASSWORD=your-production-db-password
REDIS_HOST=your-production-redis-host
```

---

> **This document covers the complete OAuth2 implementation in FriendsTogether — from concept to production. Every file, every flow, every bug, and every fix is documented here.**
