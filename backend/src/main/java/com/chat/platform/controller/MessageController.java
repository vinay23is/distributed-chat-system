package com.chat.platform.controller;

import com.chat.platform.dto.message.MessageResponse;
import com.chat.platform.dto.message.SendMessageRequest;
import com.chat.platform.service.MessageService;
import com.chat.platform.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getHistory(
            @PathVariable String roomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserDetails user) {

        roomService.assertMembership(user.getUsername(), roomId);
        return ResponseEntity.ok(messageService.getHistory(roomId, cursor, limit));
    }

    @PostMapping
    public ResponseEntity<MessageResponse> send(
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserDetails user) {

        roomService.assertMembership(user.getUsername(), roomId);
        roomService.assertNotMuted(user.getUsername(), roomId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.sendMessage(user.getUsername(), roomId, request));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MessageResponse>> search(
            @PathVariable String roomId,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails user) {

        roomService.assertMembership(user.getUsername(), roomId);
        return ResponseEntity.ok(messageService.searchMessages(roomId, q, limit));
    }

    @PatchMapping("/{messageId}")
    public ResponseEntity<MessageResponse> edit(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {

        roomService.assertMembership(user.getUsername(), roomId);
        return ResponseEntity.ok(messageService.editMessage(user.getUsername(), messageId, body.get("content")));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> delete(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal UserDetails user) {

        roomService.assertMembership(user.getUsername(), roomId);
        messageService.deleteMessage(user.getUsername(), messageId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{messageId}/pin")
    public ResponseEntity<Void> pin(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal UserDetails user) {

        roomService.assertMembership(user.getUsername(), roomId);
        messageService.pinMessage(user.getUsername(), messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserDetails user) {

        roomService.assertMembership(user.getUsername(), roomId);
        messageService.markRead(user.getUsername(), roomId);
        return ResponseEntity.noContent().build();
    }
}
