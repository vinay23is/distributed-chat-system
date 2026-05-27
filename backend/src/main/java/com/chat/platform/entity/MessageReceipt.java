package com.chat.platform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "message_receipts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"}),
    indexes = {
        @Index(name = "idx_receipts_message", columnList = "message_id"),
        @Index(name = "idx_receipts_user", columnList = "user_id")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptStatus status;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ReceiptStatus {
        SENT, DELIVERED, READ
    }
}
