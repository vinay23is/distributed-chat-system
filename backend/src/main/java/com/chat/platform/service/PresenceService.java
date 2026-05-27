package com.chat.platform.service;

import com.chat.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    @Value("${app.redis.presence-ttl-seconds}")
    private long presenceTtlSeconds;

    @Value("${app.redis.typing-ttl-seconds}")
    private long typingTtlSeconds;

    private static final String ONLINE_KEY = "presence:online";
    private static final String ROOM_MEMBERS_KEY = "presence:room:";
    private static final String TYPING_KEY = "typing:";

    @Transactional
    public void markOnline(String userId) {
        redisTemplate.opsForSet().add(ONLINE_KEY, userId);
        redisTemplate.expire(ONLINE_KEY + ":" + userId, Duration.ofSeconds(presenceTtlSeconds));
        redisTemplate.opsForValue().set("presence:hb:" + userId, Instant.now().toString(),
                Duration.ofSeconds(presenceTtlSeconds));
        userRepository.updateLastSeen(userId, Instant.now());
    }

    @Transactional
    public void markOffline(String userId) {
        redisTemplate.opsForSet().remove(ONLINE_KEY, userId);
        redisTemplate.delete("presence:hb:" + userId);
        userRepository.updateLastSeen(userId, Instant.now());
    }

    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ONLINE_KEY, userId));
    }

    public Set<Object> getOnlineUsers() {
        return redisTemplate.opsForSet().members(ONLINE_KEY);
    }

    public void joinRoom(String userId, String roomId) {
        redisTemplate.opsForSet().add(ROOM_MEMBERS_KEY + roomId, userId);
    }

    public void leaveRoom(String userId, String roomId) {
        redisTemplate.opsForSet().remove(ROOM_MEMBERS_KEY + roomId, userId);
    }

    public Set<Object> getRoomOnlineMembers(String roomId) {
        return redisTemplate.opsForSet().members(ROOM_MEMBERS_KEY + roomId);
    }

    public boolean isInRoom(String userId, String roomId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ROOM_MEMBERS_KEY + roomId, userId));
    }

    public void setTyping(String userId, String roomId, boolean typing) {
        String key = TYPING_KEY + roomId + ":" + userId;
        if (typing) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(typingTtlSeconds));
        } else {
            redisTemplate.delete(key);
        }
    }

    public Set<String> getTypingUsers(String roomId) {
        Set<String> keys = redisTemplate.keys(TYPING_KEY + roomId + ":*");
        if (keys == null || keys.isEmpty()) return Set.of();
        return keys.stream()
                .map(k -> k.replace(TYPING_KEY + roomId + ":", ""))
                .collect(java.util.stream.Collectors.toSet());
    }

    // Heartbeat to keep presence alive (called by client periodically).
    // @Transactional here so the self-call to markOnline runs within an active transaction
    // (Spring's proxy is bypassed on self-invocation, so the caller must own the transaction).
    @Transactional
    public void heartbeat(String userId) {
        markOnline(userId);
    }
}
