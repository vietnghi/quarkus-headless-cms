package com.quarkus.cms.rest;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Schema introspection endpoints for discovering content-type and component definitions
 * at runtime.
 *
 * <p>Provides a Strapi-compatible content-type browser API, returning the full schema
 * definitions including fields, relations, components, and dynamic zones.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET /api/schemas — list all registered content-type schemas
 *   <li>GET /api/schemas/{uid} — get a single content-type schema by UID
 *   <li>GET /api/schemas/components — list all registered component schemas
 * </ul>
 */
@Path("/api/schemas")
@Produces({MediaType.APPLICATION_JSON, "application/vnd.api+json"})
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Schema Introspection",
     description = "Endpoints for discovering content-type and component schema definitions at runtime")
public class SchemaIntrospectionResource {

  @Inject
  SchemaStorageService schemaStorageService;

  /**
   * Returns a list of all registered content-type schema definitions.
   * Each entry includes the content-type metadata, fields, relations, and configuration.
   */
  @GET
  @Operation(
      summary = "List all content-type schemas",
      description = "Returns all registered content-type schema definitions with their fields, "
          + "relations, components, and configuration options.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "List of content-type schemas",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  })
  public Uni<Response> listSchemas() {
    return Uni.createFrom().item(() -> {
      List<ContentTypeDefinition> contentTypes = schemaStorageService.getAllContentTypes();
      List<Map<String, Object>> schemas = contentTypes.stream()
          .map(this::toSchemaMap)
          .collect(Collectors.toList());
      return Response.ok(new StrapiSingleResponse<>(schemas)).build();
    });
  }

  /**
   * Returns the full schema definition for a single content type by its UID.
   *
   * @param uid the content-type UID (e.g., {@code api::article.article})
   * @return the full schema definition
   */
  @GET
  @Path("/{uid}")
  @Operation(
      summary = "Get a single content-type schema",
      description = "Returns the full schema definition for a specific content type, "
          + "including its fields, relations, components, dynamic zones, and options.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Content-type schema found",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class))),
      @APIResponse(responseCode = "404", description = "Content-type not found",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> getSchema(
      @Parameter(description = "Content-type UID (e.g., api::article.article)", required = true)
      @PathParam("uid") String uid) {

    return Uni.createFrom().item(() -> {
      ContentTypeDefinition ct = schemaStorageService.getContentType(uid);
      if (ct == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(StrapiErrorResponse.of(404, "NotFoundError",
                "Content type not found: " + uid))
            .build();
      }
      return Response.ok(new StrapiSingleResponse<>(toSchemaMap(ct))).build();
    });
  }

  /**
   * Returns a list of all registered component schemas.
   */
  @GET
  @Path("/components")
  @Operation(
      summary = "List all component schemas",
      description = "Returns all registered component schema definitions.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "List of component schemas",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  })
  public Uni<Response> listComponents() {
    return Uni.createFrom().item(() -> {
      List<ComponentDefinition> components = schemaStorageService.getAllComponents();
      List<Map<String, Object>> schemas = components.stream()
          .map(this::toComponentMap)
          .collect(Collectors.toList());
      return Response.ok(new StrapiSingleResponse<>(schemas)).build();
    });
  }

  // ========================================================================
  // Internal mapping helpers
  // ========================================================================

  /**
   * Converts a {@link ContentTypeDefinition} into a serializable map structure
   * suitable for the response body.
   */
  private Map<String, Object> toSchemaMap(ContentTypeDefinition ct) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("uid", ct.getUid());
    map.put("kind", ct.getKind().name());
    map.put("singularName", ct.getSingularName());
    map.put("pluralName", ct.getPluralName());
    map.put("displayName", ct.getDisplayName());
    map.put("description", ct.getDescription());
    map.put("draftAndPublish", ct.isDraftAndPublish());
    map.put("localized", ct.isLocalized());

    // Fields
    List<Map<String, Object>> fields = ct.getFields().stream()
        .map(this::toFieldMap)
        .collect(Collectors.toList());
    map.put("fields", fields);

    // Relations
    List<Map<String, Object>> relations = ct.getRelations().stream()
        .map(this::toRelationMap)
        .collect(Collectors.toList());
    map.put("relations", relations);

    // Component UIDs
    map.put("components", ct.getComponents());

    // Dynamic zones
    List<Map<String, Object>> dynamicZones = ct.getDynamicZones().stream()
        .map(this::toDynamicZoneMap)
        .collect(Collectors.toList());
    map.put("dynamicZones", dynamicZones);

    return map;
  }

  /**
   * Converts a {@link FieldDefinition} to a serializable map.
   */
  private Map<String, Object> toFieldMap(FieldDefinition field) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", field.getName());
    map.put("type", field.getType().name());
    map.put("required", field.isRequired());
    map.put("unique", field.isUnique());
    map.put("default", field.getDefaultValue());
    map.put("minLength", field.getMinLength());
    map.put("maxLength", field.getMaxLength());
    map.put("min", field.getMin());
    map.put("max", field.getMax());
    return map;
  }

  /**
   * Converts a {@link RelationDefinition} to a serializable map.
   */
  private Map<String, Object> toRelationMap(RelationDefinition rel) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("fieldName", rel.getFieldName());
    map.put("type", rel.getType().name());
    map.put("target", rel.getTarget());
    map.put("bidirectional", rel.isBidirectional());
    map.put("morph", rel.isMorph());
    return map;
  }

  /**
   * Converts a {@link DynamicZoneDefinition} to a serializable map.
   */
  private Map<String, Object> toDynamicZoneMap(DynamicZoneDefinition dz) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", dz.getName());
    map.put("components", dz.getComponents());
    return map;
  }

  /**
   * Converts a {@link ComponentDefinition} to a serializable map.
   */
  private Map<String, Object> toComponentMap(ComponentDefinition comp) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("uid", comp.getUid());
    map.put("displayName", comp.getDisplayName());
    map.put("description", comp.getDescription());
    map.put("category", comp.getCategory());

    // Fields
    List<Map<String, Object>> fields = comp.getFields().stream()
        .map(this::toFieldMap)
        .collect(Collectors.toList());
    map.put("fields", fields);

    return map;
  }
}
