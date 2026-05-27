package com.chat.platform.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record ChatEvent(
        String eventType,       // MESSAGE_SENT, USER_JOINED, USER_LEFT
        String roomId,
        String messageId,
        String senderId,
        String senderName,
        String senderAvatarUrl,
        String content,
        String messageType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp
) {}
