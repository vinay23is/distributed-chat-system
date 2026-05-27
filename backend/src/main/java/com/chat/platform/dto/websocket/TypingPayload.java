package com.chat.platform.dto.websocket;

public record TypingPayload(
        String roomId,
        String userId,
        String userName,
        boolean typing
) {}
