# Project Structure & Architecture

This document provides a visualization of the `friendstogether` backend architecture, detailing the file structure and the logical flow between the various modules in the application.

## High-Level Architecture Flow

The following Mermaid diagram illustrates how data flows through the application, from client requests down to the database level.

```mermaid
flowchart TD
    %% Define Styles
    classDef controller fill:#4a90e2,stroke:#333,stroke-width:2px,color:#fff,rx:5px,ry:5px
    classDef service fill:#f39c12,stroke:#333,stroke-width:2px,color:#fff,rx:5px,ry:5px
    classDef repository fill:#27ae60,stroke:#333,stroke-width:2px,color:#fff,rx:5px,ry:5px
    classDef entity fill:#8e44ad,stroke:#333,stroke-width:2px,color:#fff,rx:5px,ry:5px
    classDef security fill:#e74c3c,stroke:#333,stroke-width:2px,color:#fff,rx:5px,ry:5px
    classDef client fill:#34495e,stroke:#333,stroke-width:2px,color:#fff,rx:5px,ry:5px
    classDef db fill:#bdc3c7,stroke:#333,stroke-width:2px,color:#2c3e50,shape:cylinder

    %% Nodes
    Client[Client Applications<br>React/Mobile]:::client
    WebSocket[WebSocket/STOMP<br>Real-time]:::client
    
    subgraph Security Layer
        JwtFilter[JwtAuthenticationFilter]:::security
        WsAuth[WebSocketAuthInterceptor]:::security
        WsEvent[WebSocketEventListener]:::security
    end
    
    subgraph Controllers [Controllers - API Endpoints]
        AuthController:::controller
        ChatController:::controller
        VoiceRoomController:::controller
        SocialController:::controller
        PresenceController:::controller
    end
    
    subgraph Services [Services - Business Logic]
        AuthService:::service
        ChatService:::service
        VoiceRoomService:::service
        SocialService:::service
        PresenceService:::service
    end
    
    subgraph Repositories [Repositories - Data Access Layer]
        UserRepository:::repository
        ChatMessageRepository:::repository
        VoiceRoomRepository:::repository
        FriendshipRepository:::repository
    end
    
    subgraph Entities [Entities - Database Models]
        User:::entity
        ChatMessage:::entity
        VoiceRoom:::entity
        Friendship:::entity
    end
    
    Database[(PostgreSQL/MySQL)]:::db
    Redis[(Redis Cache)]:::db

    %% Relationships
    Client -->|HTTP REST| JwtFilter
    WebSocket -->|WS Upgrade| WsAuth
    
    JwtFilter --> Controllers
    WsAuth --> ChatController
    WsEvent --> PresenceService
    
    AuthController --> AuthService
    ChatController --> ChatService
    VoiceRoomController --> VoiceRoomService
    SocialController --> SocialService
    PresenceController --> PresenceService
    
    AuthService --> UserRepository
    ChatService --> ChatMessageRepository
    VoiceRoomService --> VoiceRoomRepository
    SocialService --> FriendshipRepository
    
    ChatService -.-> PresenceService
    PresenceService -.-> Redis
    
    UserRepository --> User
    ChatMessageRepository --> ChatMessage
    VoiceRoomRepository --> VoiceRoom
    FriendshipRepository --> Friendship
    
    Entities --> Database
```

## Directory Structure

Here is the structural mapping of the `src` directory visually represented as a mind map:

```mermaid
mindmap
  root((src))
    main
      java
        com
          gaur
            friendstogether
              FriendstogetherApplication
              config
                CorsConfig
                RedisConfig
                S3Config
              controller
                Endpoints for Auth, Chat, Rooms
              dto
                request
                response
              entity
                Database Models
              exception
                GlobalExceptionHandler
              repository
                Data JPA Repositories
              security
                config
                  Security Configuration
              service
                Business Logic
              util
                Helpers and Utilities
      resources
        application_properties("application.properties")
        static
        templates
```
