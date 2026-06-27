package com.shop.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies JwtFilter correctly sets / skips authentication in the SecurityContext
 * based on the "jwt" auth cookie contents.
 *
 * Uses DELETE /api/products/{id} (requires ADMIN role) as the probe endpoint:
 *   - No / invalid auth  → filter skips context population → anonymous → 403
 *   - Valid ADMIN JWT    → filter populates context → auth passes → 204 (Spring returns 204 for delete, even non-existent)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class JwtFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-32chars";

    @Autowired
    private MockMvc mockMvc;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
    }

    @Test
    void filter_ShouldProceedAsAnonymous_WhenNoJwtCookie() throws Exception {
        mockMvc.perform(delete("/api/products/99999"))
                .andExpect(status().isForbidden());
    }

    @Test
    void filter_ShouldProceedAsAnonymous_WhenCookieHasWrongName() throws Exception {
        mockMvc.perform(delete("/api/products/99999")
                .cookie(new Cookie("session", "not-a-jwt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void filter_ShouldSetAdminAuthentication_WhenValidAdminJwtProvided() throws Exception {
        String token = jwtUtil.generateToken("filter-admin", "filter-admin@test.com", "ADMIN");

        // ADMIN JWT → auth passes → 204 (delete returns no-content even when product absent)
        mockMvc.perform(delete("/api/products/99999")
                .cookie(new Cookie("jwt", token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void filter_ShouldProceedAsAnonymous_WhenJwtIsExpired() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .subject("alice")
                .claim("role", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key)
                .compact();

        // Filter catches ExpiredJwtException silently → anonymous → 403
        mockMvc.perform(delete("/api/products/99999")
                .cookie(new Cookie("jwt", expiredToken)))
                .andExpect(status().isForbidden());
    }
}
