package com.quarkus.cms.admin.ui.resource;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side rendered admin dashboard.
 * Provides the main landing page for the CMS admin panel with overview stats
 * and quick-access links to content types.
 */
@Path("/admin")
@Produces(MediaType.TEXT_HTML)
public class AdminDashboardResource {

    @Inject
    @Location("admin/dashboard.html")
    Template dashboard;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    ContentManagerService contentManager;

    @GET
    public TemplateInstance get() {
        List<ContentTypeDefinition> contentTypes = schemaStorageService.getAllContentTypes();

        Map<String, Object> stats = new HashMap<>();
        stats.put("contentTypeCount", contentTypes.size());

        // Count entries across all content types
        long totalEntries = 0;
        long draftCount = 0;
        long publishedCount = 0;
        for (ContentTypeDefinition ct : contentTypes) {
            // Count drafts for this content type (locale: en)
            long ctDrafts = CmsEntry.count(
                "contentType = ?1 and status = ?2 and locale = ?3",
                ct.getUid(), "draft", "en");
            long ctPublished = CmsEntry.count(
                "contentType = ?1 and status = ?2 and locale = ?3",
                ct.getUid(), "published", "en");
            draftCount += ctDrafts;
            publishedCount += ctPublished;
            totalEntries += ctDrafts + ctPublished;
        }
        stats.put("entryCount", totalEntries);
        stats.put("draftCount", draftCount);
        stats.put("publishedCount", publishedCount);

        return dashboard
            .data("title", "Dashboard")
            .data("contentTypes", contentTypes)
            .data("stats", stats);
    }
}
