package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST resource for the admin Content Manager.
 *
 * Provides CRUD and lifecycle management (publish/unpublish/discard)
 * for content entries, along with relation management, version history,
 * bulk operations, and import/export.
 * Mirrors the Strapi v5 admin content-manager API for frontend compatibility.
 *
 * All endpoints require admin authentication with appropriate permissions.
 */
@Path("/admin/content-manager")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentManagerResource {

    @Inject
    ContentManagerService contentManager;

    // ---- Collection Types ----

    /**
     * Lists entries for a collection-type content type.
     */
    @GET
    @Path("/collection-types/{contentType}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response listEntries(
        @PathParam("contentType") String contentType,
        @QueryParam("status") String status,
        @QueryParam("locale") @DefaultValue("en") String locale,
        @QueryParam("page") @DefaultValue("0") int page,
        @QueryParam("pageSize") @DefaultValue("20") int pageSize) {

        long total = contentManager.countEntries(contentType, status, locale);
        List<CmsEntry> entries = contentManager.listEntries(contentType, status, locale, page, pageSize);

        int pageCount = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("pageSize", pageSize);
        pagination.put("pageCount", pageCount);
        pagination.put("total", total);

        Map<String, Object> meta = new HashMap<>();
        meta.put("pagination", pagination);

        return Response.ok(new StrapiCollectionResponse<>(entries, meta)).build();
    }

    /**
     * Retrieves a single entry by document ID.
     */
    @GET
    @Path("/collection-types/{contentType}/{documentId}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response getEntry(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @QueryParam("locale") @DefaultValue("en") String locale) {

        CmsEntry entry = contentManager.getEntry(documentId, locale);
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError",
                    "Entry not found: " + documentId + " (locale: " + locale + ")"))
                .build();
        }
        return Response.ok(new StrapiSingleResponse<>(entry)).build();
    }

    /**
     * Creates a new draft entry for the given content type.
     */
    @POST
    @Path("/collection-types/{contentType}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.create")
    public Response createEntry(
        @PathParam("contentType") String contentType,
        Map<String, Object> body) {

        try {
            Map<String, Object> data = extractData(body);
            String locale = extractLocale(body);
            Long userId = extractUserId(body);

            CmsEntry entry = contentManager.createEntry(contentType, data, locale, userId);
            return Response.status(Response.Status.CREATED)
                .entity(new StrapiSingleResponse<>(entry)).build();

        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Updates an existing draft entry's data.
     */
    @PUT
    @Path("/collection-types/{contentType}/{documentId}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response updateEntry(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        Map<String, Object> body) {

        try {
            Map<String, Object> data = extractData(body);
            String locale = extractLocale(body);
            Long userId = extractUserId(body);

            CmsEntry entry = contentManager.updateEntry(documentId, data, locale, userId);
            return Response.ok(new StrapiSingleResponse<>(entry)).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    /**
     * Deletes an entire document (all versions/statuses).
     */
    @DELETE
    @Path("/collection-types/{contentType}/{documentId}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.delete")
    public Response deleteEntry(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId) {

        long deleted = contentManager.deleteDocument(documentId);
        return Response.ok(Map.of(
            "deleted", deleted > 0,
            "documentId", documentId
        )).build();
    }

    // ---- Bulk Operations ---- //

    /**
     * Bulk publishes multiple entries by document ID.
     */
    @POST
    @Path("/collection-types/{contentType}/actions/bulkPublish")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response bulkPublish(
        @PathParam("contentType") String contentType,
        Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<String> documentIds = (List<String>) body.get("documentIds");
        if (documentIds == null || documentIds.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "documentIds is required"))
                .build();
        }

        String locale = extractLocale(body);
        Long userId = extractUserId(body);

        List<Map<String, Object>> results = contentManager.bulkPublish(documentIds, locale, userId);
        return Response.ok(Map.of("data", results)).build();
    }

    /**
     * Bulk unpublishes multiple entries by document ID.
     */
    @POST
    @Path("/collection-types/{contentType}/actions/bulkUnpublish")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response bulkUnpublish(
        @PathParam("contentType") String contentType,
        Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<String> documentIds = (List<String>) body.get("documentIds");
        if (documentIds == null || documentIds.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "documentIds is required"))
                .build();
        }

        String locale = extractLocale(body);

        List<Map<String, Object>> results = contentManager.bulkUnpublish(documentIds, locale);
        return Response.ok(Map.of("data", results)).build();
    }

    /**
     * Bulk deletes multiple entries by document ID.
     */
    @POST
    @Path("/collection-types/{contentType}/actions/bulkDelete")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.delete")
    public Response bulkDelete(
        @PathParam("contentType") String contentType,
        Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<String> documentIds = (List<String>) body.get("documentIds");
        if (documentIds == null || documentIds.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "documentIds is required"))
                .build();
        }

        List<Map<String, Object>> results = contentManager.bulkDelete(documentIds);
        return Response.ok(Map.of("data", results)).build();
    }

    // ---- Import / Export ---- //

    /**
     * Exports all entries for a content type as JSON.
     */
    @GET
    @Path("/collection-types/{contentType}/actions/export")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response exportEntries(
        @PathParam("contentType") String contentType,
        @QueryParam("locale") @DefaultValue("en") String locale,
        @QueryParam("status") String status) {

        List<CmsEntry> entries = contentManager.listAllEntries(contentType, locale);
        // If status filter specified, apply client-side
        if (status != null && !status.isBlank()) {
            String finalStatus = status.toLowerCase();
            entries = entries.stream()
                .filter(e -> finalStatus.equals(e.status))
                .toList();
        }
        return Response.ok(Map.of("data", entries, "contentType", contentType)).build();
    }

    /**
     * Imports entries into a content type from JSON.
     */
    @POST
    @Path("/collection-types/{contentType}/actions/import")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.create")
    public Response importEntries(
        @PathParam("contentType") String contentType,
        Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
        if (entries == null || entries.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "entries array is required"))
                .build();
        }

        String locale = extractLocale(body);
        String importMode = (String) body.getOrDefault("mode", "create"); // "create" or "upsert"

        List<Map<String, Object>> results = contentManager.importEntries(
            contentType, entries, locale, importMode);
        return Response.ok(Map.of("data", results)).build();
    }

    // ---- Lifecycle: Publish / Unpublish / Discard Draft ----

    /**
     * Publishes the current draft, creating an immutable version snapshot.
     */
    @POST
    @Path("/collection-types/{contentType}/{documentId}/actions/publish")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response publish(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        Map<String, Object> body) {

        try {
            String locale = extractLocale(body);
            Long userId = extractUserId(body);

            CmsEntry published = contentManager.publishEntry(documentId, locale, userId);
            return Response.ok(new StrapiSingleResponse<>(published)).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Unpublishes the currently published version.
     */
    @POST
    @Path("/collection-types/{contentType}/{documentId}/actions/unpublish")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response unpublish(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        Map<String, Object> body) {

        try {
            String locale = extractLocale(body);

            contentManager.unpublishEntry(documentId, locale);
            return Response.ok(Map.of("unpublished", true, "documentId", documentId)).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Discards (deletes) the current draft, preserving the published version.
     */
    @POST
    @Path("/collection-types/{contentType}/{documentId}/actions/discardDraft")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response discardDraft(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        Map<String, Object> body) {

        String locale = extractLocale(body);
        contentManager.discardDraft(documentId, locale);
        return Response.ok(Map.of("discarded", true, "documentId", documentId)).build();
    }

    // ---- Versions ----

    /**
     * Returns all versions (draft + published) for a document, newest first.
     */
    @GET
    @Path("/collection-types/{contentType}/{documentId}/versions")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response getVersions(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @QueryParam("locale") @DefaultValue("en") String locale) {

        List<CmsEntry> versions = contentManager.getVersions(documentId, locale);
        return Response.ok(new StrapiSingleResponse<>(versions)).build();
    }

    /**
     * Returns a specific published version by number.
     */
    @GET
    @Path("/collection-types/{contentType}/{documentId}/versions/{versionNumber}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response getVersion(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @PathParam("versionNumber") int versionNumber,
        @QueryParam("locale") @DefaultValue("en") String locale) {

        CmsEntry version = contentManager.getVersion(documentId, versionNumber, locale);
        if (version == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", "Version not found"))
                .build();
        }
        return Response.ok(new StrapiSingleResponse<>(version)).build();
    }

    // ---- Single Types ----

    /**
     * Retrieves the single-type entry for a given content type.
     */
    @GET
    @Path("/single-types/{contentType}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response getSingleType(
        @PathParam("contentType") String contentType,
        @QueryParam("locale") @DefaultValue("en") String locale) {

        CmsEntry entry = contentManager.getSingleTypeEntry(contentType, locale);
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError",
                    "No entry found for single type '" + contentType + "'"))
                .build();
        }
        return Response.ok(new StrapiSingleResponse<>(entry)).build();
    }

    /**
     * Creates or updates a single-type entry.
     */
    @PUT
    @Path("/single-types/{contentType}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response upsertSingleType(
        @PathParam("contentType") String contentType,
        Map<String, Object> body) {

        try {
            Map<String, Object> data = extractData(body);
            String locale = extractLocale(body);
            Long userId = extractUserId(body);

            CmsEntry entry = contentManager.upsertSingleType(contentType, data, locale, userId);
            return Response.ok(new StrapiSingleResponse<>(entry)).build();

        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Deletes the single-type entry for a content type.
     */
    @DELETE
    @Path("/single-types/{contentType}")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.delete")
    public Response deleteSingleType(
        @PathParam("contentType") String contentType,
        @QueryParam("locale") @DefaultValue("en") String locale) {

        CmsEntry entry = contentManager.getSingleTypeEntry(contentType, locale);
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError",
                    "No entry found for single type '" + contentType + "'"))
                .build();
        }

        long deleted = contentManager.deleteDocument(entry.documentId);
        return Response.ok(Map.of("deleted", deleted > 0, "contentType", contentType)).build();
    }

    // ---- Relations ----

    /**
     * Attaches a relation from a source document to a target document.
     */
    @POST
    @Path("/collection-types/{contentType}/{documentId}/relations")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response attachRelation(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        Map<String, Object> body) {

        try {
            String fieldName = (String) body.get("fieldName");
            String targetDocumentId = (String) body.get("targetDocumentId");
            String targetType = (String) body.get("targetType");
            int orderIndex = body.containsKey("orderIndex") ? ((Number) body.get("orderIndex")).intValue() : 0;

            if (fieldName == null || targetDocumentId == null || targetType == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "fieldName, targetDocumentId, and targetType are required"))
                    .build();
            }

            CmsRelation rel = contentManager.attachRelation(
                documentId, contentType, targetDocumentId, targetType, fieldName, orderIndex);
            return Response.status(Response.Status.CREATED)
                .entity(new StrapiSingleResponse<>(rel)).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Detaches a specific relation.
     */
    @DELETE
    @Path("/collection-types/{contentType}/{documentId}/relations")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response detachRelation(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        Map<String, Object> body) {

        String fieldName = (String) body.get("fieldName");
        String targetDocumentId = (String) body.get("targetDocumentId");

        if (fieldName == null || targetDocumentId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError",
                    "fieldName and targetDocumentId are required"))
                .build();
        }

        contentManager.detachRelation(documentId, fieldName, targetDocumentId);
        return Response.ok(Map.of("detached", true)).build();
    }

    /**
     * Lists relations for a source document and optional field filter.
     */
    @GET
    @Path("/collection-types/{contentType}/{documentId}/relations")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response getRelations(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        @QueryParam("fieldName") String fieldName) {

        if (fieldName != null && !fieldName.isBlank()) {
            List<CmsRelation> relations = contentManager.findRelations(documentId, fieldName);
            return Response.ok(Map.of("relations", relations)).build();
        }
        List<CmsRelation> relations = contentManager.findRelations(documentId, null);
        return Response.ok(Map.of("relations", relations)).build();
    }

    /**
     * Reorders relations for a given field.
     */
    @PUT
    @Path("/collection-types/{contentType}/{documentId}/relations/reorder")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response reorderRelations(
        @PathParam("contentType") String contentType,
        @PathParam("documentId") String documentId,
        Map<String, Object> body) {

        String fieldName = (String) body.get("fieldName");
        @SuppressWarnings("unchecked")
        List<String> orderedTargetIds = (List<String>) body.get("orderedTargetIds");

        if (fieldName == null || orderedTargetIds == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError",
                    "fieldName and orderedTargetIds are required"))
                .build();
        }

        contentManager.reorderRelations(documentId, fieldName, orderedTargetIds);
        return Response.ok(Map.of("reordered", true)).build();
    }

    // ---- Helper methods ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> body) {
        if (body.containsKey("data") && body.get("data") instanceof Map) {
            return (Map<String, Object>) body.get("data");
        }
        Map<String, Object> data = new HashMap<>(body);
        data.remove("locale");
        data.remove("userId");
        data.remove("status");
        data.remove("fieldName");
        data.remove("targetDocumentId");
        data.remove("targetType");
        data.remove("orderIndex");
        data.remove("orderedTargetIds");
        data.remove("documentIds");
        data.remove("entries");
        data.remove("mode");
        return data;
    }

    private String extractLocale(Map<String, Object> body) {
        if (body.containsKey("locale") && body.get("locale") instanceof String) {
            return (String) body.get("locale");
        }
        return "en";
    }

    private Long extractUserId(Map<String, Object> body) {
        if (body.containsKey("userId") && body.get("userId") instanceof Number) {
            return ((Number) body.get("userId")).longValue();
        }
        return 1L;
    }
}
