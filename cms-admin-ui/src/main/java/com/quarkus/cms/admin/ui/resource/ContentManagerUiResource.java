package com.quarkus.cms.admin.ui.resource;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side rendered content manager pages (HTMX-powered).
 * Provides listing, creation, and editing of content entries for all content types.
 */
@Path("/admin/content-manager")
@Produces(MediaType.TEXT_HTML)
public class ContentManagerUiResource {

    @Inject
    @Location("admin/content-manager/list.html")
    Template list;

    @Inject
    @Location("admin/content-manager/edit.html")
    Template edit;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    ContentManagerService contentManager;

    // ---- List ---- //

    @GET
    @Path("/{contentType}")
    public TemplateInstance listEntries(
        @PathParam("contentType") String contentType,
        @QueryParam("status") String status,
        @QueryParam("locale") @DefaultValue("en") String locale,
        @QueryParam("page") @DefaultValue("0") int page,
        @QueryParam("pageSize") @DefaultValue("20") int pageSize) {

        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) {
            throw new NotFoundException("Content type not found: " + contentType);
        }

        long total = contentManager.countEntries(contentType, status, locale);
        List<CmsEntry> entries = contentManager.listEntries(contentType, status, locale, page, pageSize);
        int pageCount = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;

        return list
            .data("title", ct.getDisplayName() != null ? ct.getDisplayName() : ct.getUid())
            .data("contentType", ct)
            .data("entries", entries)
            .data("total", total)
            .data("page", page)
            .data("pageSize", pageSize)
            .data("pageCount", pageCount)
            .data("status", status)
            .data("locale", locale);
    }

    // ---- Create Form ---- //

    @GET
    @Path("/{contentType}/create")
    public TemplateInstance createForm(
        @PathParam("contentType") String contentType,
        @QueryParam("locale") @DefaultValue("en") String locale) {

        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) {
            throw new NotFoundException("Content type not found: " + contentType);
        }

        return edit
            .data("title", "Create " + (ct.getDisplayName() != null ? ct.getDisplayName() : ct.getSingularName()))
            .data("contentType", ct)
            .data("entry", null)
            .data("isNew", true)
            .data("locale", locale)
            .data("fieldValues", new HashMap<String, Object>());
    }

    // ---- Edit Form ---- //

    @GET
    @Path("/{contentType}/{documentId}")
    public TemplateInstance editForm(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @QueryParam("locale") @DefaultValue("en") String locale,
        @QueryParam("saved") String saved,
        @QueryParam("published") String published) {

        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) {
            throw new NotFoundException("Content type not found: " + contentType);
        }

        CmsEntry entry = contentManager.getEntry(documentId, locale);
        if (entry == null) {
            throw new NotFoundException("Entry not found: " + documentId + " (locale: " + locale + ")");
        }

        Map<String, Object> fieldValues = entry.data != null ? entry.data : new HashMap<>();

        return edit
            .data("title", "Edit " + (ct.getDisplayName() != null ? ct.getDisplayName() : ct.getSingularName()))
            .data("contentType", ct)
            .data("entry", entry)
            .data("isNew", false)
            .data("locale", locale)
            .data("fieldValues", fieldValues)
            .data("saved", saved != null)
            .data("published", published != null);
    }

    // ---- Create (POST) ---- //

    @POST
    @Path("/{contentType}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createEntry(
        @PathParam("contentType") String contentType,
        @FormParam("locale") @DefaultValue("en") String locale,
        MultivaluedMap<String, String> formParams) {

        try {
            Map<String, Object> data = extractFieldData(formParams);
            CmsEntry entry = contentManager.createEntry(contentType, data, locale, 1L);

            return Response.seeOther(URI.create(
                "/admin/content-manager/" + contentType + "/" + entry.documentId + "?locale=" + locale))
                .build();
        } catch (Exception e) {
            ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
            Map<String, Object> fieldValues = extractFieldData(formParams);

            TemplateInstance form = edit
                .data("title", "Create " + (ct != null && ct.getDisplayName() != null ? ct.getDisplayName() : contentType))
                .data("contentType", ct)
                .data("entry", null)
                .data("isNew", true)
                .data("locale", locale)
                .data("fieldValues", fieldValues)
                .data("errorMessage", e.getMessage());

            return Response.status(Response.Status.BAD_REQUEST)
                .entity(form)
                .build();
        }
    }

    // ---- Update (POST) ---- //

    @POST
    @Path("/{contentType}/{documentId}/update")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateEntry(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @FormParam("locale") @DefaultValue("en") String locale,
        MultivaluedMap<String, String> formParams) {

        try {
            Map<String, Object> data = extractFieldData(formParams);
            contentManager.updateEntry(documentId, data, locale, 1L);

            return Response.seeOther(URI.create(
                "/admin/content-manager/" + contentType + "/" + documentId + "?locale=" + locale + "&saved=true"))
                .build();
        } catch (Exception e) {
            ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
            CmsEntry existing = contentManager.getEntry(documentId, locale);
            Map<String, Object> fieldValues = extractFieldData(formParams);

            TemplateInstance form = edit
                .data("title", "Edit " + (ct != null && ct.getDisplayName() != null ? ct.getDisplayName() : contentType))
                .data("contentType", ct)
                .data("entry", existing)
                .data("isNew", false)
                .data("locale", locale)
                .data("fieldValues", fieldValues)
                .data("errorMessage", e.getMessage());

            return Response.status(Response.Status.BAD_REQUEST)
                .entity(form)
                .build();
        }
    }

    // ---- Delete ---- //

    @POST
    @Path("/{contentType}/{documentId}/delete")
    public Response deleteEntry(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId) {

        try {
            contentManager.deleteDocument(documentId);
            return Response.seeOther(URI.create("/admin/content-manager/" + contentType + "?deleted=true"))
                .build();
        } catch (Exception e) {
            return Response.seeOther(URI.create(
                "/admin/content-manager/" + contentType + "?error=" + e.getMessage()))
                .build();
        }
    }

    // ---- Publish ---- //

    @POST
    @Path("/{contentType}/{documentId}/publish")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response publishEntry(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @FormParam("locale") @DefaultValue("en") String locale) {

        try {
            contentManager.publishEntry(documentId, locale, 1L);
        } catch (Exception e) {
            // Ignore publish errors for now
        }

        return Response.seeOther(URI.create(
            "/admin/content-manager/" + contentType + "/" + documentId + "?locale=" + locale + "&published=true"))
            .build();
    }

    // ---- Unpublish ---- //

    @POST
    @Path("/{contentType}/{documentId}/unpublish")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response unpublishEntry(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @FormParam("locale") @DefaultValue("en") String locale) {

        try {
            contentManager.unpublishEntry(documentId, locale);
        } catch (Exception e) {
            // Ignore
        }

        return Response.seeOther(URI.create(
            "/admin/content-manager/" + contentType + "/" + documentId + "?locale=" + locale))
            .build();
    }

    // ---- Helpers ---- //

    /**
     * Extracts form field data, handling complex keys like data[fieldName].
     */
    private Map<String, Object> extractFieldData(MultivaluedMap<String, String> formParams) {
        Map<String, Object> data = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : formParams.entrySet()) {
            String key = entry.getKey();
            if ("locale".equals(key) || "contentType".equals(key)) {
                continue;
            }

            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }

            // Handle data[fieldName] pattern
            if (key.startsWith("data[") && key.endsWith("]")) {
                String fieldName = key.substring(5, key.length() - 1);
                data.put(fieldName, values.get(0));
            } else if (!"locale".equals(key)) {
                data.put(key, values.get(0));
            }
        }

        return data;
    }
}
