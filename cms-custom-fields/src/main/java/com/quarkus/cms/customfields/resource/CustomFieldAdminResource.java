package com.quarkus.cms.customfields.resource;

import com.quarkus.cms.customfields.CustomFieldDefinition;
import com.quarkus.cms.customfields.CustomFieldService;
import com.quarkus.cms.customfields.spi.CustomFieldType;
import com.quarkus.cms.customfields.spi.CustomFieldTypeRegistry;
import com.quarkus.cms.customfields.validation.FieldValidationFramework;
import com.quarkus.cms.customfields.validation.ValidationResult;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin REST resource for managing custom field definitions and their types.
 *
 * <p>Provides CRUD operations for custom field definitions, type registration querying, and field
 * value validation.
 */
@Path("/admin/custom-fields")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomFieldAdminResource {

  @Inject CustomFieldService customFieldService;

  @Inject CustomFieldTypeRegistry typeRegistry;

  @Inject FieldValidationFramework validationFramework;

  // ---- Custom Field Types (SPI) ----

  /** Lists all registered field types (built-in + custom). */
  @GET
  @Path("/types")
  public Response listFieldTypes() {
    List<Map<String, Object>> typeInfos =
        typeRegistry.getAllTypes().stream().map(this::typeToInfo).collect(Collectors.toList());
    return Response.ok(new StrapiSingleResponse<>(typeInfos)).build();
  }

  /** Gets metadata for a specific field type. */
  @GET
  @Path("/types/{typeId}")
  public Response getFieldType(@PathParam("typeId") String typeId) {
    CustomFieldType type = typeRegistry.getType(typeId);
    if (type == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", "Field type not found: " + typeId))
          .build();
    }
    return Response.ok(new StrapiSingleResponse<>(typeToInfo(type))).build();
  }

  // ---- Custom Field Definitions ----

  /** Lists all custom field definitions for a content type. */
  @GET
  @Path("/definitions")
  public Response listDefinitions(@QueryParam("contentType") String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              StrapiErrorResponse.of(
                  400, "ValidationError", "contentType query parameter is required"))
          .build();
    }
    List<CustomFieldDefinition> defs = customFieldService.getFields(contentType);
    return Response.ok(new StrapiSingleResponse<>(defs)).build();
  }

  /** Gets a single custom field definition. */
  @GET
  @Path("/definitions/{id}")
  public Response getDefinition(@PathParam("id") Long id) {
    CustomFieldDefinition def = CustomFieldDefinition.findById(id);
    if (def == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(
              StrapiErrorResponse.of(
                  404, "NotFoundError", "Custom field definition not found: " + id))
          .build();
    }
    return Response.ok(new StrapiSingleResponse<>(def)).build();
  }

  /** Creates a new custom field definition. */
  @POST
  @Path("/definitions")
  public Response createDefinition(Map<String, Object> body) {
    try {
      String contentType = (String) body.get("contentType");
      String fieldName = (String) body.get("fieldName");
      String label = (String) body.get("label");
      String fieldType = (String) body.get("fieldType");
      Object defaultValue = body.get("defaultValue");
      boolean required = Boolean.TRUE.equals(body.get("required"));
      String placeholder = (String) body.get("placeholder");
      String description = (String) body.get("description");
      int sortOrder =
          body.containsKey("sortOrder") ? ((Number) body.get("sortOrder")).intValue() : 0;

      // Validate that the field type exists
      if (fieldType != null && !typeRegistry.hasType(fieldType)) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                StrapiErrorResponse.of(
                    400,
                    "ValidationError",
                    "Unknown field type: "
                        + fieldType
                        + ". Available types: "
                        + typeRegistry.getTypeIds()))
            .build();
      }

      // Validate required fields
      if (contentType == null || fieldName == null || label == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                StrapiErrorResponse.of(
                    400, "ValidationError", "contentType, fieldName, and label are required"))
            .build();
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> validationRules =
          (Map<String, Object>) body.getOrDefault("validationRules", Map.of());

      CustomFieldDefinition def =
          customFieldService.defineField(
              contentType,
              fieldName,
              label,
              fieldType,
              defaultValue != null ? defaultValue.toString() : null,
              required,
              placeholder,
              null,
              sortOrder);

      // Store additional config (validation rules, type-specific options)
      if (!validationRules.isEmpty() || defaultValue != null) {
        def.config = new LinkedHashMap<>();
        if (!validationRules.isEmpty()) {
          def.config.putAll(validationRules);
        }
        if (defaultValue != null) {
          def.config.put("defaultValue", defaultValue);
        }
        def.persist();
      }

      if (description != null && !description.isBlank()) {
        def.setDescription(description);
        def.persist();
      }

      return Response.status(Response.Status.CREATED)
          .entity(new StrapiSingleResponse<>(def))
          .build();

    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }

  /** Updates an existing custom field definition. */
  @PUT
  @Path("/definitions/{id}")
  public Response updateDefinition(@PathParam("id") Long id, Map<String, Object> body) {
    try {
      String label = (String) body.get("label");
      String fieldType = (String) body.get("fieldType");
      Object defaultValue = body.get("defaultValue");
      boolean required =
          body.containsKey("required") ? Boolean.TRUE.equals(body.get("required")) : false;
      String placeholder = (String) body.get("placeholder");
      int sortOrder =
          body.containsKey("sortOrder") ? ((Number) body.get("sortOrder")).intValue() : 0;

      CustomFieldDefinition def =
          customFieldService.updateField(
              id,
              label,
              fieldType,
              defaultValue != null ? defaultValue.toString() : null,
              required,
              placeholder,
              null,
              sortOrder);

      if (body.containsKey("description")) {
        def.setDescription((String) body.get("description"));
        def.persist();
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> validationRules = (Map<String, Object>) body.get("validationRules");
      if (validationRules != null) {
        if (def.config == null) {
          def.config = new LinkedHashMap<>();
        }
        def.config.putAll(validationRules);
        def.persist();
      }

      return Response.ok(new StrapiSingleResponse<>(def)).build();

    } catch (IllegalArgumentException e) {
      if (e.getMessage().contains("not found")) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
            .build();
      }
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }

  /** Deletes a custom field definition and all its stored values. */
  @DELETE
  @Path("/definitions/{id}")
  public Response deleteDefinition(@PathParam("id") Long id) {
    try {
      customFieldService.removeField(id);
      return Response.ok(Map.of("deleted", true, "id", id)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
          .build();
    }
  }

  /** Validates a value against a field type's rules without persisting. */
  @POST
  @Path("/validate")
  public Response validateValue(Map<String, Object> body) {
    try {
      String fieldName = (String) body.get("fieldName");
      String fieldType = (String) body.get("fieldType");
      Object value = body.get("value");

      @SuppressWarnings("unchecked")
      Map<String, Object> config = (Map<String, Object>) body.getOrDefault("config", Map.of());

      if (fieldName == null || fieldType == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                StrapiErrorResponse.of(
                    400, "ValidationError", "fieldName and fieldType are required"))
            .build();
      }

      ValidationResult result =
          validationFramework.validateField(fieldName, fieldType, value, config, Map.of());
      if (result.isValid()) {
        return Response.ok(Map.of("valid", true)).build();
      }
      return Response.ok(Map.of("valid", false, "errors", result.getErrors())).build();

    } catch (Exception e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }

  // ---- Helpers ----

  private Map<String, Object> typeToInfo(CustomFieldType type) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("typeId", type.getTypeId());
    info.put("displayName", type.getDisplayName());
    info.put("category", type.getCategory());
    info.put("description", type.getDescription());
    info.put("valueType", type.getValueType().getSimpleName());
    info.put("defaultValue", type.getDefaultValue());
    info.put("supportsUnique", type.supportsUnique());
    info.put("supportsLengthConstraints", type.supportsLengthConstraints());
    info.put("supportsRangeConstraints", type.supportsRangeConstraints());
    info.put("supportsRegex", type.supportsRegex());
    info.put("supportsEnumValues", type.supportsEnumValues());
    info.put("pluginOptions", type.getPluginOptions());
    info.put("configSchema", type.getConfigSchema());
    return info;
  }
}
