package com.quarkus.cms.i18n.resource;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.i18n.dto.LocaleDto;
import com.quarkus.cms.i18n.service.I18nService;
import com.quarkus.cms.i18n.service.LocaleService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin REST resource for internationalization (i18n) management.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Managing available locales (CRUD)</li>
 *   <li>Querying and managing entry localizations (translations)</li>
 *   <li>Inspecting content-type localization configuration</li>
 * </ul>
 *
 * All endpoints require admin authentication.
 */
@Path("/admin/i18n")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminI18nResource {

  @Inject
  LocaleService localeService;

  @Inject
  I18nService i18nService;

  @Inject
  SchemaStorageService schemaStorageService;

  // ========================================================================
  // Locale Management
  // ========================================================================

  /**
   * Lists all configured locales.
   */
  @GET
  @Path("/locales")
  public Response listLocales() {
    return Response.ok(Map.of("data", localeService.listLocales())).build();
  }

  /**
   * Gets a single locale by code.
   */
  @GET
  @Path("/locales/{code}")
  public Response getLocale(@PathParam("code") String code) {
    return localeService.getLocale(code)
        .map(dto -> Response.ok(Map.of("data", dto)).build())
        .orElse(Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "Locale not found: " + code))
            .build());
  }

  /**
   * Creates a new locale.
   */
  @POST
  @Path("/locales")
  public Response createLocale(LocaleDto dto) {
    try {
      LocaleDto created = localeService.createLocale(dto);
      return Response.status(Response.Status.CREATED)
          .entity(Map.of("data", created))
          .build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", e.getMessage()))
          .build();
    }
  }

  /**
   * Updates an existing locale.
   */
  @PUT
  @Path("/locales/{code}")
  public Response updateLocale(@PathParam("code") String code, LocaleDto dto) {
    try {
      LocaleDto updated = localeService.updateLocale(code, dto);
      return Response.ok(Map.of("data", updated)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", e.getMessage()))
          .build();
    }
  }

  /**
   * Deletes a locale.
   */
  @DELETE
  @Path("/locales/{code}")
  public Response deleteLocale(@PathParam("code") String code) {
    try {
      localeService.deleteLocale(code);
      return Response.ok(Map.of("deleted", true, "code", code)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", e.getMessage()))
          .build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", e.getMessage()))
          .build();
    }
  }

  /**
   * Returns the default locale.
   */
  @GET
  @Path("/locales/default")
  public Response getDefaultLocale() {
    String defaultCode = localeService.getDefaultLocale();
    return localeService.getLocale(defaultCode)
        .map(dto -> Response.ok(Map.of("data", dto)).build())
        .orElse(Response.ok(Map.of("data", Map.of("code", defaultCode, "displayName", defaultCode)))
            .build());
  }

  // ========================================================================
  // Entry Localizations
  // ========================================================================

  /**
   * Returns all localizations for a content entry.
   *
   * @param contentType the content type UID
   * @param documentId  the document ID
   */
  @GET
  @Path("/content-types/{contentType}/{documentId}/localizations")
  public Response getLocalizations(
      @PathParam("contentType") String contentType,
      @PathParam("documentId") String documentId) {

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("documentId", documentId);
    result.put("contentType", contentType);
    result.put("localizations", i18nService.getLocalizationsSummary(documentId));
    return Response.ok(Map.of("data", result)).build();
  }

  /**
   * Creates a new locale variant (translation) for an existing entry.
   *
   * <p>Request body:
   * <pre>
   * {
   *   "locale": "fr",
   *   "data": { "title": "Mon Article", "content": "..." }
   * }
   * </pre>
   */
  @POST
  @Path("/content-types/{contentType}/{documentId}/localizations")
  public Response createLocalization(
      @PathParam("contentType") String contentType,
      @PathParam("documentId") String documentId,
      Map<String, Object> body) {

    try {
      String targetLocale = (String) body.get("locale");
      String sourceLocale = (String) body.getOrDefault("sourceLocale", localeService.getDefaultLocale());
      @SuppressWarnings("unchecked")
      Map<String, Object> localizedData = (Map<String, Object>) body.getOrDefault("data", Map.of());
      Long userId = body.containsKey("userId")
          ? ((Number) body.get("userId")).longValue() : null;

      if (targetLocale == null || targetLocale.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "locale is required"))
            .build();
      }

      CmsEntry created = i18nService.createLocalization(
          documentId, sourceLocale, targetLocale, localizedData, userId);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("documentId", created.documentId);
      result.put("locale", created.locale);
      result.put("status", created.status);

      return Response.status(Response.Status.CREATED)
          .entity(Map.of("data", result))
          .build();

    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", e.getMessage()))
          .build();
    }
  }

  // ========================================================================
  // Content-Type i18n Configuration
  // ========================================================================

  /**
   * Lists all content types with their localization configuration.
   *
   * <p>Returns which content types and fields support localization.
   */
  @GET
  @Path("/content-types")
  public Response listContentTypeLocalizationConfig() {
    List<ContentTypeDefinition> allTypes = schemaStorageService.getAllContentTypes();

    List<Map<String, Object>> configs = allTypes.stream()
        .map(ct -> {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("uid", ct.getUid());
          entry.put("singularName", ct.getSingularName());
          entry.put("localized", ct.isLocalized());

          // Per-field localization flags
          List<Map<String, Object>> fieldConfigs = ct.getFields().stream()
              .map(f -> {
                Map<String, Object> fc = new LinkedHashMap<>();
                fc.put("name", f.getName());
                fc.put("type", f.getType().name());
                fc.put("localized", f.isLocalized());
                return fc;
              })
              .collect(Collectors.toList());
          entry.put("fields", fieldConfigs);

          // Available locales for this content type
          entry.put("availableLocales", localeService.getEnabledLocaleCodes());

          return entry;
        })
        .toList();

    return Response.ok(Map.of("data", configs)).build();
  }

  /**
   * Returns localization config for a single content type.
   */
  @GET
  @Path("/content-types/{contentType}")
  public Response getContentTypeLocalizationConfig(
      @PathParam("contentType") String contentType) {

    ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
    if (ct == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "Content type not found: " + contentType))
          .build();
    }

    Map<String, Object> config = new LinkedHashMap<>();
    config.put("uid", ct.getUid());
    config.put("localized", ct.isLocalized());

    List<Map<String, Object>> fieldConfigs = ct.getFields().stream()
        .map(f -> {
          Map<String, Object> fc = new LinkedHashMap<>();
          fc.put("name", f.getName());
          fc.put("type", f.getType().name());
          fc.put("localized", f.isLocalized());
          return fc;
        })
        .toList();
    config.put("fields", fieldConfigs);
    config.put("availableLocales", localeService.getEnabledLocaleCodes());

    return Response.ok(Map.of("data", config)).build();
  }
}
