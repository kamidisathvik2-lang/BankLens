package com.banklens.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtil")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-for-hs256-algorithm-yes";
    private static final long EXPIRATION = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION);
    }

    @Test
    @DisplayName("generates a valid token for a given email")
    void generateToken_valid() {
        String token = jwtUtil.generateToken("sathvik@example.com");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    @DisplayName("extracts email from a valid token")
    void extractEmail_valid() {
        String email = "sathvik@example.com";
        String token = jwtUtil.generateToken(email);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
    }

    @Test
    @DisplayName("validates a freshly generated token")
    void isTokenValid_freshToken() {
        String token = jwtUtil.generateToken("user@example.com");
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("rejects a tampered token")
    void isTokenValid_tamperedToken() {
        String token = jwtUtil.generateToken("user@example.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("rejects a blank token")
    void isTokenValid_blank() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
        assertThat(jwtUtil.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("rejects an expired token")
    void isTokenValid_expired() {
        JwtUtil shortLived = new JwtUtil(SECRET, 1L); // expires in 1ms
        String token = shortLived.generateToken("user@example.com");
        // Token should expire almost immediately
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        assertThat(shortLived.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("throws on secret shorter than 32 bytes")
    void constructor_shortSecret() {
        assertThatThrownBy(() -> new JwtUtil("short", EXPIRATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("different users get different tokens")
    void generateToken_differentUsers() {
        String t1 = jwtUtil.generateToken("user1@example.com");
        String t2 = jwtUtil.generateToken("user2@example.com");
        assertThat(t1).isNotEqualTo(t2);
    }
}
