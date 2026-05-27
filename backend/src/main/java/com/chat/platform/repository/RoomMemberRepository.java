package com.chat.platform.repository;

import com.chat.platform.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, String> {

    Optional<RoomMember> findByRoomIdAndUserId(String roomId, String userId);

    boolean existsByRoomIdAndUserId(String roomId, String userId);

    List<RoomMember> findByRoomId(String roomId);

    void deleteByRoomIdAndUserId(String roomId, String userId);

    @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.user WHERE rm.room.id = :roomId")
    List<RoomMember> findMembersWithUser(String roomId);
}
