package com.chat.platform.service;

import com.chat.platform.entity.Message;
import com.chat.platform.entity.Notification;
import com.chat.platform.entity.Room;
import com.chat.platform.entity.User;
import com.chat.platform.kafka.ChatEvent;
import com.chat.platform.repository.MessageRepository;
import com.chat.platform.repository.NotificationRepository;
import com.chat.platform.repository.RoomMemberRepository;
import com.chat.platform.repository.RoomRepository;
import com.chat.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void createNotificationsForEvent(ChatEvent event) {
        if (!"MESSAGE_SENT".equals(event.eventType())) return;

        Room room = roomRepository.findById(event.roomId()).orElse(null);
        if (room == null) return;

        User sender = userRepository.findById(event.senderId()).orElse(null);
        if (sender == null) return;

        Message message = event.messageId() != null
                ? messageRepository.findById(event.messageId()).orElse(null)
                : null;

        // Notify all room members who are NOT in the room right now (or offline)
        roomMemberRepository.findMembersWithUser(event.roomId()).stream()
                .filter(member -> !member.getUser().getId().equals(event.senderId()))
                .filter(member -> !presenceService.isInRoom(member.getUser().getId(), event.roomId()))
                .forEach(member -> {
                    Notification notification = Notification.builder()
                            .user(member.getUser())
                            .room(room)
                            .message(message)
                            .type(Notification.NotificationType.NEW_MESSAGE)
                            .body(sender.getName() + ": " + truncate(event.content(), 80))
                            .build();
                    notificationRepository.save(notification);

                    // Push live notification if user is online (just not in this room)
                    if (presenceService.isOnline(member.getUser().getId())) {
                        messagingTemplate.convertAndSendToUser(
                                member.getUser().getId(),
                                "/queue/notifications",
                                Map.of(
                                        "type", "NEW_MESSAGE",
                                        "roomId", event.roomId(),
                                        "roomName", room.getName(),
                                        "body", notification.getBody()
                                )
                        );
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(String userId, int page) {
        return notificationRepository.findByUserId(userId, PageRequest.of(page, 20));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(String userId) {
        notificationRepository.markAllRead(userId);
    }

    @Transactional
    public void markRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
