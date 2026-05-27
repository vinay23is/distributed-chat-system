package com.chat.platform.dto.room;

import com.chat.platform.entity.Room;
import com.chat.platform.entity.RoomMember;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public record RoomResponse(
        String id,
        String name,
        String description,
        String type,
        String createdById,
        String createdByName,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        int memberCount,
        String currentUserRole,
        List<MemberSummary> members
) {
    public record MemberSummary(
            String userId,
            String name,
            String avatarUrl,
            String role,
            boolean online
    ) {}

    public static RoomResponse from(Room room, List<RoomMember> members,
                                     RoomMember.MemberRole currentUserRole) {
        List<MemberSummary> summaries = members.stream()
                .map(m -> new MemberSummary(
                        m.getUser().getId(),
                        m.getUser().getName(),
                        m.getUser().getAvatarUrl(),
                        m.getRole().name(),
                        false
                ))
                .toList();

        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getType().name(),
                room.getCreatedBy().getId(),
                room.getCreatedBy().getName(),
                room.getCreatedAt(),
                members.size(),
                currentUserRole != null ? currentUserRole.name() : null,
                summaries
        );
    }
}
