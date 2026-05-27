package com.chat.platform.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record ChatMessage(
        String eventType,  // MESSAGE, TYPING, PRESENCE, RECEIPT, NOTIFICATION
        String roomId,
        String messageId,
        String senderId,
        String senderName,
        String senderAvatarUrl,
        String content,
        String messageType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        Object payload     // extra data depending on eventType
) {}
