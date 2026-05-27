package com.chat.platform.websocket;

import com.chat.platform.kafka.ChatEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Receives Redis Pub/Sub events and fans them out to WebSocket clients on THIS instance.
 *
 * This is the key distributed pattern: every backend instance subscribes to Redis
 * channels. When any instance publishes a ChatEvent, ALL instances receive it here
 * and broadcast to their locally connected WebSocket clients.
 *
 * Flow:
 *   User sends message
 *   -> Backend instance receives via STOMP /app/chat/{roomId}
 *   -> MessageService saves to PostgreSQL
 *   -> MessageService publishes to Redis channel chat:room:{roomId}
 *   -> This subscriber on EVERY instance receives the event
 *   -> Each instance calls messagingTemplate.convertAndSend("/topic/room/{roomId}", event)
 *   -> All WebSocket clients subscribed to that topic receive the message
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper redisObjectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        log.debug("Redis event on channel {}", channel);

        try {
            ChatEvent event = redisObjectMapper.readValue(body, ChatEvent.class);

            if (channel.startsWith("chat:room:")) {
                String roomId = channel.replace("chat:room:", "");
                routeRoomEvent(roomId, event);
            }
        } catch (Exception e) {
            log.error("Failed to process Redis message on channel {}: {}", channel, e.getMessage(), e);
        }
    }

    private void routeRoomEvent(String roomId, ChatEvent event) {
        switch (event.eventType()) {
            case "MESSAGE_SENT", "MESSAGE_EDITED", "MESSAGE_DELETED" ->
                    messagingTemplate.convertAndSend("/topic/room/" + roomId, event);

            case "TYPING_START", "TYPING_STOP" ->
                    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/typing", event);

            case "ROOM_READ" ->
                    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/receipts", event);

            default ->
                    log.warn("Unknown event type: {}", event.eventType());
        }
    }
}
