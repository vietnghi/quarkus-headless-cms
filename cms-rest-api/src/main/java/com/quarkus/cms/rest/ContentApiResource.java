package com.quarkus.cms.rest;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.entity.EntityService;
import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.ContentQueryEngine;
import com.quarkus.cms.core.query.PopulateNode;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.core.util.AcceptLanguageParser;
import com.quarkus.cms.i18n.service.LocaleService;
import com.quarkus.cms.rest.dto.ContentEntryDto;
import com.quarkus.cms.rest.dto.PaginationMeta;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import com.quarkus.cms.rest.query.QueryParamParser;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Strapi v5-compatible Content API: dynamic CRUD endpoints for registered content types.
 *
 * <p>All endpoints are reactive (Mutiny types via RESTEasy Reactive). Content types are resolved
 * dynamically from the path — the same controller handles all content types at runtime.
 *
 * <h3>Endpoints:</h3>
 *
 * <ul>
 *   <li>GET /api/{contentType} — find many (filters, sort, pagination, population, locale)
 *   <li>GET /api/{contentType}/{documentId} — find one (with locale resolution)
 *   <li>POST /api/{contentType} — create (with locale support)
 *   <li>PUT /api/{contentType}/{documentId} — update (with locale support)
 *   <li>DELETE /api/{contentType}/{documentId} — delete
 *   <li>POST /api/{contentType}/{documentId}/publish — publish (with locale support)
 *   <li>POST /api/{contentType}/{documentId}/unpublish — unpublish (with locale support)
 * </ul>
 */
@Path("/api/{contentType}")
@Produces({MediaType.APPLICATION_JSON, "application/vnd.api+json"})
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Content API", description = "Strapi v5-compatible Content API endpoints for dynamic content-type CRUD")
public class ContentApiResource {

  @Inject EntityService entityService;

  @Inject CmsEntryRepository repository;

  @Inject ContentQueryEngine queryEngine;

  @Inject SchemaStorageService schemaStorageService;

  @Inject LocaleService localeService;

  @Context HttpHeaders httpHeaders;

  /**
   * Find many entries for a content type.
   *
   * <p>Supports Strapi v5 query parameters: filters, sort, pagination, populate, fields, locale,
   * publicationState.
   */
  @GET
  @Blocking
  @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.read")
  @Operation(
      summary = "Find many entries",
      description = "Returns a paginated list of entries for the given content type. Supports filters, sort, pagination, population, and locale filtering.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Paginated list of entries",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiCollectionResponse.class))),
      @APIResponse(responseCode = "400", description = "Invalid query parameters",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> findMany(
      @Parameter(description = "Content type name (e.g., article, product)", required = true)
      @PathParam("contentType") String contentType,
      @Context UriInfo uriInfo) {

    return Uni.createFrom()
        .item(() -> {
          Map<String, String> params = toSingleValueMap(uriInfo);
          CmsQuery query = QueryParamParser.parse(contentType, params);

          // Default to published status for public API
          if (query.getStatus() == null) {
            query.setStatus("published");
          }

          // Resolve locale: explicit query param > Accept-Language header > default locale
          String resolvedLocale = resolveRequestLocale(params.get("locale"));
          query.setLocale(resolvedLocale);

          List<CmsEntry> entries = queryEngine.list(query);
          long total = queryEngine.count(query);

          List<ContentEntryDto> dtos =
              entries.stream().map(ContentEntryDto::from).toList();

          // Apply field selection if specified
          if (query.getFields() != null && !query.getFields().isEmpty()) {
            dtos = dtos.stream()
                .map(dto -> applyFieldSelection(dto, query.getFields()))
                .toList();
          }

          PaginationMeta pagination =
              new PaginationMeta(query.getPage(), query.getPageSize(), total);

          return Response.ok(new StrapiCollectionResponse<>(dtos, pagination)).build();
        });
  }

  /** Find a single entry by document ID. */
  @GET
  @Path("/{documentId}")
  @Blocking
  @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.read")
  @Operation(
      summary = "Find one entry",
      description = "Returns a single content entry by its document ID. Supports locale and status query parameters.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Entry found",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class))),
      @APIResponse(responseCode = "404", description = "Entry not found",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> findOne(
      @Parameter(description = "Content type name", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @Parameter(description = "Document ID (UUID)", required = true)
      @PathParam("documentId") @NotBlank String documentId,
      @Parameter(description = "Locale code (e.g., en, fr). If omitted, resolved from Accept-Language header.")
      @QueryParam("locale") String locale,
      @Parameter(description = "Entry status filter (published, draft)")
      @QueryParam("status") String status,
      @Parameter(description = "Relation fields to populate (e.g., 'author' or 'author,category' or '*')")
      @QueryParam("populate") String populate,
      @Context UriInfo uriInfo) {

    return Uni.createFrom()
        .item(() -> {
          // Resolve locale: explicit query param > Accept-Language > default locale
          String resolvedLocale = resolveRequestLocale(locale);

          CmsEntry entry;
          if (status != null) {
            entry = CmsEntry.findByDocumentId(documentId, status, resolvedLocale);
          } else {
            entry = repository.findPublished(documentId, resolvedLocale);
            if (entry == null) {
              entry = repository.findDraft(documentId, resolvedLocale);
            }
          }

          if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError",
                    "Entry not found: " + documentId + " for locale: " + resolvedLocale))
                .build();
          }

          // Apply population if requested
          if (populate != null && !populate.isBlank()) {
            List<PopulateNode> populateNodes = QueryParamParser.parsePopulate(populate);
            if (!populateNodes.isEmpty()) {
              ContentTypeDefinition ctDef = schemaStorageService.getContentType(contentType);
              if (ctDef != null) {
                queryEngine.populateEntry(entry, ctDef, populateNodes);
              }
            }
          }

          ContentEntryDto dto = ContentEntryDto.from(entry);

          // Apply field selection if specified via query param
          // (fields param on findOne currently comes via the URI info, not an explicit param)
          String fieldsParam = uriInfo != null ? uriInfo.getQueryParameters().getFirst("fields") : null;
          if (fieldsParam != null && !fieldsParam.isBlank()) {
            // Single field
            dto = applyFieldSelection(dto, java.util.Set.of(fieldsParam));
          }

          return Response.ok(new StrapiSingleResponse<>(dto)).build();
        });
  }

  /** Create a new content entry. */
  @POST
  @Blocking
  @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.create")
  @Operation(
      summary = "Create a new entry",
      description = "Creates a new content entry of the specified content type with the provided field data. The entry is created in draft status by default.")
  @APIResponses({
      @APIResponse(responseCode = "201", description = "Entry created",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class))),
      @APIResponse(responseCode = "400", description = "Validation error",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> create(
      @Parameter(description = "Content type name", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @RequestBody(description = "Entry data with content-type fields. Accepts arbitrary key-value pairs that map to the content-type schema fields.", required = true,
          content = @Content(schema = @Schema(implementation = Map.class)))
      @NotNull @Size(min = 1) Map<String, Object> body,
      @Parameter(description = "Locale code (e.g., en, fr). If omitted, resolved from Accept-Language header.")
      @QueryParam("locale") String locale) {

    return Uni.createFrom()
        .item(() -> {
          // Resolve locale: explicit query param > Accept-Language header > default locale
          String resolvedLocale = resolveRequestLocale(locale);

          // Validate body is not empty
          Map<String, Object> data = extractDataFields(body);
          if (data.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError",
                    "Request body must contain at least one data field"))
                .build();
          }

          CmsEntry entry = repository.create(contentType, data, resolvedLocale);
          Log.infof("Created entry: %s (type=%s, locale=%s)", entry.documentId, contentType, resolvedLocale);
          return Response.status(Response.Status.CREATED)
              .entity(new StrapiSingleResponse<>(ContentEntryDto.from(entry)))
              .build();
        });
  }

  /** Update an existing content entry. */
  @PUT
  @Path("/{documentId}")
  @Blocking
  @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.update")
  @Operation(
      summary = "Update an entry",
      description = "Updates an existing content entry by document ID. Only updates the draft version — call publish to make changes live.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Entry updated",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class))),
      @APIResponse(responseCode = "400", description = "Validation error or entry not found",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> update(
      @Parameter(description = "Content type name", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @Parameter(description = "Document ID", required = true)
      @PathParam("documentId") @NotBlank String documentId,
      @RequestBody(description = "Updated entry data", required = true)
      @NotNull @Size(min = 1) Map<String, Object> body,
      @Parameter(description = "Locale code (e.g., en, fr). If omitted, resolved from Accept-Language header.")
      @QueryParam("locale") String locale) {

    return Uni.createFrom()
        .item(() -> {
          Map<String, Object> data = extractDataFields(body);
          if (data.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError",
                    "Request body must contain at least one data field"))
                .build();
          }

          String resolvedLocale = resolveRequestLocale(locale);
          try {
            CmsEntry entry = repository.update(documentId, data, null, resolvedLocale);
            Log.infof("Updated entry: %s (locale=%s)", documentId, resolvedLocale);
            return Response.ok(new StrapiSingleResponse<>(ContentEntryDto.from(entry))).build();
          } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
          }
        });
  }

  /** Delete a content entry (all versions). */
  @DELETE
  @Path("/{documentId}")
  @Blocking
  @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.delete")
  @Operation(
      summary = "Delete an entry",
      description = "Permanently deletes all versions of a content entry by document ID.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Entry deleted"),
      @APIResponse(responseCode = "404", description = "Entry not found",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> delete(
      @Parameter(description = "Content type name", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @Parameter(description = "Document ID", required = true)
      @PathParam("documentId") @NotBlank String documentId) {

    return Uni.createFrom()
        .item(() -> {
          CmsEntry entry = repository.findByDocumentId(documentId);
          if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError",
                    "Entry not found: " + documentId))
                .build();
          }

          repository.delete(documentId);
          Log.infof("Deleted entry: %s", documentId);

          return Response.ok(Map.of(
              "documentId", documentId,
              "deleted", true))
              .build();
        });
  }

  /** Publish a draft entry (makes it publicly visible). */
  @POST
  @Path("/{documentId}/publish")
  @Blocking
  @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.publish")
  @Operation(
      summary = "Publish an entry",
      description = "Publishes a draft entry, creating a published version that is visible via the public Content API.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Entry published",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class))),
      @APIResponse(responseCode = "400", description = "Draft entry not found",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> publish(
      @Parameter(description = "Content type name", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @Parameter(description = "Document ID", required = true)
      @PathParam("documentId") @NotBlank String documentId,
      @Parameter(description = "Locale code (e.g., en, fr). If omitted, resolved from Accept-Language header.")
      @QueryParam("locale") String locale) {

    return Uni.createFrom()
        .item(() -> {
          String resolvedLocale = resolveRequestLocale(locale);
          try {
            CmsEntry published = repository.publish(documentId, null, resolvedLocale);
            Log.infof("Published entry: %s (locale=%s)", documentId, resolvedLocale);
            return Response.ok(
                new StrapiSingleResponse<>(ContentEntryDto.from(published))).build();
          } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
          }
        });
  }

  /** Unpublish an entry (removes the published version). */
  @POST
  @Path("/{documentId}/unpublish")
  @Blocking
  @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.publish")
  @Operation(
      summary = "Unpublish an entry",
      description = "Removes the published version of an entry. The draft version remains intact.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Entry unpublished")
  })
  public Uni<Response> unpublish(
      @Parameter(description = "Content type name", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @Parameter(description = "Document ID", required = true)
      @PathParam("documentId") @NotBlank String documentId,
      @Parameter(description = "Locale code (e.g., en, fr). If omitted, resolved from Accept-Language header.")
      @QueryParam("locale") String locale) {

    return Uni.createFrom()
        .item(() -> {
          String resolvedLocale = resolveRequestLocale(locale);
          repository.unpublish(documentId, resolvedLocale);
          Log.infof("Unpublished entry: %s (locale=%s)", documentId, resolvedLocale);
          return Response.ok(Map.of(
              "documentId", documentId,
              "unpublished", true,
              "locale", resolvedLocale))
              .build();
        });
  }

  /**
   * Extracts content-type-specific field data from the request body, filtering out metadata
   * fields that are handled as path/query parameters.
   */
  private Map<String, Object> extractDataFields(Map<String, Object> body) {
    if (body == null) {
      return Map.of();
    }
    var data = new java.util.HashMap<>(body);
    data.remove("contentType");
    data.remove("locale");
    data.remove("status");
    data.remove("connect");
    data.remove("disconnect");
    // Unwrap a "data" envelope if present (Strapi v5 request format)
    if (data.containsKey("data") && data.get("data") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) data.get("data");
      data = new java.util.HashMap<>(inner);
    }
    return data;
  }

  /**
   * Applies field selection filtering to a DTO, removing any data fields not
   * included in the specified set of field names. Standard metadata fields
   * (id, documentId, locale, status, createdAt, updatedAt, publishedAt) are
   * always preserved.
   */
  private ContentEntryDto applyFieldSelection(ContentEntryDto dto, java.util.Set<String> fields) {
    if (fields == null || fields.isEmpty()) {
      return dto;
    }

    // Build a new map with only the requested fields + standard metadata
    Map<String, Object> filtered = new java.util.LinkedHashMap<>();

    // Always preserve standard metadata
    if (dto.getId() != null) filtered.put("id", dto.getId());
    if (dto.getDocumentId() != null) filtered.put("documentId", dto.getDocumentId());
    if (dto.getLocale() != null) filtered.put("locale", dto.getLocale());
    if (dto.getStatus() != null) filtered.put("status", dto.getStatus());
    if (dto.getCreatedAt() != null) filtered.put("createdAt", dto.getCreatedAt().toString());
    if (dto.getUpdatedAt() != null) filtered.put("updatedAt", dto.getUpdatedAt().toString());
    if (dto.getPublishedAt() != null) filtered.put("publishedAt", dto.getPublishedAt().toString());

    // Include only requested fields from the dynamic data
    for (String field : fields) {
      Object val = dto.getDataFields().get(field);
      if (val != null) {
        filtered.put(field, val);
      }
    }

    // Create a new DTO with filtered data
    ContentEntryDto filteredDto = new ContentEntryDto();
    filteredDto.setId(dto.getId());
    filteredDto.setDocumentId(dto.getDocumentId());
    filteredDto.setLocale(dto.getLocale());
    filteredDto.setStatus(dto.getStatus());
    filteredDto.setCreatedAt(dto.getCreatedAt());
    filteredDto.setUpdatedAt(dto.getUpdatedAt());
    filteredDto.setPublishedAt(dto.getPublishedAt());
    filteredDto.setCreatedById(dto.getCreatedById());
    filteredDto.setUpdatedById(dto.getUpdatedById());
    filteredDto.setPublishedById(dto.getPublishedById());

    for (Map.Entry<String, Object> entry : filtered.entrySet()) {
      String key = entry.getKey();
      // Skip standard fields that are set via setters
      if ("id".equals(key) || "documentId".equals(key) || "locale".equals(key)
          || "status".equals(key) || "createdAt".equals(key) || "updatedAt".equals(key)
          || "publishedAt".equals(key)) {
        continue;
      }
      filteredDto.setDataField(key, entry.getValue());
    }

    return filteredDto;
  }

  /**
   * Converts URI query parameters (with potentially multiple values) to a single-value map.
   */
  private Map<String, String> toSingleValueMap(UriInfo uriInfo) {
    Map<String, String> map = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
      List<String> values = entry.getValue();
      if (values != null && !values.isEmpty()) {
        map.put(entry.getKey(), values.get(0));
      }
    }
    return map;
  }

  /**
   * Resolves the locale for an incoming request.
   *
   * <p>Priority: explicit query parameter > Accept-Language header > configured default locale > "en".
   */
  private String resolveRequestLocale(String queryParamLocale) {
    if (queryParamLocale != null && !queryParamLocale.isBlank()) {
      return queryParamLocale;
    }
    String defaultLocale;
    try {
      defaultLocale = localeService.getDefaultLocale();
    } catch (Exception e) {
      defaultLocale = "en";
    }

    String acceptLanguage = httpHeaders.getHeaderString(HttpHeaders.ACCEPT_LANGUAGE);
    if (acceptLanguage != null && !acceptLanguage.isBlank()) {
      try {
        List<String> enabledLocales = localeService.getEnabledLocaleCodes();
        if (!enabledLocales.isEmpty()) {
          return AcceptLanguageParser.bestMatch(acceptLanguage, enabledLocales);
        }
      } catch (Exception ignored) {
        // Fall through to default
      }
    }

    return defaultLocale;
  }
}
