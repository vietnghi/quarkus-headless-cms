package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.SchemaVersion;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.schema.storage.SchemaValidationException;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * REST resource for the admin Content-Type Builder.
 *
 * Provides full schema lifecycle management for content types and components:
 * list, get, create, update, delete, version history, and rollback.
 * Mirrors the Strapi v5 admin content-type-builder API.
 *
 * All endpoints require admin authentication with the appropriate
 * "admin::content-type-builder.*" permission.
 */
@Path("/admin/content-type-builder")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentTypeBuilderResource {

    @Inject
    SchemaStorageService schemaService;

    // ---- Content Types ----

    /**
     * Lists all registered content types.
     */
    @GET
    @Path("/content-types")
    @PermissionCheck("admin::content-type-builder.read")
    public Response listContentTypes() {
        List<ContentTypeDefinition> types = schemaService.getAllContentTypes();
        return Response.ok(new StrapiSingleResponse<>(types)).build();
    }

    /**
     * Gets a single content type by UID.
     */
    @GET
    @Path("/content-types/{uid}")
    @PermissionCheck("admin::content-type-builder.read")
    public Response getContentType(@PathParam("uid") String uid) {
        ContentTypeDefinition ct = schemaService.getContentType(uid);
        if (ct == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", "Content type not found: " + uid))
                .build();
        }
        return Response.ok(new StrapiSingleResponse<>(ct)).build();
    }

    /**
     * Creates or updates a content type definition.
     */
    @POST
    @Path("/content-types")
    @PermissionCheck("admin::content-type-builder.create")
    public Response createContentType(Map<String, Object> body) {
        try {
            String uid = (String) body.get("uid");
            if (uid == null || uid.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError", "uid is required"))
                    .build();
            }

            ContentTypeDefinition ct = deserializeContentType(body);
            String changeDescription = (String) body.getOrDefault("changeDescription", "Created via admin API");
            String createdBy = (String) body.getOrDefault("createdBy", "admin");

            ContentTypeDefinition saved = schemaService.registerContentType(ct, changeDescription, createdBy);
            return Response.status(Response.Status.CREATED)
                .entity(new StrapiSingleResponse<>(saved))
                .build();

        } catch (SchemaValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "Failed to create content type: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Updates an existing content type definition.
     */
    @PUT
    @Path("/content-types/{uid}")
    @PermissionCheck("admin::content-type-builder.update")
    public Response updateContentType(
        @PathParam("uid") String uid,
        Map<String, Object> body) {

        try {
            body.put("uid", uid);

            ContentTypeDefinition ct = deserializeContentType(body);
            String changeDescription = (String) body.getOrDefault("changeDescription", "Updated via admin API");
            String createdBy = (String) body.getOrDefault("createdBy", "admin");

            ContentTypeDefinition saved = schemaService.registerContentType(ct, changeDescription, createdBy);
            return Response.ok(new StrapiSingleResponse<>(saved)).build();

        } catch (SchemaValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "Failed to update content type: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Deletes a content type definition.
     */
    @DELETE
    @Path("/content-types/{uid}")
    @PermissionCheck("admin::content-type-builder.delete")
    public Response deleteContentType(@PathParam("uid") String uid) {
        ContentTypeDefinition ct = schemaService.getContentType(uid);
        if (ct == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", "Content type not found: " + uid))
                .build();
        }

        schemaService.deleteContentType(uid);
        return Response.ok(Map.of("deleted", true, "uid", uid)).build();
    }

    /**
     * Returns the version history for a content type.
     */
    @GET
    @Path("/content-types/{uid}/versions")
    @PermissionCheck("admin::content-type-builder.read")
    public Response getContentTypeVersions(@PathParam("uid") String uid) {
        ContentTypeDefinition ct = schemaService.getContentType(uid);
        if (ct == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", "Content type not found: " + uid))
                .build();
        }

        List<SchemaVersion> versions = schemaService.getVersionHistory(uid);
        return Response.ok(new StrapiSingleResponse<>(versions)).build();
    }

    /**
     * Rolls back a content type to its previous definition.
     */
    @POST
    @Path("/content-types/{uid}/actions/rollback")
    @PermissionCheck("admin::content-type-builder.update")
    public Response rollbackContentType(
        @PathParam("uid") String uid,
        Map<String, Object> body) {

        try {
            String reason = (String) body.getOrDefault("reason", null);
            String createdBy = (String) body.getOrDefault("createdBy", "admin");

            ContentTypeDefinition ct = schemaService.rollbackContentType(uid, reason, createdBy);
            return Response.ok(new StrapiSingleResponse<>(ct)).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    // ---- Components ----

    /**
     * Lists all registered components.
     */
    @GET
    @Path("/components")
    @PermissionCheck("admin::content-type-builder.read")
    public Response listComponents() {
        List<ComponentDefinition> components = schemaService.getAllComponents();
        return Response.ok(new StrapiSingleResponse<>(components)).build();
    }

    /**
     * Gets a single component by UID.
     */
    @GET
    @Path("/components/{uid}")
    @PermissionCheck("admin::content-type-builder.read")
    public Response getComponent(@PathParam("uid") String uid) {
        ComponentDefinition comp = schemaService.getComponent(uid);
        if (comp == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", "Component not found: " + uid))
                .build();
        }
        return Response.ok(new StrapiSingleResponse<>(comp)).build();
    }

    /**
     * Creates or updates a component definition.
     */
    @POST
    @Path("/components")
    @PermissionCheck("admin::content-type-builder.create")
    public Response createComponent(Map<String, Object> body) {
        try {
            String uid = (String) body.get("uid");
            if (uid == null || uid.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError", "uid is required"))
                    .build();
            }

            ComponentDefinition comp = deserializeComponent(body);
            String changeDescription = (String) body.getOrDefault("changeDescription", "Created via admin API");
            String createdBy = (String) body.getOrDefault("createdBy", "admin");

            ComponentDefinition saved = schemaService.registerComponent(comp, changeDescription, createdBy);
            return Response.status(Response.Status.CREATED)
                .entity(new StrapiSingleResponse<>(saved))
                .build();

        } catch (SchemaValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "Failed to create component: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Updates an existing component definition.
     */
    @PUT
    @Path("/components/{uid}")
    @PermissionCheck("admin::content-type-builder.update")
    public Response updateComponent(
        @PathParam("uid") String uid,
        Map<String, Object> body) {

        try {
            body.put("uid", uid);

            ComponentDefinition comp = deserializeComponent(body);
            String changeDescription = (String) body.getOrDefault("changeDescription", "Updated via admin API");
            String createdBy = (String) body.getOrDefault("createdBy", "admin");

            ComponentDefinition saved = schemaService.registerComponent(comp, changeDescription, createdBy);
            return Response.ok(new StrapiSingleResponse<>(saved)).build();

        } catch (SchemaValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", "Failed to update component: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Deletes a component definition.
     */
    @DELETE
    @Path("/components/{uid}")
    @PermissionCheck("admin::content-type-builder.delete")
    public Response deleteComponent(@PathParam("uid") String uid) {
        ComponentDefinition comp = schemaService.getComponent(uid);
        if (comp == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", "Component not found: " + uid))
                .build();
        }

        schemaService.deleteComponent(uid);
        return Response.ok(Map.of("deleted", true, "uid", uid)).build();
    }

    /**
     * Returns the version history for a component.
     */
    @GET
    @Path("/components/{uid}/versions")
    @PermissionCheck("admin::content-type-builder.read")
    public Response getComponentVersions(@PathParam("uid") String uid) {
        ComponentDefinition comp = schemaService.getComponent(uid);
        if (comp == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", "Component not found: " + uid))
                .build();
        }

        List<SchemaVersion> versions = schemaService.getVersionHistory(uid);
        return Response.ok(new StrapiSingleResponse<>(versions)).build();
    }

    // ---- Deserialization Helpers ----

    private ContentTypeDefinition deserializeContentType(Map<String, Object> raw) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.convertValue(raw, ContentTypeDefinition.class);
    }

    private ComponentDefinition deserializeComponent(Map<String, Object> raw) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.convertValue(raw, ComponentDefinition.class);
    }
}
