package com.chat.platform.websocket;

import com.chat.platform.dto.message.SendMessageRequest;
import com.chat.platform.dto.websocket.TypingPayload;
import com.chat.platform.service.MessageService;
import com.chat.platform.service.PresenceService;
import com.chat.platform.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final MessageService messageService;
    private final PresenceService presenceService;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    // Called when client sends to /app/chat/{roomId}
    @MessageMapping("/chat/{roomId}")
    public void sendMessage(@DestinationVariable String roomId,
                            @Payload SendMessageRequest request,
                            SimpMessageHeaderAccessor headerAccessor) {
        String userId = getPrincipalId(headerAccessor);
        if (userId == null) return;

        try {
            roomService.assertMembership(userId, roomId);
            roomService.assertNotMuted(userId, roomId);
            // The actual save + Redis publish happens in MessageService
            messageService.sendMessage(userId, roomId, request);
        } catch (Exception e) {
            log.warn("WebSocket message rejected for user {}: {}", userId, e.getMessage());
            messagingTemplate.convertAndSendToUser(userId, "/queue/errors",
                    Map.of("error", e.getMessage()));
        }
    }

    // Called when client sends to /app/typing/{roomId}
    @MessageMapping("/typing/{roomId}")
    public void handleTyping(@DestinationVariable String roomId,
                             @Payload Map<String, Boolean> body,
                             SimpMessageHeaderAccessor headerAccessor) {
        String userId = getPrincipalId(headerAccessor);
        if (userId == null) return;

        boolean isTyping = Boolean.TRUE.equals(body.get("typing"));
        presenceService.setTyping(userId, roomId, isTyping);

        // Broadcast typing event to the room via Redis (fan-out to all instances)
        // The RedisMessageSubscriber will forward this to /topic/room/{roomId}/typing
    }

    // Called when client subscribes to a room — track room presence
    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId,
                         SimpMessageHeaderAccessor headerAccessor) {
        String userId = getPrincipalId(headerAccessor);
        if (userId == null) return;

        presenceService.joinRoom(userId, roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/presence",
                Map.of("userId", userId, "event", "JOINED"));
    }

    // Called when client leaves a room
    @MessageMapping("/room/{roomId}/leave")
    public void leaveRoom(@DestinationVariable String roomId,
                          SimpMessageHeaderAccessor headerAccessor) {
        String userId = getPrincipalId(headerAccessor);
        if (userId == null) return;

        presenceService.leaveRoom(userId, roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/presence",
                Map.of("userId", userId, "event", "LEFT"));
    }

    // Mark messages in a room as read
    @MessageMapping("/read/{roomId}")
    public void markRead(@DestinationVariable String roomId,
                         SimpMessageHeaderAccessor headerAccessor) {
        String userId = getPrincipalId(headerAccessor);
        if (userId == null) return;
        messageService.markRead(userId, roomId);
    }

    // Heartbeat to keep presence alive
    @MessageMapping("/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        String userId = getPrincipalId(headerAccessor);
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = getPrincipalIdFromAccessor(accessor);
        if (userId != null) {
            presenceService.markOnline(userId);
            log.debug("User connected: {}", userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = getPrincipalIdFromAccessor(accessor);
        if (userId != null) {
            presenceService.markOffline(userId);
            log.debug("User disconnected: {}", userId);
        }
    }

    private String getPrincipalId(SimpMessageHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        return principal != null ? principal.getName() : null;
    }

    private String getPrincipalIdFromAccessor(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        return principal != null ? principal.getName() : null;
    }
}
