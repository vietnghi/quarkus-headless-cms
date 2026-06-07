package com.quarkus.cms.admin.api.filter;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter for admin API endpoints.
 *
 * Limits requests to /admin/* endpoints based on client IP address
 * to prevent abuse. Uses a token-bucket-like approach with a configurable
 * max requests per window.
 *
 * Configuration (via system properties / env vars):
 * <ul>
 *   <li>{@code cms.admin.rate-limit.max-requests} — max requests per window (default: 100)</li>
 *   <li>{@code cms.admin.rate-limit.window-seconds} — window duration in seconds (default: 60)</li>
 * </ul>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AdminRateLimitingFilter implements ContainerRequestFilter {

    /**
     * Default max requests per IP per window.
     */
    static final int DEFAULT_MAX_REQUESTS = 100;

    /**
     * Default window duration in seconds.
     */
    static final int DEFAULT_WINDOW_SECONDS = 60;

    private final int maxRequests;
    private final long windowMillis;

    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    public AdminRateLimitingFilter() {
        this.maxRequests = Integer.getInteger("cms.admin.rate-limit.max-requests", DEFAULT_MAX_REQUESTS);
        this.windowMillis = Long.getLong("cms.admin.rate-limit.window-seconds", DEFAULT_WINDOW_SECONDS) * 1000L;
        Log.infof("Admin rate limiting initialized: max %d requests per %d seconds",
            maxRequests, windowMillis / 1000);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Only apply to /admin/ endpoints
        if (!path.startsWith("admin/")) {
            return;
        }

        // Skip rate limiting for GET requests to static-like endpoints
        if (path.startsWith("admin-panel/") || path.startsWith("admin/content-types/")
                || path.startsWith("admin/content-manager/")) {
            String method = requestContext.getMethod();
            if ("GET".equals(method)) {
                return;
            }
        }

        String clientIp = getClientIp(requestContext);
        long now = System.currentTimeMillis();

        RateLimitEntry entry = rateLimitMap.compute(clientIp, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > windowMillis) {
                // Start a new window
                return new RateLimitEntry(now, new AtomicInteger(1));
            }
            // Increment within existing window
            existing.count.incrementAndGet();
            return existing;
        });

        // Check if exceeded
        if (entry.count.get() > maxRequests) {
            Log.warnf("Rate limit exceeded for IP %s on %s (count: %d)", clientIp, path, entry.count.get());
            requestContext.abortWith(
                Response.status(429, "Too Many Requests")
                    .header("Retry-After", String.valueOf(windowMillis / 1000))
                    .entity(Map.of(
                        "error", "Too many requests. Please try again later.",
                        "statusCode", 429
                    ))
                    .build()
            );
        }
    }

    /**
     * Extracts the client IP from the request, checking X-Forwarded-For first.
     */
    private String getClientIp(ContainerRequestContext requestContext) {
        String xForwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String ip = xForwardedFor.split(",")[0].trim();
            if (!ip.isBlank()) {
                return ip;
            }
        }
        String xRealIp = requestContext.getHeaderString("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        // Fallback to request source IP
        String remoteAddr = requestContext.getHeaderString("X-Original-Forwarded-For");
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return remoteAddr;
        }
        return "unknown";
    }

    /**
     * Internal rate limit entry tracking a window start and request count.
     */
    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger count;

        RateLimitEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
