# ============================================================================
#      FRIENDSTOGETHER — WEBSOCKET STOMP CHAT COMPLETE TESTING GUIDE
#      Login se lekar Chat Message tak — Har ek step in depth
#      Created: 2026-04-06
# ============================================================================


# ============================================================================
#  TABLE OF CONTENTS
# ============================================================================
#
#  1. ARCHITECTURE OVERVIEW — Poora flow samjho
#  2. PRE-REQUISITES — Kya kya chahiye testing se pehle
#  3. STEP 1: Register Users (HTTP POST)
#  4. STEP 2: Verify OTP (HTTP POST)
#  5. STEP 3: Login → Get JWT Token (HTTP POST)
#  6. STEP 4: WebSocket Connection Open Karo
#  7. STEP 5: STOMP CONNECT Frame + JWT Authentication
#  8. STEP 6: SUBSCRIBE to Message Queue
#  9. STEP 7: SEND Chat Message
# 10. STEP 8: Database Mein Verify Karo
# 11. IMPORTANT CONCEPTS — JWT, STOMP, Principal explained
# 12. SERVER LOGS — Kya dikhna chahiye console mein
# 13. TROUBLESHOOTING — Common errors & fixes
# 14. STOMP FRAME FORMAT — Rules & Gotchas
# 15. API & FRAME QUICK REFERENCE TABLE
#
# ============================================================================


# ============================================================================
#  1. ARCHITECTURE OVERVIEW
# ============================================================================

## Poora Flow Samjho — Client se Database tak

Jab ek user doosre user ko message bhejta hai, ye complete journey hoti hai:

```
CLIENT (Apidog/Browser)
    |
    |--- HTTP GET ws://localhost:8080/ws  (WebSocket Handshake)
    |        |
    |        v
    |    [Spring Security Filter] → /ws/** permitAll() → Handshake OK
    |        |
    |        v
    |    [101 Switching Protocols] → WebSocket Connection Open
    |
    |--- STOMP CONNECT Frame + "Authorization: Bearer <JWT>"
    |        |
    |        v
    |    [WebSocketAuthInterceptor.java]
    |        |--- JWT Token nikaalo header se
    |        |--- JwtUtil.getEmailFromToken(token)
    |        |--- UserDetailsService.loadUserByUsername(email)
    |        |--- accessor.setUser(authToken)  ← ★ PRINCIPAL SET HOTA HAI ★
    |        |
    |        v
    |    CONNECTED frame wapas client ko
    |
    |--- STOMP SEND Frame → destination: /app/chat.sendMessage
    |        |
    |        v
    |    [WebSocketAuthInterceptor.java] → Log: SEND frame aaya
    |        |
    |        v
    |    [STOMP Message Broker] → /app prefix hata ke → /chat.sendMessage
    |        |
    |        v
    |    [ChatController.java] → @MessageMapping("/chat.sendMessage")
    |        |--- principal.getName() → sender email
    |        |--- chatService.saveMessage()
    |        |
    |        v
    |    [ChatService.java]
    |        |--- Sender find by email
    |        |--- Receiver find by userId
    |        |--- ChatRoom create/update
    |        |--- ChatMessage build & save to DB
    |        |
    |        v
    |    [ChatController.java]
    |        |--- messagingTemplate.convertAndSendToUser(receiverId, "/queue/messages", savedMsg)
    |        |
    |        v
    |    [STOMP Broker] → /user/{receiverId}/queue/messages
    |        |
    |        v
    |    RECEIVER CLIENT (agar subscribed hai toh message milega)
```


## Kaunsi File Kya Karti Hai?

| FILE                          | ROLE                                              |
|-------------------------------|---------------------------------------------------|
| SecurityConfig.java           | HTTP level security — /ws/** ko permitAll karta hai|
| WebSocketConfig.java          | STOMP endpoints register, broker config, interceptor register |
| WebSocketAuthInterceptor.java | STOMP CONNECT frame mein JWT check → Principal set |
| ChatController.java           | @MessageMapping se message receive → save → deliver|
| ChatService.java              | Business logic — DB mein room & message save       |
| JwtUtil.java                  | JWT token generate/validate/parse                  |
| ApplicationConfig.java        | UserDetailsService bean define karta hai            |


## HTTP vs WebSocket — Key Difference

| HTTP REQUEST                    | WEBSOCKET / STOMP FRAME                     |
|---------------------------------|---------------------------------------------|
| Har request independent hai     | Ek baar connection, phir frames chalte hain |
| Authorization header har req me | SIRF CONNECT frame mein JWT bhejte hain     |
| JwtAuthenticationFilter kaam    | WebSocketAuthInterceptor kaam karta hai     |
| SecurityFilterChain covers      | SecurityFilterChain sirf handshake tak hai   |
| Request-Response model          | Bi-directional, real-time                   |


# ============================================================================
#  2. PRE-REQUISITES
# ============================================================================

## Testing se Pehle Kya Ready Hona Chahiye:

1. SERVER RUNNING:
   - Spring Boot application `localhost:8080` par run ho rahi ho
   - Command: mvnw.cmd spring-boot:run
   - Ya IDE mein Run karo (IntelliJ/Eclipse)

2. DATABASE CONNECTED:
   - MySQL ya PostgreSQL running & connected
   - application.properties mein sahi DB credentials
   - Tables auto-create hongi (spring.jpa.hibernate.ddl-auto=update)

3. TESTING TOOL:
   - ★ APIDOG (STRONGLY RECOMMENDED) — Isme STOMP mode built-in hai
   - Postman bhi chal sakta hai par STOMP frames manually banana padega
   - Postman mein NULL byte (\0) ka issue aata hai STOMP frames mein

4. MINIMUM 2 USERS:
   - Ek Sender (message bhejega)
   - Ek Receiver (message receive karega)
   - Dono registered + OTP verified + enabled hone chahiye

5. RECEIVER KA userId:
   - Ye 9-digit unique number hai (e.g., "739460929")
   - Database ke `users` table mein `user_id` column mein milega
   - Ye email NAHI hai — ye alag identifier hai


# ============================================================================
#  3. STEP 1: REGISTER TWO USERS
# ============================================================================

## Step 1A — Register User A (Sender)

Tool:     Postman / Apidog
Method:   POST
URL:      http://localhost:8080/api/auth/register
Headers:  Content-Type: application/json

Request Body:
```json
{
    "username": "SenderUser",
    "email": "sender@example.com",
    "password": "sender123"
}
```

Expected Response (200 OK):
```
OTP sent to sender@example.com
```

Kya Hota Hai Backend Mein:
- User entity create hoti hai DB mein (isEnabled = false)
- Random 6-digit OTP generate hota hai
- OTP email par bhi jata hai (agar email service configured hai)
- OTP database ke `users` table mein `otp` column mein bhi save hota hai

Request Body Fields Explanation:
- username: 3-20 characters, sirf letters + numbers, no special chars
- email:    Valid email format zaroori (@ wala)
- password: Minimum 6 characters


## Step 1B — Register User B (Receiver)

Tool:     Postman / Apidog
Method:   POST
URL:      http://localhost:8080/api/auth/register
Headers:  Content-Type: application/json

Request Body:
```json
{
    "username": "ReceiverUser",
    "email": "receiver@example.com",
    "password": "receiver123"
}
```

Expected Response (200 OK):
```
OTP sent to receiver@example.com
```


# ============================================================================
#  4. STEP 2: VERIFY OTP (DONO USERS KE LIYE)
# ============================================================================

## OTP Kahan Se Milega?

Option 1: Email inbox check karo (agar email service configured hai)
Option 2: Database se directly nikalo:
          SELECT otp, email FROM users WHERE email = 'sender@example.com';
Option 3: Console/server logs mein print ho sakta hai


## Step 2A — Verify User A (Sender)

Tool:     Postman / Apidog
Method:   POST
URL:      http://localhost:8080/api/auth/verify-otp
Headers:  Content-Type: application/json

Request Body:
```json
{
    "email": "sender@example.com",
    "otp": "123456"
}
```

NOTE: "123456" ki jagah ACTUAL OTP daalo jo email/DB se mila!

Expected Response (200 OK):
```
Email verified successfully!
```

Kya Hota Hai Backend Mein:
- User ka `isEnabled` field `true` ho jata hai
- Ab ye user login kar sakta hai
- OTP expire check bhi hota hai (otpExpiry se compare)


## Step 2B — Verify User B (Receiver)

Same process — receiver@example.com ke liye bhi OTP verify karo.


# ============================================================================
#  5. STEP 3: LOGIN → GET JWT TOKEN
# ============================================================================

## Step 3A — Login as User A (Sender)

Tool:     Postman / Apidog
Method:   POST
URL:      http://localhost:8080/api/auth/login
Headers:  Content-Type: application/json

Request Body:
```json
{
    "email": "sender@example.com",
    "password": "sender123"
}
```

Expected Response (200 OK):
```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzZW5kZXJAZXhhbXBsZS5jb20iLCJpYXQiOjE3NDkz...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "username": "SenderUser"
}
```

★★★ BAHUT ZAROORI ★★★
→ "accessToken" ki value COPY karo aur kahi NOTEPAD mein paste karo!
→ Ye token Step 5 (STOMP CONNECT) mein use hoga!
→ Token mein 3 parts hote hain dot (.) se separated: header.payload.signature


## Step 3B — Login as User B (Receiver)

Same process — receiver@example.com se login karo
Agar receiver side bhi test karna ho (message receive dekhna ho) toh receiver ka bhi token save karo


## Step 3.5 — Receiver ka userId Note Karo

Message bhejne ke liye receiverId chahiye. Ye 9-digit userId hai, email NAHI.

Database se nikalo:
```sql
SELECT user_id, email, username FROM users WHERE email = 'receiver@example.com';
```

Result example:
| user_id   | email                | username     |
|-----------|----------------------|--------------|
| 739460929 | receiver@example.com | ReceiverUser |

→ "739460929" ye hai tumhara receiverId — ise yaad rakho Step 7 ke liye!


# ============================================================================
#  6. STEP 4: WEBSOCKET CONNECTION OPEN KARO
# ============================================================================

Ab HTTP requests khatam ho gayi hain.
Ab se sab kuch WebSocket protocol par hoga.

## Apidog Mein WebSocket Connection:

1. Apidog kholo
2. New Request → WebSocket Request select karo
3. URL daalo: ws://localhost:8080/ws
4. "Connect" button dabao

## Postman Mein WebSocket Connection:

1. Postman kholo
2. New → WebSocket Request
3. URL: ws://localhost:8080/ws
4. "Connect" press karo

Expected Result:
- Status bar mein "Connected" dikhega
- Console mein koi error nahi aayega
- Ye sirf TCP/WebSocket connection hai — abhi STOMP authentication NAHI hua!

Server Log:
```
🟢 [WS-CONFIG] STOMP Endpoint registered: /ws
```
(Ye startup par ek baar aata hai)


# ============================================================================
#  7. STEP 5: STOMP CONNECT FRAME + JWT AUTHENTICATION
# ============================================================================

## ★★★ YE SABSE IMPORTANT STEP HAI! ★★★

WebSocket connection open hone ke baad, tumhe STOMP CONNECT frame bhejni hai
JISMEIN JWT Token hoga. Bina iske Principal null rahega aur message process
nahi hoga.

## Apidog STOMP Mode Mein:

Agar Apidog mein STOMP mode available hai:
1. Message Type: STOMP
2. Command: CONNECT
3. Headers section mein add karo:
   - Key:   Authorization
   - Value: Bearer eyJhbGciOiJIUzI1NiJ9.PASTE_YOUR_TOKEN_HERE
   - Key:   accept-version
   - Value: 1.2
   - Key:   heart-beat
   - Value: 10000,10000
4. Send karo


## Raw STOMP Frame (Manual — Postman ya Raw WebSocket):

Agar tum raw text bhej rahe ho, toh EXACT ye format hona chahiye:

```
CONNECT
Authorization:Bearer eyJhbGciOiJIUzI1NiJ9.PASTE_YOUR_ACTUAL_TOKEN_HERE
accept-version:1.2
heart-beat:10000,10000

\0
```

## ★ RAW FRAME KE RULES (BAHUT ZAROORI): ★

Rule 1: PEHLI LINE mein command hona chahiye: "CONNECT" (all caps)

Rule 2: Uske baad headers, ek per line:
         HeaderName:HeaderValue
         (colon ke turant baad value, beech mein space NAHI)

Rule 3: "Authorization:Bearer " — yahan "Bearer" ke BAAD ek space hai,
         phir token. Par "Authorization" aur ":" ke beech space NAHI.
         GALAT: Authorization : Bearer token
         GALAT: Authorization: Bearer token
         SAHI:  Authorization:Bearer eyJhbG...

Rule 4: Headers ke baad EK BLANK LINE zaroori hai (body se pehle)
         Ye blank line STOMP protocol ka hissa hai — iske bina frame parse
         nahi hoga

Rule 5: Frame ke EKDUM END mein NULL byte (\0) zaroori hai
         - Ye Unicode character \u0000 hai
         - Ye "backslash zero" text NAHI hai
         - Apidog STOMP mode mein ye AUTOMATIC lagta hai
         - Postman mein ye manually add karna mushkil hai — isliye Apidog recommended


## Expected Server Logs (Console mein dikhenge):

```
🔌 [WS-AUTH] STOMP CONNECT frame receive hua — Authentication shuru...
🔑 [WS-AUTH] JWT Token mila — Validation shuru ho rahi hai...
📧 [WS-AUTH] Token se email nikala: sender@example.com
✅ [WS-AUTH] WebSocket AUTHENTICATED! User: sender@example.com | Authorities: [USER]
```


## Expected Response (Server se client ko):

```
CONNECTED
version:1.2
heart-beat:0,0

\0
```

## Kaise Confirm Karein Ki Auth Successful Hua?

✅ SUCCESS agar:
   - "CONNECTED" frame wapas aaya
   - Server log mein "✅ [WS-AUTH] WebSocket AUTHENTICATED!" dikha

❌ FAIL agar:
   - Connection drop ho gaya
   - Server log mein "❌ [WS-AUTH]" wali koi line dikhi
   - "ERROR" frame wapas aaya



# ============================================================================
#  8. STEP 6: SUBSCRIBE TO MESSAGE QUEUE (OPTIONAL PAR RECOMMENDED)
# ============================================================================

## Kab Zaroori Hai?

Ye step SENDER ke liye zaroori NAHI hai.
Ye tab zaroori hai jab tum RECEIVER SIDE test karna chahte ho — dekhna ki
kya message real-time aata hai ya nahi.

## Concept:
Jab server `messagingTemplate.convertAndSendToUser(receiverId, "/queue/messages", msg)`
call karta hai, toh message ye path par jaata hai:
    /user/{receiverId}/queue/messages

Receiver ko wo message tabhi milega jab usne pehle se is path ko SUBSCRIBE kiya ho.

## Receiver Client Se SUBSCRIBE Frame:

Pehle receiver ka bhi WebSocket connection open karo (Step 4)
Phir STOMP CONNECT karo receiver ke JWT token ke saath (Step 5)
Phir ye SUBSCRIBE frame bhejo:

### Raw STOMP Frame:
```
SUBSCRIBE
id:sub-0
destination:/user/queue/messages

\0
```

### Apidog STOMP Mode:
- Command: SUBSCRIBE
- Headers:
  - id: sub-0
  - destination: /user/queue/messages

### Server Log:
```
📥 [WS-SUBSCRIBE] User: receiver@example.com subscribed to: /user/queue/messages
```

### Notes:
- "id:sub-0" ye subscription ka unique ID hai — kuch bhi rakh sakte ho
- "/user/queue/messages" — yahan "/user" prefix Spring automatically handle karta hai
  based on authenticated user. Toh receiver ko apna userId daalne ki zaroorat NAHI.
- Spring internally is destination ko convert karta hai:
  /user/queue/messages → /user/{receiverUserId}/queue/messages


# ============================================================================
#  9. STEP 7: SEND CHAT MESSAGE — ★ THE MAIN EVENT! ★
# ============================================================================

Ab SENDER client se message bhejo!

## Apidog STOMP Mode:

- Command: SEND
- Headers:
  - destination: /app/chat.sendMessage
  - content-type: application/json
- Body:
```json
{"receiverId":"739460929","content":"Hello bhai, kya haal hai?","type":"TEXT"}
```

NOTE: "739460929" ki jagah apna ACTUAL receiver ka userId daalo (Step 3.5 se)


## Raw STOMP Frame (Manual):

```
SEND
destination:/app/chat.sendMessage
content-type:application/json

{"receiverId":"739460929","content":"Hello bhai, kya haal hai?","type":"TEXT"}\0
```


## Frame Breakdown:

Line 1: "SEND" — STOMP command
Line 2: "destination:/app/chat.sendMessage"
         - "/app" ye ApplicationDestinationPrefix hai (WebSocketConfig mein set)
         - Spring "/app" hata ke baaki "/chat.sendMessage" to @MessageMapping match karega
         - ChatController mein @MessageMapping("/chat.sendMessage") hai
Line 3: "content-type:application/json"
         - Ye zaroori hai — iske bina Spring JSON payload parse nahi kar payega
         - Bina iske ChatRequest object null fields ke saath aayega
Line 4: (BLANK LINE) — Headers aur body ka separator
Line 5: JSON body — ChatRequest DTO ke fields ke saath
         - receiverId: String — receiver ka 9-digit userId
         - content:    String — message text
         - type:       String — "TEXT", "IMAGE", "VIDEO", "AUDIO", "FILE"
\0:     NULL byte — frame terminator


## Destination Path Ka Flow:

Client sends to:    /app/chat.sendMessage
                      |
Spring removes       /app (prefix)
                      |
Remaining:          /chat.sendMessage
                      |
Matches:            @MessageMapping("/chat.sendMessage") in ChatController
                      |
Method called:      processMessage(@Payload ChatRequest, Principal principal)


## Expected Server Logs (FULL FLOW — In Order):

```
📤 [WS-SEND] SEND frame aaya | User: sender@example.com | Destination: /app/chat.sendMessage
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📩 [CHAT-CTRL] Naya message RECEIVE hua @MessageMapping par!
📩 [CHAT-CTRL] Payload: receiverId=739460929, content='Hello bhai, kya haal hai?', type=TEXT
👤 [CHAT-CTRL] Sender identified: sender@example.com
📨 [CHAT-CTRL] Receiver ID: 739460929
💾 [CHAT-CTRL] ChatService.saveMessage() call ho raha hai...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💾 [CHAT-SVC] saveMessage() START — senderEmail: sender@example.com, receiverId: 739460929
🔍 [CHAT-SVC] Database mein Sender dhoondh rahe hain: sender@example.com
✅ [CHAT-SVC] Sender mila: sender@example.com (userId: 123456789)
🔍 [CHAT-SVC] Database mein Receiver dhoondh rahe hain: 739460929
✅ [CHAT-SVC] Receiver mila: receiver@example.com (userId: 739460929)
🏠 [CHAT-SVC] Room ID generate hua: 123456789_739460929
🔍 [CHAT-SVC] ChatRoom check ho raha hai — roomId: 123456789_739460929
🆕 [CHAT-SVC] Room NAHI MILA — Naya room create ho raha hai...
✅ [CHAT-SVC] Naya ChatRoom CREATED — roomId: 123456789_739460929
📝 [CHAT-SVC] ChatMessage build ho raha hai...
✅ [CHAT-SVC] Message DATABASE MEIN SAVE HO GAYA!
   ➡️ Message ID: 1
   ➡️ Room ID: 123456789_739460929
   ➡️ Sender: sender@example.com → Receiver: receiver@example.com
   ➡️ Content: 'Hello bhai, kya haal hai?'
   ➡️ Type: TEXT | Status: SENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ [CHAT-CTRL] Message saved with ID: 1 | RoomId: 123456789_739460929
📡 [CHAT-CTRL] Real-time delivery shuru — Target: /user/739460929/queue/messages
✅ [CHAT-CTRL] Message DELIVERED to user queue: /user/739460929/queue/messages
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```


## Agar Dusre Client (Receiver) Par Test Karna Hai:

Agar receiver client Step 6 mein subscribe kar chuka hai, toh use ye message
automatically milega as a STOMP MESSAGE frame:

```
MESSAGE
subscription:sub-0
destination:/user/queue/messages
content-type:application/json

{"id":1,"roomId":"123456789_739460929","content":"Hello bhai...","type":"TEXT","status":"SENT",...}\0
```


# ============================================================================
# 10. STEP 8: DATABASE MEIN VERIFY KARO
# ============================================================================

## Chat Messages Table Check:

SQL Query:
```sql
SELECT id, room_id, sender_id, receiver_id, content, type, status, timestamp
FROM chat_messages
ORDER BY id DESC
LIMIT 5;
```

Expected Result:
| id | room_id             | sender_id | receiver_id | content                    | type | status | timestamp           |
|----|---------------------|-----------|-------------|----------------------------|------|--------|---------------------|
| 1  | 123456789_739460929 | 1         | 2           | Hello bhai, kya haal hai? | TEXT | SENT   | 2026-04-06 23:45:00 |


## Chat Rooms Table Check:

SQL Query:
```sql
SELECT id, room_id, user1_id, user2_id, last_message, last_message_time
FROM chat_rooms
ORDER BY id DESC
LIMIT 5;
```

Expected Result:
| id | room_id             | user1_id | user2_id | last_message               | last_message_time   |
|----|---------------------|----------|----------|-----------------------------|---------------------|
| 1  | 123456789_739460929 | 1        | 2        | Hello bhai, kya haal hai?  | 2026-04-06 23:45:00 |


## Room ID Kaise Banta Hai?

ChatUtils.generateRoomId("123456789", "739460929")
→ Dono userIds ko sort karta hai alphabetically
→ Underscore (_) se join karta hai
→ Result: "123456789_739460929"
→ Ye ensures ki A→B aur B→A same room mein jayenge


# ============================================================================
# 11. IMPORTANT CONCEPTS EXPLAINED
# ============================================================================

## JWT Token Kya Hai?

JWT = JSON Web Token
- Ye ek encoded string hai jo 3 parts mein hoti hai: HEADER.PAYLOAD.SIGNATURE
- Login karne par server generate karta hai
- Client har authenticated request ke saath ye token bhejta hai
- Server token se user identity (email) nikaalta hai
- Token expire hota hai — phir naya login karna padta hai

Example Token:
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzZW5kZXJAZXhhbXBsZS5jb20iLCJpYXQiOjE3NDkzNT...
|---- Header ----|---------- Payload ------------|----------- Signature ---------|


## STOMP Protocol Kya Hai?

STOMP = Simple Text Oriented Messaging Protocol
- WebSocket ke upar ek messaging layer hai
- WebSocket sirf raw bytes bhejta hai — STOMP ise structured banata hai
- Commands: CONNECT, CONNECTED, SEND, SUBSCRIBE, MESSAGE, DISCONNECT, ERROR
- Har frame mein: COMMAND + HEADERS + BLANK LINE + BODY + NULL BYTE


## Principal Kya Hai?

- Java mein `java.security.Principal` interface hai
- Ye represent karta hai "currently authenticated user"
- HTTP mein: JwtAuthenticationFilter set karta hai (SecurityContextHolder mein)
- WebSocket STOMP mein: WebSocketAuthInterceptor set karta hai (accessor.setUser())
- ChatController mein: `principal.getName()` → returns user email
- Agar Principal null hai → user authenticated NAHI hai


## HTTP Security vs WebSocket Security:

| Aspect           | HTTP Requests              | WebSocket STOMP                |
|------------------|----------------------------|--------------------------------|
| Filter/Interceptor| JwtAuthenticationFilter   | WebSocketAuthInterceptor       |
| Token Location   | Authorization header (each req) | CONNECT frame header (once) |
| Where Principal set | SecurityContextHolder   | StompHeaderAccessor.setUser()  |
| Valid For        | Single request              | Entire WebSocket session       |
| Config File      | SecurityConfig.java         | WebSocketConfig.java           |


# ============================================================================
# 12. SERVER LOGS — KYA DIKHNA CHAHIYE CONSOLE MEIN
# ============================================================================

## Application Startup Logs:
```
🟢 [WS-CONFIG] STOMP Endpoint registered: /ws
🟢 [WS-CONFIG] Allowed Origins: * (Production mein restrict karna!)
🟢 [WS-CONFIG] Message Broker configured:
   ➡️ App Destination Prefix: /app
   ➡️ Simple Broker: /topic, /queue, /user
   ➡️ User Destination Prefix: /user
🟢 [WS-CONFIG] WebSocketAuthInterceptor REGISTERED on inbound channel
🟢 [WS-CONFIG] Ab har STOMP CONNECT frame authenticate hoga!
```

## STOMP CONNECT (Authentication) Logs:
```
🔌 [WS-AUTH] STOMP CONNECT frame receive hua — Authentication shuru...
🔑 [WS-AUTH] JWT Token mila — Validation shuru ho rahi hai...
📧 [WS-AUTH] Token se email nikala: sender@example.com
✅ [WS-AUTH] WebSocket AUTHENTICATED! User: sender@example.com | Authorities: [USER]
```

## STOMP CONNECT Error Logs:
```
❌ [WS-AUTH] CONNECT frame mein Authorization header NAHI mila ya format galat hai!
❌ [WS-AUTH] Expected format: 'Authorization: Bearer <JWT_TOKEN>'
```
ya:
```
❌ [WS-AUTH] Token INVALID hai ya email null mila! Email: null
```

## STOMP SEND (Message) Logs:
```
📤 [WS-SEND] SEND frame aaya | User: sender@example.com | Destination: /app/chat.sendMessage
```

## STOMP SUBSCRIBE Logs:
```
📥 [WS-SUBSCRIBE] User: receiver@example.com subscribed to: /user/queue/messages
```

## STOMP DISCONNECT Logs:
```
🔴 [WS-DISCONNECT] User disconnected: sender@example.com
```

## ChatController Logs:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📩 [CHAT-CTRL] Naya message RECEIVE hua @MessageMapping par!
📩 [CHAT-CTRL] Payload: receiverId=739460929, content='Hello', type=TEXT
👤 [CHAT-CTRL] Sender identified: sender@example.com
💾 [CHAT-CTRL] ChatService.saveMessage() call ho raha hai...
✅ [CHAT-CTRL] Message saved with ID: 1 | RoomId: 123456789_739460929
📡 [CHAT-CTRL] Real-time delivery shuru — Target: /user/739460929/queue/messages
✅ [CHAT-CTRL] Message DELIVERED to user queue: /user/739460929/queue/messages
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## ChatService Logs:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💾 [CHAT-SVC] saveMessage() START — senderEmail: sender@example.com, receiverId: 739460929
🔍 [CHAT-SVC] Database mein Sender dhoondh rahe hain: sender@example.com
✅ [CHAT-SVC] Sender mila: sender@example.com (userId: 123456789)
🔍 [CHAT-SVC] Database mein Receiver dhoondh rahe hain: 739460929
✅ [CHAT-SVC] Receiver mila: receiver@example.com (userId: 739460929)
🏠 [CHAT-SVC] Room ID generate hua: 123456789_739460929
🆕 [CHAT-SVC] Room NAHI MILA — Naya room create ho raha hai...
✅ [CHAT-SVC] Naya ChatRoom CREATED — roomId: 123456789_739460929
✅ [CHAT-SVC] Message DATABASE MEIN SAVE HO GAYA!
   ➡️ Message ID: 1
   ➡️ Room ID: 123456789_739460929
   ➡️ Sender: sender@example.com → Receiver: receiver@example.com
   ➡️ Content: 'Hello'
   ➡️ Type: TEXT | Status: SENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```


# ============================================================================
# 13. TROUBLESHOOTING — COMMON ERRORS & FIXES
# ============================================================================

## Problem 1: WebSocket Connection Fail — Can't connect to ws://localhost:8080/ws

Possible Causes & Solutions:
a) Server nahi chal raha → `mvnw.cmd spring-boot:run` karo
b) Port galat hai → application.properties mein server.port=8080 check karo
c) Firewall block kar raha → Port 8080 allow karo
d) URL galat hai → Exactly "ws://localhost:8080/ws" hona chahiye (http nahi, ws)


## Problem 2: CONNECT ke baad connection drop / ERROR frame

Possible Causes & Solutions:
a) Token nahi bheja → CONNECT frame mein "Authorization:Bearer <token>" daalo
b) Token expire ho gaya → Dubara login karo, naya token lo
c) Token format galat:
   GALAT: "Bearer: token" (extra colon)
   GALAT: "bearer token" (lowercase)
   SAHI:  "Bearer eyJhbG..."
d) Server log check karo → "❌ [WS-AUTH]" wali lines mein exact error milega


## Problem 3: CONNECTED milta hai par SEND kaam nahi karta (message process nahi hota)

Possible Causes & Solutions:
a) Destination galat hai:
   GALAT: "/chat.sendMessage" (missing /app prefix)
   GALAT: "/app/chat/sendMessage" (slash instead of dot)
   SAHI:  "/app/chat.sendMessage"

b) Content-Type header missing:
   GALAT: Header nahi daala
   SAHI:  "content-type:application/json"

c) JSON format galat:
   GALAT: {'receiverId': '123'}  (single quotes)
   GALAT: {receiverId: "123"}    (unquoted keys)
   SAHI:  {"receiverId":"123","content":"Hello","type":"TEXT"}

d) NULL byte missing → Frame ke end mein \0 (\u0000) zaroori hai

e) Blank line missing → Headers aur body ke beech blank line zaroori hai


## Problem 4: "Principal NULL HAI" log aa raha hai

Cause: CONNECT frame mein authentication nahi hua
Solutions:
a) Kya CONNECT frame pehle bheja? → SEND se pehle CONNECT zaroori hai
b) CONNECT mein token tha? → Authorization header check karo
c) Server startup log check karo:
   "🟢 [WS-CONFIG] WebSocketAuthInterceptor REGISTERED" dikhna chahiye
   Agar nahi dikhta → WebSocketConfig.java mein configureClientInboundChannel check karo


## Problem 5: "Receiver not found" error

Cause: receiverId galat hai ya user exist nahi karta
Solutions:
a) DB mein check karo: SELECT user_id FROM users;
b) receiverId 9-digit userId hona chahiye, email NAHI
c) Receiver registered + OTP verified hona chahiye


## Problem 6: Message save ho raha hai par receiver ko real-time nahi mil raha

Cause: Receiver ne subscribe nahi kiya hai
Solutions:
a) Receiver client bhi connected hai? (Step 4-5)
b) Receiver ne "/user/queue/messages" subscribe kiya? (Step 6)
c) Receiver ka userId aur receiverId match kar rahe hain?


## Problem 7: Postman mein STOMP frames kaam nahi kar rahe

Cause: Postman mein raw STOMP frame support limited hai
Solution: APIDOG use karo — isme STOMP mode built-in hai
Alternative: Browser mein ek simple HTML page banao with @stomp/stompjs library


# ============================================================================
# 14. STOMP FRAME FORMAT — RULES & GOTCHAS
# ============================================================================

## STOMP Frame Ka Structure:

```
COMMAND
header1:value1
header2:value2

Body content here\0
```

Components:
1. COMMAND  — First line, ALL CAPS (CONNECT, SEND, SUBSCRIBE, etc.)
2. HEADERS  — Key:Value pairs, one per line
3. BLANK LINE — Separator between headers and body (MANDATORY)
4. BODY     — Optional, depends on command
5. NULL BYTE — \0 (\u0000) at the very end (MANDATORY)


## Common STOMP Commands:

| Command    | Direction      | Purpose                              |
|------------|----------------|--------------------------------------|
| CONNECT    | Client → Server| Authentication/Handshake            |
| CONNECTED  | Server → Client| Auth success confirmation            |
| SEND       | Client → Server| Send message to destination          |
| SUBSCRIBE  | Client → Server| Subscribe to receive messages        |
| UNSUBSCRIBE| Client → Server| Stop receiving from destination      |
| MESSAGE    | Server → Client| Deliver message to subscriber        |
| DISCONNECT | Client → Server| Graceful disconnect                  |
| ERROR      | Server → Client| Error notification                   |


## NULL Byte (\0) Explained:

- Ye ek invisible character hai — Unicode U+0000
- STOMP protocol mein har frame ka END marker hai
- Iske bina server ko pata nahi chalta ki frame kahan khatam hua
- Apidog STOMP mode mein: AUTOMATIC lagta hai ✅
- Postman raw mein: Manually add karna padta hai ❌ (mushkil hai)
- Programming mein: "\u0000" ya "\0" likhte hain


## Header Format Rules:

SAHI:   "destination:/app/chat.sendMessage"   (no space after colon)
GALAT:  "destination: /app/chat.sendMessage"  (space after colon — GALAT!)
GALAT:  "destination : /app/chat.sendMessage" (spaces around colon — GALAT!)

Special case for Authorization:
SAHI:   "Authorization:Bearer eyJhbG..."
         (no space after colon, space between "Bearer" and token)


# ============================================================================
# 15. API & FRAME QUICK REFERENCE TABLE
# ============================================================================

## HTTP APIs (Postman/Apidog):

| Step | Method | URL                                | Auth   | Body Fields                        | Response               |
|------|--------|------------------------------------|--------|------------------------------------|------------------------|
| 1    | POST   | /api/auth/register                 | None   | username, email, password          | "OTP sent to..."       |
| 2    | POST   | /api/auth/verify-otp               | None   | email, otp                         | "Email verified..."    |
| 3    | POST   | /api/auth/login                    | None   | email, password                    | {accessToken, ...}     |

## STOMP Frames (WebSocket — ws://localhost:8080/ws):

| Step | Command   | Headers                                       | Body                                                    |
|------|-----------|-----------------------------------------------|---------------------------------------------------------|
| 5    | CONNECT   | Authorization:Bearer <JWT>, accept-version:1.2| (empty)                                                 |
| 6    | SUBSCRIBE | id:sub-0, destination:/user/queue/messages     | (empty)                                                 |
| 7    | SEND      | destination:/app/chat.sendMessage, content-type:application/json | {"receiverId":"...","content":"...","type":"TEXT"} |


## KEY POINTS YAAD RAKHNE WALE:

 1. JWT Token SIRF CONNECT frame mein jaata hai — SEND/SUBSCRIBE mein NAHI
 2. Ek baar CONNECT ho gaya → Poore session mein Principal set rehta hai
 3. /app prefix ZAROORI hai SEND destination mein → /app/chat.sendMessage
 4. content-type:application/json header SEND frame mein ZAROORI hai
 5. Apidog >> Postman for WebSocket STOMP testing
 6. receiverId = 9-digit userId (database se) — email NAHI hai
 7. NULL byte (\0) har STOMP frame ke end mein ZAROORI hai
 8. Headers aur body ke beech EK BLANK LINE zaroori hai
 9. Agar second message same users ke beech bhejo → same room use hoga (update)
10. Room ID sorted hota hai → A_B aur B_A dono same room mein jayenge


# ============================================================================
#  END OF DOCUMENTATION
#  Ab server start karo aur test karo! 🚀
#  Command: mvnw.cmd spring-boot:run
# ============================================================================
