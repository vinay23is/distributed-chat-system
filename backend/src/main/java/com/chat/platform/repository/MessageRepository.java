package com.chat.platform.repository;

import com.chat.platform.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender
        WHERE m.room.id = :roomId
        AND m.deletedAt IS NULL
        AND m.createdAt < :cursor
        ORDER BY m.createdAt DESC
        """)
    List<Message> findByRoomCursorPaged(String roomId, Instant cursor, Pageable pageable);

    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender
        WHERE m.room.id = :roomId
        AND m.deletedAt IS NULL
        AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY m.createdAt DESC
        """)
    List<Message> searchInRoom(String roomId, String query, Pageable pageable);

    long countByRoomIdAndDeletedAtIsNull(String roomId);
}
