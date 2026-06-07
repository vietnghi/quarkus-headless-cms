package com.quarkus.cms.admin.ui.resource;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.SchemaVersion;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Server-side rendered content type management pages.
 * Displays registered content types, their schemas, and component definitions.
 */
@Path("/admin/content-types")
@Produces(MediaType.TEXT_HTML)
public class ContentTypeUiResource {

    @Inject
    @Location("admin/content-types/list.html")
    Template contentTypesList;

    @Inject
    @Location("admin/content-types/detail.html")
    Template contentTypeDetail;

    @Inject
    SchemaStorageService schemaStorageService;

    @GET
    public TemplateInstance list() {
        List<ContentTypeDefinition> contentTypes = schemaStorageService.getAllContentTypes();
        List<ComponentDefinition> components = schemaStorageService.getAllComponents();

        // Pre-filter for Qute (Qute doesn't support Java streams/lambdas)
        List<ContentTypeDefinition> collectionTypes = contentTypes.stream()
            .filter(ct -> ct.isCollectionType())
            .collect(Collectors.toList());
        List<ContentTypeDefinition> singleTypes = contentTypes.stream()
            .filter(ct -> ct.isSingleType())
            .collect(Collectors.toList());

        return contentTypesList
            .data("title", "Content Types")
            .data("contentTypes", contentTypes)
            .data("collectionTypes", collectionTypes)
            .data("singleTypes", singleTypes)
            .data("components", components);
    }

    @GET
    @Path("/{uid}")
    public TemplateInstance detail(@PathParam("uid") String uid) {
        ContentTypeDefinition ct = schemaStorageService.getContentType(uid);
        if (ct == null) {
            // Check if it's a component
            ComponentDefinition comp = schemaStorageService.getComponent(uid);
            if (comp == null) {
                // Not found — redirect to list
                throw new jakarta.ws.rs.NotFoundException("Content type not found: " + uid);
            }
            // It's a component
            return contentTypeDetail
                .data("title", comp.getDisplayName() != null ? comp.getDisplayName() : comp.getUid())
                .data("schema", comp)
                .data("isComponent", true)
                .data("versionHistory", List.of());
        }

        // Get version history
        List<SchemaVersion> versions = schemaStorageService.getVersionHistory(uid);

        return contentTypeDetail
            .data("title", ct.getDisplayName() != null ? ct.getDisplayName() : ct.getUid())
            .data("schema", ct)
            .data("isComponent", false)
            .data("versionHistory", versions);
    }
}
