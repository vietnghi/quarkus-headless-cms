package com.quarkus.cms.auth.security;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpServerResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Registers security-related HTTP response headers.
 *
 * <p>Adds CORS, Content-Security-Policy, XSS protection, and other security headers to every HTTP
 * response. Headers are applied via a Vert.x route filter registered during application startup.
 *
 * <h3>Headers set:</h3>
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}
 *   <li>{@code X-Frame-Options: DENY}
 *   <li>{@code X-XSS-Protection: 1; mode=block}
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}
 *   <li>{@code Permissions-Policy: camera=(), microphone=(), geolocation=()}
 *   <li>{@code Strict-Transport-Security: max-age=31536000; includeSubDomains} (HTTPS only)
 *   <li>{@code Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'
 *       'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self'}
 * </ul>
 */
@ApplicationScoped
public class CmsSecurityHeaders {

  /** Registers a global response filter that adds security headers to every response. */
  void registerSecurityHeaders(@Observes Filters filters) {
    filters.register(
        rc -> {
          HttpServerResponse response = rc.response();

          response.putHeader("X-Content-Type-Options", "nosniff");
          response.putHeader("X-Frame-Options", "DENY");
          response.putHeader("X-XSS-Protection", "1; mode=block");
          response.putHeader("Referrer-Policy", "strict-origin-when-cross-origin");
          response.putHeader(
              "Permissions-Policy", "camera=(), microphone=(), geolocation=()");

          // CSP: permissive enough for admin UI but restrictive for external scripts
          response.putHeader(
              "Content-Security-Policy",
              "default-src 'self'; "
                  + "script-src 'self' 'unsafe-inline'; "
                  + "style-src 'self' 'unsafe-inline'; "
                  + "img-src 'self' data: https:; "
                  + "font-src 'self'; "
                  + "connect-src 'self'");

          // HSTS (only meaningful over HTTPS)
          String scheme = rc.request().scheme();
          if ("https".equals(scheme)) {
            response.putHeader(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains");
          }

          // CORS headers for API access from browser-based clients
          response.putHeader("Access-Control-Allow-Origin", "*");
          response.putHeader(
              "Access-Control-Allow-Methods",
              "GET, POST, PUT, DELETE, PATCH, OPTIONS");
          response.putHeader(
              "Access-Control-Allow-Headers",
              "Content-Type, Authorization, X-Requested-With");
          response.putHeader("Access-Control-Max-Age", "86400");

          rc.next();
        },
        100);
  }
}
