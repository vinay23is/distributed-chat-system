# Distributed Real-Time Chat Platform

Real-time chat built to solve one specific distributed systems problem: WebSocket connections are stateful and bound to a single server. When you scale horizontally, a message sent through instance A never reaches a client connected to instance B — unless you build a cross-instance relay.

This system solves it with Redis Pub/Sub as an event bus between backend instances.

**Stack:** Java 21 · Spring Boot 3.2 · STOMP/WebSocket · PostgreSQL 16 · Redis 7 · Apache Kafka · React 18 + TypeScript · Docker Compose

---

## Architecture

```
                    ┌──────────────────────────────────────────┐
                    │               NGINX :80                  │
                    │    /api/*  →  backend (least_conn)       │
                    │    /ws/*   →  backend (WS upgrade)       │
                    │    /*      →  frontend static            │
                    └──────────────┬───────────────────────────┘
                                   │
                        ┌──────────┴──────────┐
                        │                     │
                 ┌──────▼──────┐       ┌──────▼──────┐
                 │  backend-1  │       │  backend-2  │
                 │  :8080      │       │  :8081      │
                 └──────┬──────┘       └──────┬──────┘
                        │   Redis Pub/Sub      │
                        │   chat:room:*        │
                        │  ◄──────────────────►│
                        └──────────┬───────────┘
                                   │
          ┌────────────────────────┼───────────────────────┐
          │                        │                       │
   ┌──────▼──────┐         ┌───────▼──────┐       ┌───────▼──────┐
   │  PostgreSQL │         │    Redis     │        │    Kafka     │
   │  users      │         │  Pub/Sub     │        │  chat-msgs   │
   │  rooms      │         │  Presence    │        │  chat-notifs │
   │  messages   │         │  Rate limits │        │              │
   │  receipts   │         │  Typing TTLs │        └──────────────┘
   │  notifs     │         └──────────────┘
   └─────────────┘
```

---

## How a message travels across instances

```
User A (on backend-1) sends a message
  │
  ├─ ChatWebSocketController.sendMessage()
  │     checks: room membership, mute status
  │
  ├─ MessageService.sendMessage()
  │     rate-limit check  →  Redis INCR rl:msg:{userId}:{roomId}
  │     persist           →  INSERT INTO messages, message_receipts
  │     publish           →  redisTemplate.convertAndSend("chat:room:{roomId}", event)
  │                                                │
  │                                   Redis broadcasts to ALL subscribers
  │                                                │
  │                             ┌──────────────────┴──────────────────┐
  │                             │                                     │
  │                    backend-1 receives                    backend-2 receives
  │                    RedisMessageSubscriber                RedisMessageSubscriber
  │                    routes by eventType                   routes by eventType
  │                    → /topic/room/{roomId}                → /topic/room/{roomId}
  │
  └─ Kafka (parallel)
        publish → chat-notifications topic
        NotificationKafkaConsumer picks it up asynchronously
        creates Notification rows for offline room members
```

Every backend instance subscribes to `PatternTopic("chat:*")` at startup. Redis delivers a copy to each subscriber. Both instances call `messagingTemplate.convertAndSend()` on their local STOMP broker, delivering to their locally-connected clients. User B, on backend-2, gets the message with the same latency as if they were on the same instance.

---

## WebSocket Authentication

HTTP requests go through a standard `OncePerRequestFilter` that validates the JWT from the `Authorization` header. WebSocket can't use the same filter — the HTTP handshake completes before STOMP frames begin.

Instead, a `ChannelInterceptor` intercepts the STOMP `CONNECT` frame:

```java
if (StompCommand.CONNECT.equals(accessor.getCommand())) {
    String token = accessor.getNativeHeader("Authorization").get(0);
    if (jwtUtil.isValid(token)) {
        String userId = jwtUtil.extractUserId(token);
        accessor.setUser(new UsernamePasswordAuthenticationToken(...));
    }
}
```

The `Principal` set here persists for the lifetime of the WebSocket session. Every `@MessageMapping` handler receives the authenticated user via `SimpMessageHeaderAccessor.getUser()` — no per-frame token validation.

---

## Redis Usage

**Pub/Sub** — the cross-instance relay. `RedisMessageListenerContainer` subscribes to `chat:*` on startup. Publishing to `chat:room:{roomId}` from any instance triggers `RedisMessageSubscriber.onMessage()` on all instances, which routes to the correct STOMP topic based on `eventType`.

**Presence:**

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `presence:online` | SET | — | Global set of online user IDs |
| `presence:hb:{userId}` | STRING | 30s | Refreshed by client heartbeat every 20s |
| `presence:room:{roomId}` | SET | — | Users currently viewing a room |

If a client disconnects without a clean `DISCONNECT` (browser killed, network drop), `presence:hb:{userId}` expires in 30 seconds. `SessionDisconnectEvent` handles clean disconnects immediately.

**Typing indicators:**

| Key | Type | TTL |
|---|---|---|
| `typing:{roomId}:{userId}` | STRING | 5s |

Set on `TYPING_START`, auto-expires after 5 seconds. No explicit `TYPING_STOP` cleanup is required — if the user stops typing or disconnects, the key expires on its own. No "stuck typing" indicators.

**Rate limiting:**

```java
Long count = redisTemplate.opsForValue().increment(key);  // atomic INCR
if (count == 1) redisTemplate.expire(key, window);        // set TTL on first hit
if (count > maxRequests) throw new ResponseStatusException(429, ...);
```

| Action | Limit | Window |
|---|---|---|
| Sending messages | 5 | per 10 seconds per user per room |
| Login attempts | 20 | per 15 minutes per IP |
| Room creation | 10 | per hour per user |

---

## Database

```sql
users         (id uuid pk, name, email unique, password_hash, avatar_url, created_at, last_seen_at)
rooms         (id uuid pk, name, description, type, created_by → users, created_at)
room_members  (id uuid pk, room_id → rooms, user_id → users, role, joined_at, muted_until)
messages      (id uuid pk, room_id → rooms, sender_id → users, content text,
               message_type, created_at, edited_at, deleted_at, pinned_at)
message_receipts (id uuid pk, message_id → messages, user_id → users, status, updated_at)
notifications (id uuid pk, user_id → users, room_id, message_id, type, body, is_read, created_at)
```

**Indexes on `messages`:**
```sql
(room_id, created_at DESC)   -- primary read path: paginated history
(sender_id)                  -- queries by sender
(room_id, content)           -- full-text search within a room
```

**Cursor pagination** — history is fetched with `WHERE created_at < :cursor ORDER BY created_at DESC LIMIT 50`. Initial load uses `Instant.now() + 1s` as the cursor. Loading older messages passes the oldest visible message's `created_at`. This hits the `(room_id, created_at DESC)` index at `O(log N)` regardless of history depth, unlike `OFFSET` which degrades linearly.

**Soft delete** — `deleted_at` is set; content stays in the database for receipt/notification integrity. The `MESSAGE_DELETED` event carries only the `messageId`; the frontend renders a placeholder without re-fetching.

---

## Kafka

Kafka handles notification generation — a separate concern from real-time delivery.

Redis Pub/Sub is fire-and-forget: if a subscriber is momentarily down, the event is lost. That's fine for real-time delivery (the message is already in PostgreSQL; the client reloads on reconnect). For notifications it isn't — a user offline for 10 minutes should still receive them. Kafka's durable log lets the consumer restart and replay from its last committed offset.

The producer sets `spring.json.add.type.headers: false` (no `__TypeId__` header in messages). The consumer uses `ErrorHandlingDeserializer` wrapping `JsonDeserializer` with `spring.json.value.default.type: com.chat.platform.kafka.ChatEvent` as the fallback. A malformed message is logged and skipped; the consumer container doesn't crash.

---

## Kafka + Zookeeper restart behavior

On container restart, Kafka re-registers broker ID 1 in Zookeeper. If the previous session's ephemeral node (`/brokers/ids/1`) hasn't expired yet, Kafka crashes with `KeeperErrorCode = NodeExists`. Two mitigations are in place: `KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS: 6000` shortens the ephemeral node TTL, and `restart: on-failure` in docker-compose retries after the node expires naturally.

---

## Frontend

State is managed with Zustand (`useChatStore`). The WebSocket connection lives in a React context (`WebSocketContext`) that creates a single STOMP client per login session and exposes a reactive `connected: boolean` state.

**Race condition:** The STOMP handshake takes 100–500ms. If a user navigates to a room before the connection is ready, a naive implementation skips the subscription silently. The fix uses two separate effects in `ChatWindow`:

```typescript
// runs when room changes — HTTP only, no WS dependency
useEffect(() => {
  if (!activeRoomId) return
  messagesApi.getHistory(activeRoomId).then(...)
}, [activeRoomId])

// runs when room OR connection state changes
useEffect(() => {
  if (!activeRoomId || !connected) return
  return subscribeToRoom(activeRoomId)  // returns unsubscribe cleanup
}, [activeRoomId, connected, subscribeToRoom])
```

When `connected` flips to `true` after the handshake, the subscription effect re-fires automatically.

---

## WebSocket Protocol

**Client → Server (`/app/...`):**

| Destination | Payload |
|---|---|
| `/app/chat/{roomId}` | `{ content, messageType? }` |
| `/app/typing/{roomId}` | `{ typing: boolean }` |
| `/app/room/{roomId}/join` | `{}` |
| `/app/room/{roomId}/leave` | `{}` |
| `/app/read/{roomId}` | `{}` |
| `/app/heartbeat` | `{}` |

**Server → Client (subscribe):**

| Destination | Content |
|---|---|
| `/topic/room/{roomId}` | `ChatEvent` — MESSAGE_SENT, MESSAGE_EDITED, MESSAGE_DELETED |
| `/topic/room/{roomId}/typing` | `ChatEvent` — TYPING_START, TYPING_STOP |
| `/topic/room/{roomId}/presence` | `{ userId, event: JOINED\|LEFT }` |
| `/topic/room/{roomId}/receipts` | `ChatEvent` — ROOM_READ |
| `/user/queue/notifications` | `Notification` |
| `/user/queue/errors` | `{ error: string }` |

---

## REST API

All endpoints except `/api/auth/*` require `Authorization: Bearer <token>`.

```
# Auth
POST /api/auth/register            { name, email, password }
POST /api/auth/login               { email, password }

# Rooms
GET  /api/rooms                    all public rooms
GET  /api/rooms/me                 rooms the current user belongs to
POST /api/rooms                    create  { name, description, type }
GET  /api/rooms/{id}               details + member list
POST /api/rooms/{id}/join
POST /api/rooms/{id}/leave
DELETE /api/rooms/{id}/members/{userId}           kick (admin/owner only)
POST   /api/rooms/{id}/members/{userId}/mute?minutes=N

# Messages
GET    /api/rooms/{id}/messages?cursor=&limit=    paginated history
POST   /api/rooms/{id}/messages                   send { content }
GET    /api/rooms/{id}/messages/search?q=         search within room
PATCH  /api/rooms/{id}/messages/{msgId}           edit { content }
DELETE /api/rooms/{id}/messages/{msgId}           soft delete
POST   /api/rooms/{id}/messages/{msgId}/pin       toggle pin
POST   /api/rooms/{id}/messages/read              mark room read

# Users
GET  /api/users/me
GET  /api/users/online
GET  /api/users/{id}/presence
POST /api/users/me/heartbeat

# Notifications
GET  /api/notifications
GET  /api/notifications/count
POST /api/notifications/read-all
```

---

## Running locally

```bash
git clone https://github.com/vinay23is/distributed-chat-system
cd distributed-chat-system
cp .env.example .env         # set JWT_SECRET to any 32+ char string
docker compose up --build
```

Open `http://localhost:3000`. Register two users in separate browser windows — one will connect through backend-1, the other through backend-2. `docker compose logs -f backend-1 backend-2` shows both instances logging the same Redis pub/sub event when a message is sent.

**Infrastructure only (for development):**
```bash
docker compose up postgres redis zookeeper kafka -d

# terminal 1
cd backend && ./mvnw spring-boot:run

# terminal 2
cd backend && SERVER_PORT=8081 ./mvnw spring-boot:run

# terminal 3
cd frontend && npm install && npm run dev
```

**Environment variables:**

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | — | Required. 32+ char string |
| `SERVER_PORT` | `8080` | Backend port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_NAME` | `chatdb` | |
| `DB_USER` | `chatuser` | |
| `DB_PASS` | `chatpass` | |
| `REDIS_HOST` | `localhost` | |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | |

---

## Tech stack

| | |
|---|---|
| Backend | Java 21, Spring Boot 3.2, Spring WebSocket (STOMP), Spring Security, Spring Data JPA |
| Database | PostgreSQL 16 |
| Cache / messaging | Redis 7 (Pub/Sub, Sets, Strings with TTL) |
| Event streaming | Apache Kafka 3.7 (Confluent Platform 7.6) |
| Auth | jjwt 0.12, BCrypt |
| Frontend | React 18, TypeScript, Zustand, @stomp/stompjs, SockJS, Tailwind CSS, Vite |
| Infrastructure | Docker Compose, NGINX |