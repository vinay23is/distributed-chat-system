package com.chat.platform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.rate-limit.messages-per-10s}")
    private int messagesPer10s;

    @Value("${app.rate-limit.login-per-15m}")
    private int loginPer15m;

    @Value("${app.rate-limit.room-creation-per-hour}")
    private int roomCreationPerHour;

    public void checkMessageLimit(String userId, String roomId) {
        String key = "rl:msg:" + userId + ":" + roomId;
        checkLimit(key, messagesPer10s, Duration.ofSeconds(10), "Message rate limit exceeded. Try again shortly.");
    }

    public void checkLoginLimit(String clientIp) {
        String key = "rl:login:" + clientIp;
        checkLimit(key, loginPer15m, Duration.ofMinutes(15), "Too many login attempts. Try again in 15 minutes.");
    }

    public void checkRoomCreationLimit(String userId) {
        String key = "rl:room:" + userId;
        checkLimit(key, roomCreationPerHour, Duration.ofHours(1), "Room creation limit reached. Try again later.");
    }

    private void checkLimit(String key, int maxRequests, Duration window, String errorMessage) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) return;

        if (count == 1) {
            redisTemplate.expire(key, window);
        }

        if (count > maxRequests) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, errorMessage);
        }
    }
}
