package com.chat.platform.controller;

import com.chat.platform.dto.room.CreateRoomRequest;
import com.chat.platform.dto.room.RoomResponse;
import com.chat.platform.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(user.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getPublicRooms(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(roomService.getPublicRooms(user.getUsername()));
    }

    @GetMapping("/me")
    public ResponseEntity<List<RoomResponse>> getMyRooms(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(roomService.getMyRooms(user.getUsername()));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoomDetails(@PathVariable String roomId,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(roomService.getRoomDetails(user.getUsername(), roomId));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomResponse> joinRoom(@PathVariable String roomId,
                                                  @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(roomService.joinRoom(user.getUsername(), roomId));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId,
                                           @AuthenticationPrincipal UserDetails user) {
        roomService.leaveRoom(user.getUsername(), roomId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ResponseEntity<Void> kickMember(@PathVariable String roomId,
                                            @PathVariable String targetUserId,
                                            @AuthenticationPrincipal UserDetails user) {
        roomService.kickMember(user.getUsername(), roomId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/members/{targetUserId}/mute")
    public ResponseEntity<Void> muteUser(@PathVariable String roomId,
                                          @PathVariable String targetUserId,
                                          @RequestParam(defaultValue = "10") int minutes,
                                          @AuthenticationPrincipal UserDetails user) {
        roomService.muteUser(user.getUsername(), roomId, targetUserId, minutes);
        return ResponseEntity.noContent().build();
    }
}
