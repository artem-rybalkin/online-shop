package com.shop.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-only-32chars";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", TEST_SECRET);
    }

    @Test
    void generateToken_ShouldReturnNonNullToken() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", "USER");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void extractUsername_ShouldReturnCorrectUsername() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", "USER");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void validateToken_ShouldReturnTrue_ForMatchingUsernameAndValidToken() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", "USER");
        assertThat(jwtUtil.validateToken(token, "alice")).isTrue();
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenUsernameMismatch() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", "USER");
        assertThat(jwtUtil.validateToken(token, "bob")).isFalse();
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenTokenHasNoSubjectClaim() {
        // Build a signed token that deliberately omits the sub claim
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        String noSubToken = Jwts.builder()
                .claim("email", "ghost@example.com")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();

        assertThat(jwtUtil.validateToken(noSubToken, "alice")).isFalse();
    }

    @Test
    void validateToken_ShouldThrowExpiredJwtException_ForExpiredToken() {
        // Build a token that expired 1 second ago
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key)
                .compact();

        // JJWT throws ExpiredJwtException during parseSignedClaims — the JwtFilter
        // catches this in its try/catch so the request proceeds as anonymous
        assertThrows(ExpiredJwtException.class,
                () -> jwtUtil.validateToken(expiredToken, "alice"));
    }

    @Test
    void generateToken_ShouldProduceDifferentTokens_ForDifferentUsers() {
        String tokenA = jwtUtil.generateToken("alice", "alice@example.com", "USER");
        String tokenB = jwtUtil.generateToken("bob", "bob@example.com", "USER");
        assertThat(tokenA).isNotEqualTo(tokenB);
    }

    @Test
    void extractRole_ShouldReturnCorrectRole() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", "ADMIN");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void extractExpiration_ShouldReturnFutureDate_ForFreshToken() {
        String token = jwtUtil.generateToken("alice", "alice@example.com", "USER");
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());
    }

    @Test
    void extractUsername_ShouldThrow_WhenTokenSignedWithDifferentKey() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-for-tests-only-32chars!".getBytes());
        String tamperedToken = Jwts.builder()
                .subject("alice")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(wrongKey)
                .compact();

        assertThrows(SignatureException.class,
                () -> jwtUtil.extractUsername(tamperedToken));
    }
}
