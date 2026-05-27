package com.chat.platform.service;

import com.chat.platform.dto.message.MessageResponse;
import com.chat.platform.dto.message.SendMessageRequest;
import com.chat.platform.entity.Message;
import com.chat.platform.entity.MessageReceipt;
import com.chat.platform.entity.Room;
import com.chat.platform.entity.User;
import com.chat.platform.kafka.ChatEvent;
import com.chat.platform.kafka.KafkaEventProducer;
import com.chat.platform.repository.MessageReceiptRepository;
import com.chat.platform.repository.MessageRepository;
import com.chat.platform.repository.RoomRepository;
import com.chat.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RateLimitService rateLimitService;
    private final KafkaEventProducer kafkaEventProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int PAGE_SIZE = 50;

    @Transactional
    public MessageResponse sendMessage(String userId, String roomId, SendMessageRequest request) {
        rateLimitService.checkMessageLimit(userId, roomId);

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Message.MessageType type = request.messageType() != null
                ? Message.MessageType.valueOf(request.messageType().toUpperCase())
                : Message.MessageType.TEXT;

        Message message = Message.builder()
                .room(room)
                .sender(sender)
                .content(request.content())
                .messageType(type)
                .build();
        messageRepository.save(message);

        // Create SENT receipt for sender
        MessageReceipt senderReceipt = MessageReceipt.builder()
                .message(message)
                .user(sender)
                .status(MessageReceipt.ReceiptStatus.SENT)
                .updatedAt(Instant.now())
                .build();
        receiptRepository.save(senderReceipt);

        // Publish to Redis Pub/Sub for same-instance and cross-instance delivery
        ChatEvent event = new ChatEvent(
                "MESSAGE_SENT",
                roomId,
                message.getId(),
                sender.getId(),
                sender.getName(),
                sender.getAvatarUrl(),
                message.getContent(),
                message.getMessageType().name(),
                message.getCreatedAt()
        );

        redisTemplate.convertAndSend("chat:room:" + roomId, event);

        // Publish to Kafka for notification service (async, decoupled)
        kafkaEventProducer.publishNotificationEvent(event);

        return MessageResponse.from(message, List.of(senderReceipt));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getHistory(String roomId, String cursor, int limit) {
        int pageSize = Math.min(limit > 0 ? limit : PAGE_SIZE, 100);
        Instant cursorTime = cursor != null ? Instant.parse(cursor) : Instant.now().plusSeconds(1);

        return messageRepository.findByRoomCursorPaged(
                roomId, cursorTime, PageRequest.of(0, pageSize)
        ).stream()
                .map(msg -> {
                    List<MessageReceipt> receipts = receiptRepository.findByMessageId(msg.getId());
                    return MessageResponse.from(msg, receipts);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(String roomId, String query, int limit) {
        return messageRepository.searchInRoom(roomId, query, PageRequest.of(0, Math.min(limit, 50)))
                .stream()
                .map(msg -> MessageResponse.from(msg, receiptRepository.findByMessageId(msg.getId())))
                .toList();
    }

    @Transactional
    public MessageResponse editMessage(String userId, String messageId, String newContent) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot edit another user's message");
        }
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Message was deleted");
        }

        message.setContent(newContent);
        message.setEditedAt(Instant.now());
        messageRepository.save(message);

        // Broadcast edit event via Redis
        ChatEvent editEvent = new ChatEvent(
                "MESSAGE_EDITED",
                message.getRoom().getId(),
                message.getId(),
                userId, null, null, newContent, null, Instant.now()
        );
        redisTemplate.convertAndSend("chat:room:" + message.getRoom().getId(), editEvent);

        return MessageResponse.from(message, receiptRepository.findByMessageId(messageId));
    }

    @Transactional
    public void deleteMessage(String userId, String messageId, boolean isAdmin) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (!isAdmin && !message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's message");
        }

        message.setDeletedAt(Instant.now());
        messageRepository.save(message);

        ChatEvent deleteEvent = new ChatEvent(
                "MESSAGE_DELETED",
                message.getRoom().getId(),
                message.getId(),
                userId, null, null, null, null, Instant.now()
        );
        redisTemplate.convertAndSend("chat:room:" + message.getRoom().getId(), deleteEvent);
    }

    @Transactional
    public void pinMessage(String userId, String messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        message.setPinnedAt(message.getPinnedAt() == null ? Instant.now() : null);
        messageRepository.save(message);
    }

    @Transactional
    public void markRead(String userId, String roomId) {
        int updated = receiptRepository.markRoomMessagesRead(roomId, userId, Instant.now());
        if (updated > 0) {
            ChatEvent readEvent = new ChatEvent(
                    "ROOM_READ",
                    roomId, null, userId, null, null, null, null, Instant.now()
            );
            redisTemplate.convertAndSend("chat:room:" + roomId, readEvent);
        }
    }
}
