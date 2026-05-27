package com.chat.platform.controller;

import com.chat.platform.entity.User;
import com.chat.platform.repository.UserRepository;
import com.chat.platform.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PresenceService presenceService;

    @GetMapping("/me")
    public ResponseEntity<User> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findById(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(user);
    }

    @GetMapping("/online")
    public ResponseEntity<Set<Object>> getOnlineUsers() {
        return ResponseEntity.ok(presenceService.getOnlineUsers());
    }

    @GetMapping("/{userId}/presence")
    public ResponseEntity<Map<String, Object>> getUserPresence(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "online", presenceService.isOnline(userId),
                "lastSeenAt", user.getLastSeenAt() != null ? user.getLastSeenAt().toString() : null
        ));
    }

    @PostMapping("/me/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal UserDetails userDetails) {
        presenceService.heartbeat(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
