package com.chat.platform.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record AuthResponse(
        String token,
        String userId,
        String name,
        String email,
        String avatarUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt
) {}
