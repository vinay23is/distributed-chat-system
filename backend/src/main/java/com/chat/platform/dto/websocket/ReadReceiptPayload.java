package com.chat.platform.dto.websocket;

public record ReadReceiptPayload(
        String messageId,
        String userId,
        String status
) {}
