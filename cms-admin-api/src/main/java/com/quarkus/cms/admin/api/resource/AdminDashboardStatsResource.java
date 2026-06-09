package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.audit.CmsAuditLog;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.review.EntryStageAssignment;
import com.quarkus.cms.review.WorkflowService;
import com.quarkus.cms.review.WorkflowStage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;

/**
 * Admin dashboard statistics endpoint.
 *
 * Provides aggregated content statistics, recent entries, and
 * system health information for the admin dashboard UI.
 *
 * All endpoints require admin authentication.
 */
@Path("/admin/dashboard-stats")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminDashboardStatsResource {

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    WorkflowService workflowService;

    /**
     * Get comprehensive dashboard statistics.
     */
    @GET
    @PermissionCheck("admin::dashboard.read")
    public Response getDashboardStats() {
        List<ContentTypeDefinition> contentTypes = schemaStorageService.getAllContentTypes();

        long totalContentTypes = contentTypes.size();
        long totalEntries = 0;
        long draftCount = 0;
        long publishedCount = 0;
        long unpublishedCount = 0;

        List<Map<String, Object>> contentTypeBreakdown = new ArrayList<>();

        for (ContentTypeDefinition ct : contentTypes) {
            String uid = ct.getUid();
            long ctDrafts = CmsEntry.count(
                "contentType = ?1 and status = ?2", uid, "draft");
            long ctPublished = CmsEntry.count(
                "contentType = ?1 and status = ?2", uid, "published");
            long ctUnpublished = CmsEntry.count(
                "contentType = ?1 and status = ?2", uid, "unpublished");

            draftCount += ctDrafts;
            publishedCount += ctPublished;
            unpublishedCount += ctUnpublished;

            Map<String, Object> ctStats = new HashMap<>();
            ctStats.put("uid", uid);
            ctStats.put("name", ct.getDisplayName() != null ? ct.getDisplayName() : ct.getSingularName());
            ctStats.put("kind", ct.getKind().name());
            ctStats.put("draftCount", ctDrafts);
            ctStats.put("publishedCount", ctPublished);
            ctStats.put("unpublishedCount", ctUnpublished);
            ctStats.put("total", ctDrafts + ctPublished + ctUnpublished);
            contentTypeBreakdown.add(ctStats);

            totalEntries += ctDrafts + ctPublished + ctUnpublished;
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("contentTypeCount", totalContentTypes);
        stats.put("totalEntries", totalEntries);
        stats.put("draftCount", draftCount);
        stats.put("publishedCount", publishedCount);
        stats.put("unpublishedCount", unpublishedCount);
        stats.put("contentTypeBreakdown", contentTypeBreakdown);

        // Workflow stage counts
        List<Map<String, Object>> workflowStats = new ArrayList<>();
        for (com.quarkus.cms.review.CmsWorkflow wf : workflowService.listActiveWorkflows()) {
            List<WorkflowStage> stages = workflowService.getStages(wf.id);
            List<Map<String, Object>> stageStats = stages.stream().map(stage -> {
                long count = EntryStageAssignment.countByStage(stage.id);
                Map<String, Object> s = new HashMap<>();
                s.put("stageId", stage.id);
                s.put("stageName", stage.name);
                s.put("color", stage.color);
                s.put("entryCount", count);
                return s;
            }).toList();

            Map<String, Object> wfStats = new HashMap<>();
            wfStats.put("workflowId", wf.id);
            wfStats.put("workflowName", wf.name);
            wfStats.put("isDefault", wf.isDefault);
            wfStats.put("stageCount", stages.size());
            wfStats.put("totalEntries", EntryStageAssignment.countByWorkflow(wf.id));
            wfStats.put("stages", stageStats);
            workflowStats.add(wfStats);
        }
        stats.put("workflows", workflowStats);

        // Recent audit activity
        long auditToday = CmsAuditLog.count("createdAt >= ?1",
            java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
        long auditWeek = CmsAuditLog.count("createdAt >= ?1",
            java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS));
        stats.put("auditActivity", Map.of(
            "last24h", auditToday,
            "last7d", auditWeek,
            "total", CmsAuditLog.count()
        ));

        return Response.ok(Map.of("data", stats)).build();
    }

    /**
     * Get recent entries across all content types.
     */
    @GET
    @Path("/recent-entries")
    @PermissionCheck("admin::dashboard.read")
    public Response getRecentEntries(
            @QueryParam("limit") @DefaultValue("10") int limit) {
        limit = Math.min(Math.max(limit, 1), 50);

        List<CmsEntry> recentEntries = CmsEntry.find(
            "order by updatedAt desc")
            .page(0, limit)
            .list();

        List<Map<String, Object>> entries = new ArrayList<>();
        for (CmsEntry entry : recentEntries) {
            Map<String, Object> e = new HashMap<>();
            e.put("documentId", entry.documentId);
            e.put("contentType", entry.contentType);
            e.put("status", entry.status);
            e.put("locale", entry.locale);
            e.put("updatedAt", entry.updatedAt != null ? entry.updatedAt.toString() : null);
            e.put("createdAt", entry.createdAt != null ? entry.createdAt.toString() : null);

            // Get title/name from data if available
            if (entry.data != null) {
                String title = (String) entry.data.getOrDefault("title",
                    entry.data.getOrDefault("name",
                        entry.data.getOrDefault("heading", "")));
                e.put("title", title);
            }
            entries.add(e);
        }

        return Response.ok(Map.of("data", entries)).build();
    }

    /**
     * Get system health status.
     */
    @GET
    @Path("/system-health")
    @PermissionCheck("admin::dashboard.read")
    public Response getSystemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();

        // Database status
        try {
            long entryCount = CmsEntry.count();
            health.put("database", Map.of(
                "status", "healthy",
                "entryCount", entryCount
            ));
        } catch (Exception e) {
            health.put("database", Map.of(
                "status", "unhealthy",
                "error", e.getMessage()
            ));
        }

        // Schema storage status
        try {
            List<ContentTypeDefinition> types = schemaStorageService.getAllContentTypes();
            health.put("schemaStorage", Map.of(
                "status", "healthy",
                "contentTypeCount", types.size()
            ));
        } catch (Exception e) {
            health.put("schemaStorage", Map.of(
                "status", "unhealthy",
                "error", e.getMessage()
            ));
        }

        health.put("system", Map.of(
            "status", "healthy",
            "version", "1.0.0-SNAPSHOT",
            "quarkusVersion", "3.36.1"
        ));

        return Response.ok(Map.of("data", health)).build();
    }
}
