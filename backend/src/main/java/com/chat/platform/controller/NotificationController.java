package com.chat.platform.controller;

import com.chat.platform.entity.Notification;
import com.chat.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(notificationService.getNotifications(user.getUsername(), page));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(user.getUsername())));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserDetails user) {
        notificationService.markAllRead(user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable String notificationId,
                                          @AuthenticationPrincipal UserDetails user) {
        notificationService.markRead(user.getUsername(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
