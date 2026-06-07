package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * System configuration endpoints for the admin panel.
 *
 * Provides CRUD for general settings, email configuration, and
 * security settings. Settings are persisted via an in-memory map
 * (backed by database in production via a CmsConfig entity).
 *
 * All endpoints require admin authentication with appropriate permissions.
 */
@Path("/admin/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminConfigResource {

    // In-memory settings store (would be replaced by DB-backed entity in production)
    private final Map<String, Object> settings = new HashMap<>();

    public AdminConfigResource() {
        // Default general settings
        settings.put("general", new HashMap<>(Map.of(
            "siteName", "Quarkus CMS",
            "siteUrl", "http://localhost:8080",
            "defaultLocale", "en",
            "locales", List.of("en"),
            "timezone", "UTC"
        )));

        // Default email settings
        settings.put("email", new HashMap<>(Map.of(
            "provider", "sendmail",
            "from", "noreply@example.com",
            "replyTo", "support@example.com",
            "smtpHost", "",
            "smtpPort", 587,
            "smtpSecure", false
        )));

        // Default security settings
        settings.put("security", new HashMap<>(Map.of(
            "jwtExpiresIn", "7d",
            "refreshTokenExpiresIn", "30d",
            "passwordMinLength", 8,
            "maxLoginAttempts", 5,
            "lockoutTimeMinutes", 15,
            "allowedOrigins", List.of("http://localhost:3000", "http://localhost:8080"),
            "corsEnabled", true
        )));
    }

    // ---- General Settings ---- //

    /**
     * Get all configuration categories.
     */
    @GET
    @PermissionCheck("admin::config.read")
    public Response getAllConfig() {
        return Response.ok(new StrapiSingleResponse<>(settings)).build();
    }

    /**
     * Get general site settings.
     */
    @GET
    @Path("/general")
    @PermissionCheck("admin::config.read")
    public Response getGeneralSettings() {
        return Response.ok(new StrapiSingleResponse<>(settings.getOrDefault("general", Map.of()))).build();
    }

    /**
     * Update general site settings.
     */
    @PUT
    @Path("/general")
    @PermissionCheck("admin::config.update")
    public Response updateGeneralSettings(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> general = (Map<String, Object>) settings.computeIfAbsent("general", k -> new HashMap<>());
        general.putAll(body);
        Log.info("Updated general settings");
        return Response.ok(new StrapiSingleResponse<>(general)).build();
    }

    // ---- Email Configuration ---- //

    /**
     * Get email configuration.
     */
    @GET
    @Path("/email")
    @PermissionCheck("admin::config.read")
    public Response getEmailConfig() {
        return Response.ok(new StrapiSingleResponse<>(settings.getOrDefault("email", Map.of()))).build();
    }

    /**
     * Update email configuration.
     */
    @PUT
    @Path("/email")
    @PermissionCheck("admin::config.update")
    public Response updateEmailConfig(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> email = (Map<String, Object>) settings.computeIfAbsent("email", k -> new HashMap<>());
        email.putAll(body);
        Log.info("Updated email configuration");
        return Response.ok(new StrapiSingleResponse<>(email)).build();
    }

    /**
     * Test email configuration by sending a test email (placeholder).
     */
    @POST
    @Path("/email/test")
    @PermissionCheck("admin::config.update")
    public Response testEmailConfig(Map<String, Object> body) {
        // In production, this would send a test email using the configured provider
        Log.info("Test email requested (simulated)");
        return Response.ok(Map.of("sent", true, "message", "Test email simulation successful")).build();
    }

    // ---- Security Settings ---- //

    /**
     * Get security settings.
     */
    @GET
    @Path("/security")
    @PermissionCheck("admin::config.read")
    public Response getSecuritySettings() {
        return Response.ok(new StrapiSingleResponse<>(settings.getOrDefault("security", Map.of()))).build();
    }

    /**
     * Update security settings.
     */
    @PUT
    @Path("/security")
    @PermissionCheck("admin::config.update")
    public Response updateSecuritySettings(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> security = (Map<String, Object>) settings.computeIfAbsent("security", k -> new HashMap<>());
        security.putAll(body);
        Log.info("Updated security settings");

        // If allowedOrigins changed, log the update
        if (body.containsKey("allowedOrigins")) {
            Log.infof("CORS allowed origins updated: %s", body.get("allowedOrigins"));
        }

        return Response.ok(new StrapiSingleResponse<>(security)).build();
    }

    /**
     * Regenerate JWT signing keys (placeholder).
     */
    @POST
    @Path("/security/regenerate-keys")
    @PermissionCheck("admin::config.update")
    public Response regenerateKeys() {
        // In production, this would regenerate JWT keys and log the event
        Log.info("JWT key regeneration requested (simulated)");
        return Response.ok(Map.of("regenerated", true, "message", "Key regeneration simulation successful. Restart required for changes to take effect.")).build();
    }
}
