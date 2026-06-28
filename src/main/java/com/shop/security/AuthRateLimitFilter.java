package com.shop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fixed-window rate limiter for authentication endpoints.
 * Tracks request counts per client IP; returns 429 when the limit is exceeded.
 * Configure via rate-limit.auth.* properties.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.auth.max-requests:10}")
    private int maxRequests;

    @Value("${rate-limit.auth.window-seconds:60}")
    private int windowSeconds;

    // X-Forwarded-For is only trusted when the request's own remote address is one of
    // these reverse proxies — otherwise a client could spoof the header to get a fresh
    // rate-limit bucket on every request and bypass the limit entirely.
    @Value("${rate-limit.auth.trusted-proxies:}")
    private String trustedProxies;

    @Autowired
    private ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, RateLimitEntry> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        evictExpiredEntries();
        String ip = resolveClientIp(request);

        if (!tryConsume(ip)) {
            String message = "Too many requests. Please try again in " + windowSeconds + " seconds.";
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.body(message)));
            return;
        }

        chain.doFilter(request, response);
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000;
        buckets.entrySet().removeIf(e -> now - e.getValue().windowStart > windowMs);
    }

    private boolean tryConsume(String ip) {
        long now = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000;

        RateLimitEntry entry = buckets.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart > windowMs) {
                return new RateLimitEntry(now, 1);
            }
            existing.count++;
            return existing;
        });

        return entry.count <= maxRequests;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (trustedProxies == null || trustedProxies.isBlank()) {
            return false;
        }
        Set<String> proxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        return proxies.contains(remoteAddr);
    }

    private static class RateLimitEntry {
        long windowStart;
        int count;

        RateLimitEntry(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
