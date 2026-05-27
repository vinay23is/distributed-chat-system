package com.chat.platform.service;

import com.chat.platform.dto.room.CreateRoomRequest;
import com.chat.platform.dto.room.RoomResponse;
import com.chat.platform.entity.Room;
import com.chat.platform.entity.RoomMember;
import com.chat.platform.entity.User;
import com.chat.platform.repository.RoomMemberRepository;
import com.chat.platform.repository.RoomRepository;
import com.chat.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final RateLimitService rateLimitService;

    @Transactional
    public RoomResponse createRoom(String userId, CreateRoomRequest request) {
        rateLimitService.checkRoomCreationLimit(userId);

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Room.RoomType type = request.type() != null
                ? Room.RoomType.valueOf(request.type().toUpperCase())
                : Room.RoomType.PUBLIC;

        Room room = Room.builder()
                .name(request.name())
                .description(request.description())
                .type(type)
                .createdBy(creator)
                .build();
        roomRepository.save(room);

        RoomMember owner = RoomMember.builder()
                .room(room)
                .user(creator)
                .role(RoomMember.MemberRole.OWNER)
                .build();
        roomMemberRepository.save(owner);

        return RoomResponse.from(room, List.of(owner), RoomMember.MemberRole.OWNER);
    }

    @Transactional
    public RoomResponse joinRoom(String userId, String roomId) {
        if (roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            return getRoomDetails(userId, roomId);
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (room.getType() == Room.RoomType.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This room is private");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        RoomMember member = RoomMember.builder()
                .room(room)
                .user(user)
                .role(RoomMember.MemberRole.MEMBER)
                .build();
        roomMemberRepository.save(member);

        List<RoomMember> allMembers = roomMemberRepository.findMembersWithUser(roomId);
        return RoomResponse.from(room, allMembers, RoomMember.MemberRole.MEMBER);
    }

    @Transactional
    public void leaveRoom(String userId, String roomId) {
        RoomMember member = roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a member of this room"));

        if (member.getRole() == RoomMember.MemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room owner cannot leave. Transfer ownership or delete the room.");
        }

        roomMemberRepository.deleteByRoomIdAndUserId(roomId, userId);
    }

    @Transactional
    public void kickMember(String requesterId, String roomId, String targetUserId) {
        RoomMember requester = roomMemberRepository.findByRoomIdAndUserId(roomId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member"));

        if (requester.getRole() == RoomMember.MemberRole.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners and admins can kick members");
        }

        roomMemberRepository.deleteByRoomIdAndUserId(roomId, targetUserId);
    }

    @Transactional
    public void muteUser(String requesterId, String roomId, String targetUserId, int minutes) {
        RoomMember requester = roomMemberRepository.findByRoomIdAndUserId(roomId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member"));

        if (requester.getRole() == RoomMember.MemberRole.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners and admins can mute members");
        }

        RoomMember target = roomMemberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not in room"));

        target.setMutedUntil(Instant.now().plusSeconds((long) minutes * 60));
        roomMemberRepository.save(target);
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomDetails(String userId, String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        List<RoomMember> members = roomMemberRepository.findMembersWithUser(roomId);

        RoomMember.MemberRole currentUserRole = members.stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .map(RoomMember::getRole)
                .findFirst()
                .orElse(null);

        return RoomResponse.from(room, members, currentUserRole);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(String userId) {
        return roomRepository.findRoomsByMember(userId).stream()
                .map(room -> {
                    List<RoomMember> members = roomMemberRepository.findByRoomId(room.getId());
                    RoomMember.MemberRole role = members.stream()
                            .filter(m -> m.getUser().getId().equals(userId))
                            .map(RoomMember::getRole)
                            .findFirst()
                            .orElse(RoomMember.MemberRole.MEMBER);
                    return RoomResponse.from(room, members, role);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getPublicRooms(String userId) {
        return roomRepository.findAccessibleRooms(userId).stream()
                .map(room -> {
                    List<RoomMember> members = roomMemberRepository.findByRoomId(room.getId());
                    RoomMember.MemberRole role = members.stream()
                            .filter(m -> m.getUser().getId().equals(userId))
                            .map(RoomMember::getRole)
                            .findFirst()
                            .orElse(null);
                    return RoomResponse.from(room, members, role);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertMembership(String userId, String roomId) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this room");
        }
    }

    @Transactional(readOnly = true)
    public void assertNotMuted(String userId, String roomId) {
        roomMemberRepository.findByRoomIdAndUserId(roomId, userId).ifPresent(member -> {
            if (member.getMutedUntil() != null && member.getMutedUntil().isAfter(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are muted until " + member.getMutedUntil());
            }
        });
    }
}
