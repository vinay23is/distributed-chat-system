package com.chat.platform.repository;

import com.chat.platform.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    @Query("""
        SELECT n FROM Notification n
        JOIN FETCH n.room
        JOIN FETCH n.message
        WHERE n.user.id = :userId
        ORDER BY n.createdAt DESC
        """)
    List<Notification> findByUserId(String userId, Pageable pageable);

    long countByUserIdAndReadFalse(String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllRead(String userId);
}
