package com.chat.platform.repository;

import com.chat.platform.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    @Query("""
        SELECT r FROM Room r
        WHERE r.type = 'PUBLIC'
        OR r.id IN (SELECT rm.room.id FROM RoomMember rm WHERE rm.user.id = :userId)
        ORDER BY r.createdAt DESC
        """)
    List<Room> findAccessibleRooms(String userId);

    @Query("""
        SELECT r FROM Room r
        JOIN RoomMember rm ON rm.room = r
        WHERE rm.user.id = :userId
        ORDER BY r.createdAt DESC
        """)
    List<Room> findRoomsByMember(String userId);

    Page<Room> findByNameContainingIgnoreCaseAndType(String name, Room.RoomType type, Pageable pageable);
}
