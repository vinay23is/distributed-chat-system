package com.chat.platform.dto.message;

import com.chat.platform.entity.Message;
import com.chat.platform.entity.MessageReceipt;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public record MessageResponse(
        String id,
        String roomId,
        String senderId,
        String senderName,
        String senderAvatarUrl,
        String content,
        String messageType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant editedAt,
        boolean deleted,
        boolean pinned,
        List<ReceiptSummary> receipts
) {
    public record ReceiptSummary(String userId, String status) {}

    public static MessageResponse from(Message msg, List<MessageReceipt> receipts) {
        return new MessageResponse(
                msg.getId(),
                msg.getRoom().getId(),
                msg.getSender().getId(),
                msg.getSender().getName(),
                msg.getSender().getAvatarUrl(),
                msg.isDeleted() ? null : msg.getContent(),
                msg.getMessageType().name(),
                msg.getCreatedAt(),
                msg.getEditedAt(),
                msg.isDeleted(),
                msg.getPinnedAt() != null,
                receipts.stream()
                        .map(r -> new ReceiptSummary(r.getUser().getId(), r.getStatus().name()))
                        .toList()
        );
    }
}
