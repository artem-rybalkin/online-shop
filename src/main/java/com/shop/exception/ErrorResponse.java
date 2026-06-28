package com.shop.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for the {@code {timestamp, message}} JSON body returned by
 * every error response in the app, including the few that aren't routed through
 * GlobalExceptionHandler (e.g. AuthRateLimitFilter, which runs before DispatcherServlet).
 */
public final class ErrorResponse {

    private ErrorResponse() {
    }

    public static Map<String, Object> body(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", message);
        return body;
    }
}
