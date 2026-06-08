package com.chat.platform.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void generatedTokenContainsUserIdAndEmailClaim() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        String token = jwtUtil.generateToken("user-123", "vinay@example.com");
        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("user-123");
        assertThat(claims.get("email", String.class)).isEqualTo("vinay@example.com");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user-123");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void invalidTokenIsRejected() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        assertThat(jwtUtil.isValid("not-a-jwt")).isFalse();
    }
}
