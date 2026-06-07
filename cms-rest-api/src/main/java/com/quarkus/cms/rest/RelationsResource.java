package com.quarkus.cms.rest;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.query.ContentQueryEngine;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.i18n.service.LocaleService;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import com.quarkus.cms.rest.query.QueryParamParser;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Strapi v5-compatible Relations API: dedicated endpoints for resolving relation fields
 * on content entries.
 *
 * <p>Provides access to the resolved target entries for a relation field on a specific
 * content entry, with support for optional locale, status, and nested population.
 *
 * <h3>Endpoints:</h3>
 *
 * <ul>
 *   <li>GET /api/{contentType}/{documentId}/relations/{fieldName} — resolve targets for a relation field
 *   <li>GET /api/{contentType}/{documentId}/relations — list all relation field definitions
 * </ul>
 */
@Path("/api/{contentType}/{documentId}/relations")
@Produces({MediaType.APPLICATION_JSON, "application/vnd.api+json"})
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Relations API", description = "Dedicated endpoints for resolving relation fields on content entries")
public class RelationsResource {

    @Inject
    ContentQueryEngine queryEngine;

    @Inject
    CmsEntryRepository repository;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    LocaleService localeService;

    /**
     * Returns the resolved target entries for a specific relation field on a content entry.
     *
     * <p>This is the primary Relations API endpoint. It returns the actual target documents
     * that a relation field points to, rather than just their IDs. Supports optional locale
     * and status filtering of the target documents.
     *
     * @param contentType  the content type name (e.g., article, product)
     * @param documentId   the source document ID (UUID)
     * @param fieldName    the relation field name to resolve
     * @param locale       optional locale filter for target entries
     * @param status       optional status filter for target entries (published, draft)
     * @return the resolved relation targets wrapped in a Strapi-style response
     */
    @GET
    @Path("/{fieldName}")
    @Blocking
    @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.read")
    @Operation(
        summary = "Resolve relation field targets",
        description = "Returns the resolved target entries for a relation field on a content entry. " +
                      "Supports locale and status filtering of the resolved targets."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Resolved relation targets",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = StrapiSingleResponse.class))),
        @APIResponse(responseCode = "404", description = "Entry not found or relation not defined",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = StrapiErrorResponse.class)))
    })
    public Uni<Response> resolveRelationField(
            @Parameter(description = "Content type name", required = true)
            @PathParam("contentType") String contentType,
            @Parameter(description = "Source document ID", required = true)
            @PathParam("documentId") String documentId,
            @Parameter(description = "Relation field name", required = true)
            @PathParam("fieldName") String fieldName,
            @Parameter(description = "Locale filter for target entries")
            @QueryParam("locale") String locale,
            @Parameter(description = "Status filter for target entries (published, draft)")
            @QueryParam("status") String status) {

      return Uni.createFrom().item(() -> {
        // Resolve content type by UID or singular name
        ContentTypeDefinition ct = resolveContentTypeDefinition(contentType);
        if (ct == null) {
          return Response.status(Response.Status.NOT_FOUND)
                  .entity(StrapiErrorResponse.of(404, "NotFoundError",
                          "Unknown content type: " + contentType))
                  .build();
        }

        // Validate relation field exists
        RelationDefinition relDef = ct.getRelations().stream()
                .filter(r -> r.getFieldName().equals(fieldName))
                .findFirst()
                .orElse(null);
        if (relDef == null) {
          return Response.status(Response.Status.NOT_FOUND)
                  .entity(StrapiErrorResponse.of(404, "NotFoundError",
                          "Relation field '" + fieldName + "' not defined in content type '" + contentType + "'"))
                  .build();
        }

        // Find the source entry
        CmsEntry entry = repository.findByDocumentId(documentId);
        if (entry == null) {
          return Response.status(Response.Status.NOT_FOUND)
                  .entity(StrapiErrorResponse.of(404, "NotFoundError",
                          "Entry not found: " + documentId))
                  .build();
        }

        // Resolve the relation field targets
        List<CmsEntry> targets = queryEngine.resolveRelationField(
                entry, ct, fieldName, locale, status);

        if (targets.isEmpty()) {
          // Return empty result rather than 404 — the relation exists but has no targets
          if (isSingleTargetRelation(relDef)) {
            return Response.ok(new StrapiSingleResponse<>(null)).build();
          }
          return Response.ok(new StrapiSingleResponse<>(List.of())).build();
        }

        if (isSingleTargetRelation(relDef)) {
          // One-to-one: return single object
          Map<String, Object> populated = targets.get(0).getPopulatedData();
          return Response.ok(new StrapiSingleResponse<>(populated)).build();
        }

        // One-to-many, many-to-many: return array
        List<Map<String, Object>> populatedList = targets.stream()
                .map(CmsEntry::getPopulatedData)
                .collect(Collectors.toList());
        return Response.ok(new StrapiSingleResponse<>(populatedList)).build();
      });
    }

    /**
     * Lists all relation field definitions for a content type, including their types and targets.
     *
     * @param contentType the content type name
     * @return list of relation definitions
     */
    @GET
    @Blocking
    @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.read")
    @Operation(
        summary = "List relation fields",
        description = "Returns all relation field definitions for a content type, including their types and targets."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "List of relation definitions")
    })
    public Uni<Response> listRelationFields(
            @Parameter(description = "Content type name", required = true)
            @PathParam("contentType") String contentType) {

        return Uni.createFrom().item(() -> {
            ContentTypeDefinition ct = resolveContentTypeDefinition(contentType);
            if (ct == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(StrapiErrorResponse.of(404, "NotFoundError",
                                "Unknown content type: " + contentType))
                        .build();
            }

            List<Map<String, Object>> relations = ct.getRelations().stream()
                    .map(rel -> Map.<String, Object>of(
                            "fieldName", rel.getFieldName(),
                            "type", rel.getType().name(),
                            "target", rel.getTarget(),
                            "bidirectional", rel.isBidirectional(),
                            "morph", rel.isMorph()
                    ))
                    .collect(Collectors.toList());

            return Response.ok(new StrapiSingleResponse<>(relations)).build();
        });
    }

    private boolean isSingleTargetRelation(RelationDefinition relDef) {
        if (relDef == null) return false;
        return switch (relDef.getType()) {
            case ONE_TO_ONE, MANY_TO_ONE, MORPH_TO_ONE -> true;
            default -> false;
        };
    }

  /**
   * Resolves a content-type name (which could be a UID or a singular name) to a
   * {@link ContentTypeDefinition}.
   *
   * <p>Tries exact UID match first, then falls back to matching by singular name
   * across all registered content types.
   */
  private ContentTypeDefinition resolveContentTypeDefinition(String name) {
    Objects.requireNonNull(name, "contentType must not be null");
    // Try exact UID match first
    ContentTypeDefinition ct = schemaStorageService.getContentType(name);
    if (ct != null) return ct;
    // Fallback: match by singular name
    return schemaStorageService.getAllContentTypes().stream()
        .filter(c -> name.equals(c.getSingularName()))
        .findFirst()
        .orElse(null);
  }
}
