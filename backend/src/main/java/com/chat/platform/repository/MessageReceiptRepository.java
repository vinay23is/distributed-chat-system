package com.chat.platform.repository;

import com.chat.platform.entity.MessageReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, String> {

    Optional<MessageReceipt> findByMessageIdAndUserId(String messageId, String userId);

    List<MessageReceipt> findByMessageId(String messageId);

    @Modifying
    @Query("""
        UPDATE MessageReceipt r SET r.status = 'READ', r.updatedAt = :now
        WHERE r.message.room.id = :roomId
        AND r.user.id = :userId
        AND r.status != 'READ'
        """)
    int markRoomMessagesRead(String roomId, String userId, Instant now);
}
