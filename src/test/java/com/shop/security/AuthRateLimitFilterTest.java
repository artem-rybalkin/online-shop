package com.shop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AuthRateLimitFilter.
 * Instantiates the filter directly (no Spring context) for speed and isolation.
 */
class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter();
        ReflectionTestUtils.setField(filter, "maxRequests", 2);
        ReflectionTestUtils.setField(filter, "windowSeconds", 60);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void doFilter_ShouldApplyRateLimit_ForAuthEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();
        FilterChain chain = (req, resp) -> chainCalls.incrementAndGet();

        // First request — within limit
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void doFilter_ShouldSkipRateLimit_ForNonAuthEndpoints() throws Exception {
        // Set a limit of 1 to confirm it is NOT enforced on non-auth paths
        ReflectionTestUtils.setField(filter, "maxRequests", 1);

        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/products");
        AtomicInteger chainCalls = new AtomicInteger();
        FilterChain chain = (req, resp) -> chainCalls.incrementAndGet();

        filter.doFilter(req1, new MockHttpServletResponse(), chain);
        filter.doFilter(req2, new MockHttpServletResponse(), chain);

        // Both requests pass through — filter is bypassed for non-auth endpoints
        assertThat(chainCalls.get()).isEqualTo(2);
    }

    @Test
    void doFilter_ShouldReturn429_WhenRateLimitExceeded() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequests", 1);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        FilterChain chain = (req, resp) -> {};

        // First request — within limit
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request, first, chain);
        assertThat(first.getStatus()).isNotEqualTo(429);

        // Second request — exceeds limit
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request, second, chain);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("Too many requests");
    }

    @Test
    void doFilter_ShouldPassThrough_WhenRequestsAreWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("10.0.0.2");
        AtomicInteger chainCalls = new AtomicInteger();
        FilterChain chain = (req, resp) -> chainCalls.incrementAndGet();

        for (int i = 0; i < 2; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        // Both within the limit of 2
        assertThat(chainCalls.get()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void evictExpiredEntries_ShouldRemoveStaleEntry_OnNextRequest() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr("10.1.1.1");
        filter.doFilter(req1, new MockHttpServletResponse(), (req, resp) -> {});

        // Age the entry for 10.1.1.1 beyond the window by setting windowStart to epoch
        ConcurrentHashMap<String, Object> buckets =
                (ConcurrentHashMap<String, Object>) ReflectionTestUtils.getField(filter, "buckets");
        Object staleEntry = buckets.get("10.1.1.1");
        ReflectionTestUtils.setField(staleEntry, "windowStart", 0L);
        assertThat(buckets).containsKey("10.1.1.1");

        // A request from a different IP triggers evictExpiredEntries()
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr("10.1.1.2");
        filter.doFilter(req2, new MockHttpServletResponse(), (req, resp) -> {});

        // Stale entry for 10.1.1.1 should be gone; only 10.1.1.2 remains
        assertThat(buckets).doesNotContainKey("10.1.1.1");
        assertThat(buckets).containsKey("10.1.1.2");
    }

    @Test
    void doFilter_ShouldUseFirstIp_WhenXForwardedForHasMultipleAddresses_AndRemoteAddrIsTrustedProxy() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequests", 1);
        // Both requests arrive via the same trusted reverse proxy (default MockHttpServletRequest remoteAddr).
        ReflectionTestUtils.setField(filter, "trustedProxies", "127.0.0.1");

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 192.168.1.1");

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 192.168.1.1");

        FilterChain chain = (req, resp) -> {};

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(req1, first, chain);
        assertThat(first.getStatus()).isNotEqualTo(429);

        // Same originating IP (203.0.113.5) — should be rate-limited on second request
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(req2, second, chain);
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_ShouldIgnoreXForwardedFor_WhenRemoteAddrIsNotATrustedProxy() throws Exception {
        ReflectionTestUtils.setField(filter, "maxRequests", 1);
        // No trusted-proxies configured (the default) — X-Forwarded-For must not be trusted.

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr("198.51.100.7");
        req1.addHeader("X-Forwarded-For", "1.1.1.1");

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr("198.51.100.7");
        // Attacker varies the spoofed header on every request to try to dodge the limit.
        req2.addHeader("X-Forwarded-For", "2.2.2.2");

        FilterChain chain = (req, resp) -> {};

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(req1, first, chain);
        assertThat(first.getStatus()).isNotEqualTo(429);

        // Same real remoteAddr — must still be rate-limited even though X-Forwarded-For changed.
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(req2, second, chain);
        assertThat(second.getStatus()).isEqualTo(429);
    }
}
