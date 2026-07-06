# Distributed Real-Time Chat Platform

A real-time chat system built specifically to solve one distributed-systems problem: WebSocket connections are stateful and pinned to whichever server accepted them, so scaling horizontally breaks delivery unless you build a cross-instance relay.

No hosted live demo — this is a multi-service stack (two backend instances, Postgres, Redis, Kafka, NGINX, a React frontend) meant to be run locally via Docker Compose, not deployed to a single free-tier dyno.

## What problem does this solve?

Once you run more than one instance of a WebSocket server behind a load balancer, a message sent by a client connected to instance A never reaches a client connected to instance B — there's no shared connection state between them. Most tutorials sidestep this by only ever running one instance. This project builds the actual fix: Redis Pub/Sub as an event bus between backend instances, so every instance can deliver a message to its own locally-connected clients regardless of which instance originally received it. I built this to demonstrate horizontal-scaling patterns for stateful connections, not just CRUD-over-WebSocket.

## Tech Stack

- **Frontend:** React 18, TypeScript, Vite, Zustand (state), `@stomp/stompjs` + SockJS (WebSocket client), Tailwind CSS
- **Backend:** Java 21, Spring Boot 3.2, Spring WebSocket (STOMP), Spring Security, Spring Data JPA
- **Database:** PostgreSQL 16
- **Cache/Messaging:** Redis 7 (Pub/Sub for cross-instance fan-out, Sets for presence, Strings with TTL for typing indicators and rate limits)
- **Event Streaming:** Apache Kafka 3.7 (Confluent Platform 7.6) for durable notification delivery
- **Auth:** JWT (jjwt 0.12) + BCrypt
- **Infra/Deployment:** Docker Compose running two backend instances behind NGINX, plus Postgres, Redis, Kafka, and ZooKeeper

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

**How a message crosses instances:** User A (connected to backend-1) sends a message. `MessageService` rate-limits it (Redis `INCR`), persists it to Postgres, then publishes it to a Redis channel (`chat:room:{roomId}`). Every backend instance subscribes to `chat:*` at startup via `RedisMessageListenerContainer`, so both backend-1 and backend-2 receive the published event and each routes it to its own locally-connected STOMP clients subscribed to `/topic/room/{roomId}`. User B, connected to backend-2, gets the message with the same latency as if they'd been on the same instance as User A. In parallel, the event is also published to Kafka so offline room members get a durable notification even if Redis Pub/Sub's fire-and-forget delivery missed them.

## Key Features

- Real-time messaging with typing indicators, read receipts, and presence (online/offline, per-room "currently viewing"), all built on Redis with TTL-based expiry instead of explicit cleanup events
- Horizontal scaling proof: two backend instances behind NGINX, verifiable by watching both instances log the same Redis pub/sub event when a message is sent
- JWT auth for REST plus a STOMP `CONNECT`-frame interceptor for WebSocket sessions, since the HTTP auth filter chain doesn't run for WebSocket handshakes
- Cursor-based pagination for message history (`WHERE created_at < :cursor`) instead of `OFFSET`, so history load time doesn't degrade as a room's message count grows
- Redis-backed atomic rate limiting on message sends, login attempts, and room creation
- Soft-deleted messages (content retained for receipt/notification integrity; the delete event only carries the message ID)
- CI (GitHub Actions) runs Maven backend tests and the React/TypeScript production build

## Interesting Engineering Decisions

- **Redis Pub/Sub for cross-instance WebSocket fan-out, Kafka for durability.** These solve different problems on purpose: Redis Pub/Sub is fire-and-forget, which is fine for real-time delivery because the message is already safely in Postgres and a reconnecting client just reloads history. Kafka's durable log is used specifically for notifications, where a user offline for 10 minutes still needs to receive them when they come back — the consumer can restart and replay from its last committed offset, which Redis Pub/Sub can't do.
- **STOMP `CONNECT` interception instead of trying to reuse the HTTP JWT filter for WebSockets.** The WebSocket handshake completes as a plain HTTP request before any STOMP frames exist, so a `ChannelInterceptor` validates the JWT on the STOMP `CONNECT` frame instead and attaches a `Principal` that persists for the life of the session — every `@MessageMapping` handler then gets the authenticated user for free via `SimpMessageHeaderAccessor.getUser()`.
- **A deliberate two-`useEffect` split on the frontend to avoid a subscription race.** The STOMP handshake takes 100–500ms; if a component tried to subscribe to a room in the same effect that fetches history, navigating to a room before the socket connects would silently skip the subscription. Splitting history-fetch (fires on room change) from the room subscription (fires on room change *or* connection-state change) means the subscription effect re-fires automatically once `connected` flips to `true`.
- **Kafka partition-key-driven ordering isn't needed here, but consumer resilience is.** The notification consumer uses `ErrorHandlingDeserializer` wrapping `JsonDeserializer` with a default type fallback, so a malformed message is logged and skipped instead of crashing the whole consumer container.
- **A known Kafka/ZooKeeper restart quirk is handled explicitly.** On container restart, Kafka can crash with `NodeExists` if its previous ephemeral broker registration in ZooKeeper hasn't expired yet. This is mitigated with a shortened `KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS` (6000ms) plus `restart: on-failure`, rather than leaving it to fail silently on `docker compose up`.

## Running Locally

```bash
git clone https://github.com/vinay23is/distributed-chat-system
cd distributed-chat-system
cp .env.example .env         # set JWT_SECRET to any 32+ char string
docker compose up --build
```

Open `http://localhost:3000`. Register two users in separate browser windows — one connects through backend-1, the other through backend-2. `docker compose logs -f backend-1 backend-2` shows both instances logging the same Redis pub/sub event when a message is sent, which is the easiest way to see the cross-instance fan-out working.

**Infrastructure only (for backend/frontend development):**

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
| `DB_HOST` / `DB_NAME` / `DB_USER` / `DB_PASS` | `localhost` / `chatdb` / `chatuser` / `chatpass` | PostgreSQL connection |
| `REDIS_HOST` | `localhost` | |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | |

## REST/WebSocket API

Full endpoint reference (auth, rooms, messages, users, notifications, and the STOMP `/app`/`/topic` protocol) is documented inline in the codebase's controllers and `ChatWebSocketController`. Key REST routes:

```
POST /api/auth/register | /api/auth/login
GET/POST /api/rooms, /api/rooms/me, /api/rooms/{id}
GET/POST/PATCH/DELETE /api/rooms/{id}/messages[...]
GET /api/users/me, /api/users/online, /api/users/{id}/presence
GET /api/notifications, /api/notifications/count
```
