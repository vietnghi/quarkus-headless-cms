package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.audit.AuditService;
import com.quarkus.cms.audit.CmsAuditLog;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.rest.dto.PaginationMeta;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.*;

/**
 * Admin REST API for viewing and managing audit log entries.
 * <p>
 * Provides paginated listing with filtering, detail view, and log purging
 * for system administrators. Mirrors the Strapi admin audit log viewer.
 * </p>
 * All endpoints require the {@code admin::audit-logs.read} or
 * {@code admin::audit-logs.delete} permission.
 */
@Path("/admin/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminAuditLogResource {

    @Inject
    AuditService auditService;

    /**
     * Lists audit log entries with optional filters and pagination.
     *
     * @param action      filter by action type (e.g. CREATE, UPDATE, PUBLISH, DELETE)
     * @param userId      filter by acting user id
     * @param contentType filter by content type UID
     * @param startDate   ISO-8601 start date for createdAt range filter
     * @param endDate     ISO-8601 end date for createdAt range filter
     * @param page        one-based page number (default: 1)
     * @param pageSize    items per page (default: 25, max: 100)
     * @return paginated collection of audit log entries
     */
    @GET
    @PermissionCheck("admin::audit-logs.read")
    public Response listAuditLogs(
            @QueryParam("action") String action,
            @QueryParam("userId") Long userId,
            @QueryParam("contentType") String contentType,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("25") int pageSize) {

        // Convert to zero-based for the service
        int zeroBasedPage = Math.max(page - 1, 0);
        pageSize = Math.min(Math.max(pageSize, 1), 100);

        // Validate date formats
        if (startDate != null && !startDate.isBlank()) {
            try {
                Instant.parse(startDate);
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "Invalid startDate format: use ISO-8601 (e.g. 2026-01-01T00:00:00Z)"))
                    .build();
            }
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
                Instant.parse(endDate);
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "Invalid endDate format: use ISO-8601 (e.g. 2026-01-01T00:00:00Z)"))
                    .build();
            }
        }

        long total = auditService.countFiltered(action, userId, contentType, startDate, endDate);
        List<CmsAuditLog> logs = auditService.findFiltered(
            action, userId, contentType, startDate, endDate,
            zeroBasedPage, pageSize);

        List<Map<String, Object>> data = logs.stream()
            .map(this::auditLogToMap)
            .toList();

        PaginationMeta pagination = new PaginationMeta(page, pageSize, total);
        return Response.ok(new StrapiCollectionResponse<>(data, pagination)).build();
    }

    /**
     * Gets a single audit log entry by ID.
     *
     * @param id the audit log entry ID
     * @return the audit log entry detail
     */
    @GET
    @Path("/{id}")
    @PermissionCheck("admin::audit-logs.read")
    public Response getAuditLog(@PathParam("id") Long id) {
        CmsAuditLog log = CmsAuditLog.findById(id);
        if (log == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError",
                    "Audit log entry " + id + " not found"))
                .build();
        }
        return Response.ok(new StrapiSingleResponse<>(auditLogToMap(log))).build();
    }

    /**
     * Clears old audit log entries.
     * <p>
     * Requires the {@code admin::audit-logs.delete} permission. At least one
     * filter (action, userId, contentType, or olderThanDays) must be provided
     * to prevent accidental full-table purges.
     *
     * @param action        optional filter by action type
     * @param userId        optional filter by user id
     * @param contentType   optional filter by content type
     * @param olderThanDays delete entries older than this many days (required if no other filter)
     * @return summary of the delete operation
     */
    @DELETE
    @PermissionCheck("admin::audit-logs.delete")
    @Transactional
    public Response clearAuditLogs(
            @QueryParam("action") String action,
            @QueryParam("userId") Long userId,
            @QueryParam("contentType") String contentType,
            @QueryParam("olderThanDays") Integer olderThanDays) {

        if (olderThanDays == null || olderThanDays <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError",
                    "olderThanDays is required and must be a positive integer"))
                .build();
        }

        long deleted;
        if (action != null || userId != null || contentType != null) {
            deleted = auditService.deleteFiltered(action, userId, contentType, olderThanDays);
        } else {
            deleted = auditService.deleteOlderThan(olderThanDays);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("entriesRemoved", deleted);
        result.put("filters", Map.of(
            "action", action,
            "userId", userId,
            "contentType", contentType,
            "olderThanDays", olderThanDays
        ));
        return Response.ok(new StrapiSingleResponse<>(result)).build();
    }

    /**
     * Gets a summary of recent audit activity counts by action type.
     * Used by the dashboard to show "Recent audit activity".
     *
     * @return count summary grouped by action type
     */
    @GET
    @Path("/summary")
    @PermissionCheck("admin::dashboard.read")
    public Response getAuditSummary() {
        long total = CmsAuditLog.count();
        long recent24h = CmsAuditLog.count("createdAt >= ?1",
            Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS));
        long recent7d = CmsAuditLog.count("createdAt >= ?1",
            Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEntries", total);
        summary.put("last24h", recent24h);
        summary.put("last7d", recent7d);
        return Response.ok(new StrapiSingleResponse<>(summary)).build();
    }

    // ---- Helpers ----

    /**
     * Converts a CmsAuditLog entity into a standard map representation.
     */
    private Map<String, Object> auditLogToMap(CmsAuditLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.id);
        m.put("documentId", log.documentId);
        m.put("entryId", log.entryId);
        m.put("contentType", log.contentType);
        m.put("locale", log.locale);
        m.put("action", log.action);
        m.put("userId", log.userId);
        m.put("changes", log.changes);
        m.put("summary", log.summary);
        m.put("createdAt", log.createdAt != null ? log.createdAt.toString() : null);
        return m;
    }
}
