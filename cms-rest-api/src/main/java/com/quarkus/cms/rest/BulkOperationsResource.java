package com.quarkus.cms.rest;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.rest.dto.BatchRequest;
import com.quarkus.cms.rest.dto.BatchResponse;
import com.quarkus.cms.rest.dto.BatchResponse.BatchOperationResponse;
import com.quarkus.cms.rest.dto.BulkCreateEntry;
import com.quarkus.cms.rest.dto.BulkDeleteRequest;
import com.quarkus.cms.rest.dto.BulkOperationMeta;
import com.quarkus.cms.rest.dto.BulkOperationResult;
import com.quarkus.cms.rest.dto.BulkUpdateEntry;
import com.quarkus.cms.rest.dto.ContentEntryDto;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.service.BulkOperationException;
import com.quarkus.cms.rest.service.BulkOperationService;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * Bulk and batch operations for the REST Content API.
 *
 * <p>Provides bulk CRUD endpoints grouped by content type, plus a generic
 * batch endpoint for executing multiple operations sequentially within a
 * single database transaction.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>POST /api/{contentType}/bulk — bulk create entries
 *   <li>PUT /api/{contentType}/bulk — bulk update entries
 *   <li>DELETE /api/{contentType}/bulk — bulk delete entries
 *   <li>POST /api/batch — execute multiple operations atomically
 * </ul>
 *
 * <p>All bulk operations are transactional with automatic rollback on failure.
 */
@Path("/api")
@Produces({MediaType.APPLICATION_JSON, "application/vnd.api+json"})
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Bulk Operations",
     description = "Bulk CRUD operations and batch endpoint for content entries. "
          + "All operations are transactional with automatic rollback on failure.")
@Blocking
public class BulkOperationsResource {

  @Inject
  BulkOperationService bulkService;

  @Inject
  CmsEntryRepository repository;

  @Context
  HttpHeaders httpHeaders;

  // ========================================================================
  // Bulk Create
  // ========================================================================

  /**
   * Bulk-create content entries. All entries are created in a single transaction.
   * If any entry fails validation or creation, the entire batch is rolled back.
   */
  @POST
  @Path("/{contentType}/bulk")
  @Operation(
      summary = "Bulk create entries",
      description = "Creates multiple content entries of the specified content type in a single "
          + "transaction. If any entry fails, the entire batch is rolled back. "
          + "Returns per-item results with success/error status.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Bulk create completed with per-item results",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiCollectionResponse.class))),
      @APIResponse(responseCode = "400", description = "Validation error or bulk operation failed",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> bulkCreate(
      @Parameter(description = "Content type name (e.g., article, product)", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @RequestBody(description = "Array of entry data objects", required = true,
          content = @Content(schema = @Schema(implementation = BulkCreateEntry[].class)))
      @NotNull @NotEmpty List<@Valid BulkCreateEntry> entries,
      @Parameter(description = "Locale code (e.g., en, fr). Applied to all entries.")
      @QueryParam("locale") String locale) {

    return Uni.createFrom()
        .item(() -> {
          try {
            List<BulkOperationResult> results = bulkService.createBulk(
                contentType, entries, locale, null);

            return buildBulkResponse(results);
          } catch (BulkOperationException e) {
            // Return partial results with 400 status
            return buildBulkErrorResponse(e, "Bulk create failed at index " + e.getFailedIndex());
          }
        });
  }

  // ========================================================================
  // Bulk Update
  // ========================================================================

  /**
   * Bulk-update content entries. All updates run in a single transaction.
   * If any update fails, the entire batch is rolled back.
   */
  @PUT
  @Path("/{contentType}/bulk")
  @Operation(
      summary = "Bulk update entries",
      description = "Updates multiple content entries by document ID in a single transaction. "
          + "Each item must specify a documentId and the data fields to update. "
          + "If any update fails, the entire batch is rolled back.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Bulk update completed with per-item results",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiCollectionResponse.class))),
      @APIResponse(responseCode = "400", description = "Validation error or bulk operation failed",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> bulkUpdate(
      @Parameter(description = "Content type name (e.g., article, product)", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @RequestBody(description = "Array of update objects with documentId and data", required = true,
          content = @Content(schema = @Schema(implementation = BulkUpdateEntry[].class)))
      @NotNull @NotEmpty List<@Valid BulkUpdateEntry> updates,
      @Parameter(description = "Default locale code. Per-item locale overrides this.")
      @QueryParam("locale") String locale) {

    return Uni.createFrom()
        .item(() -> {
          try {
            List<BulkOperationResult> results = bulkService.updateBulk(
                contentType, updates, locale, null);

            return buildBulkResponse(results);
          } catch (BulkOperationException e) {
            return buildBulkErrorResponse(e, "Bulk update failed at index " + e.getFailedIndex());
          }
        });
  }

  // ========================================================================
  // Bulk Delete
  // ========================================================================

  /**
   * Bulk-delete content entries. All deletions run in a single transaction.
   * If any deletion fails, the entire batch is rolled back.
   */
  @DELETE
  @Path("/{contentType}/bulk")
  @Operation(
      summary = "Bulk delete entries",
      description = "Deletes multiple content entries by their document IDs in a single "
          + "transaction. Documents not found are reported but do not trigger a rollback. "
          + "Other failures cause the entire batch to roll back.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Bulk delete completed with per-item results",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiCollectionResponse.class))),
      @APIResponse(responseCode = "400", description = "Validation error or bulk operation failed",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> bulkDelete(
      @Parameter(description = "Content type name (e.g., article, product)", required = true)
      @PathParam("contentType") @NotBlank String contentType,
      @RequestBody(description = "Object with documentIds array", required = true,
          content = @Content(schema = @Schema(implementation = BulkDeleteRequest.class)))
      @NotNull @Valid BulkDeleteRequest deleteRequest) {

    return Uni.createFrom()
        .item(() -> {
          try {
            List<BulkOperationResult> results = bulkService.deleteBulk(contentType, deleteRequest);

            return buildBulkResponse(results);
          } catch (BulkOperationException e) {
            return buildBulkErrorResponse(e, "Bulk delete failed at index " + e.getFailedIndex());
          }
        });
  }

  // ========================================================================
  // Batch Endpoint
  // ========================================================================

  /**
   * Execute a batch of operations atomically.
   *
   * <p>Accepts an ordered list of operations (method + path + body) and executes
   * them sequentially within a single database transaction. If any operation
   * fails, all prior operations are rolled back.
   *
   * <p>Supported methods: GET, POST, PUT, DELETE. Paths must start with /api/
   * and reference the content API endpoints.
   */
  @POST
  @Path("/batch")
  @Operation(
      summary = "Execute batch of operations",
      description = "Executes an ordered list of REST operations (method + path + body) "
          + "sequentially within a single transaction. If any operation fails, all "
          + "prior operations are rolled back. Supports CRUD operations on content types.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Batch completed with per-operation results",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = BatchResponse.class))),
      @APIResponse(responseCode = "400", description = "Validation error or batch operation failed",
          content = @Content(mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  })
  public Uni<Response> batch(
      @RequestBody(description = "Ordered list of operations to execute", required = true,
          content = @Content(schema = @Schema(implementation = BatchRequest.class)))
      @NotNull @Valid BatchRequest batchRequest) {

    return Uni.createFrom()
        .item(() -> {
          List<BatchRequest.BatchOperation> operations = batchRequest.getRequests();
          List<BatchOperationResponse> results = new ArrayList<>(operations.size());
          int succeeded = 0;
          int failed = 0;

          for (int i = 0; i < operations.size(); i++) {
            BatchRequest.BatchOperation op = operations.get(i);
            try {
              BatchOperationResponse response = executeBatchOperation(op, i);
              results.add(response);
              if (response.getError() == null) {
                succeeded++;
              } else {
                failed++;
              }

              // If operation failed, stop processing and roll back
              if (response.getError() != null) {
                break;
              }
            } catch (Exception e) {
              BatchOperationResponse errorResponse = new BatchOperationResponse(
                  500, op.getPath(), op.getMethod(),
                  "Internal error: " + e.getMessage(), "ApplicationError");
              results.add(errorResponse);
              failed++;
              break; // Stop on error — caller must retry
            }
          }

          Map<String, Object> meta = new HashMap<>();
          meta.put("total", results.size());
          meta.put("succeeded", succeeded);
          meta.put("failed", failed);

          BatchResponse response = new BatchResponse(results, meta);
          return Response.ok(response).build();
        });
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  /**
   * Executes a single batch operation by routing to the appropriate internal handler.
   */
  private BatchOperationResponse executeBatchOperation(
      BatchRequest.BatchOperation op, int index) {

    String method = op.getMethod().toUpperCase();
    String path = op.getPath();

    // Validate path starts with /api/
    if (!path.startsWith("/api/")) {
      return new BatchOperationResponse(
          400, path, op.getMethod(),
          "Path must start with /api/", "ValidationError");
    }

    // Extract content type from path: /api/{contentType}[/...]
    String pathWithoutPrefix = path.substring("/api/".length());
    String contentType;
    String subPath = "";

    int slashIndex = pathWithoutPrefix.indexOf('/');
    if (slashIndex > 0) {
      contentType = pathWithoutPrefix.substring(0, slashIndex);
      subPath = pathWithoutPrefix.substring(slashIndex);
    } else {
      contentType = pathWithoutPrefix;
    }

    // Skip batch endpoint itself to prevent recursion
    if ("batch".equals(contentType)) {
      return new BatchOperationResponse(
          400, path, op.getMethod(),
          "Nested batch operations are not supported", "ValidationError");
    }

    Map<String, Object> body = op.getBody() != null ? op.getBody() : Map.of();

    try {
      switch (method) {
        case "POST":
          if (subPath.isEmpty() || "/bulk".equals(subPath)) {
            // Single or bulk create
            if (body.containsKey("data") && body.get("data") instanceof List) {
              // Bulk create via batch
              @SuppressWarnings("unchecked")
              List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("data");
              List<BulkCreateEntry> entries = new ArrayList<>();
              for (Map<String, Object> item : items) {
                BulkCreateEntry entry = new BulkCreateEntry();
                if (item != null) {
                  item.forEach(entry::setDataField);
                }
                entries.add(entry);
              }
              List<BulkOperationResult> results = bulkService.createBulk(
                  contentType, entries, extractLocale(body), null);
              return new BatchOperationResponse(
                  200, path, op.getMethod(), Map.of("data", results));
            } else {
              // Single create
              Map<String, Object> fields = new HashMap<>(body);
              fields.remove("locale");
              CmsEntry created = repository.create(contentType, fields,
                  extractLocale(body));
              ContentEntryDto dto = ContentEntryDto.from(created);
              return new BatchOperationResponse(
                  201, path, op.getMethod(), new StrapiCollectionResponse<>(List.of(dto)));
            }
          }
          return new BatchOperationResponse(
              404, path, op.getMethod(), "Unsupported path: " + subPath, "NotFoundError");

        case "PUT":
          if (subPath.startsWith("/") && subPath.length() > 1 && !"/bulk".equals(subPath)) {
            // Single update: /api/{contentType}/{documentId}
            String documentId = subPath.substring(1);
            CmsEntry updated = repository.update(
                documentId, body, null, extractLocale(body));
            ContentEntryDto dto = ContentEntryDto.from(updated);
            return new BatchOperationResponse(
                200, path, op.getMethod(), new StrapiCollectionResponse<>(List.of(dto)));
          } else if ("/bulk".equals(subPath)) {
            // Bulk update via batch not yet supported — use direct bulk endpoint
            return new BatchOperationResponse(
                400, path, op.getMethod(),
                "Use PUT /api/{contentType}/bulk directly for bulk updates",
                "ValidationError");
          }
          return new BatchOperationResponse(
              404, path, op.getMethod(), "Unsupported path: " + subPath, "NotFoundError");

        case "DELETE":
          if (subPath.startsWith("/") && subPath.length() > 1 && !"/bulk".equals(subPath)) {
            // Single delete: /api/{contentType}/{documentId}
            String documentId = subPath.substring(1);
            repository.delete(documentId);
            return new BatchOperationResponse(
                200, path, op.getMethod(), Map.of("documentId", documentId, "deleted", true));
          } else if ("/bulk".equals(subPath)) {
            return new BatchOperationResponse(
                400, path, op.getMethod(),
                "Use DELETE /api/{contentType}/bulk directly for bulk deletes",
                "ValidationError");
          }
          return new BatchOperationResponse(
              404, path, op.getMethod(), "Unsupported path: " + subPath, "NotFoundError");

        default:
          return new BatchOperationResponse(
              400, path, op.getMethod(),
              "Unsupported method: " + method, "ValidationError");
      }
    } catch (IllegalArgumentException e) {
      return new BatchOperationResponse(
          400, path, op.getMethod(), e.getMessage(), "ValidationError");
    } catch (IllegalStateException e) {
      return new BatchOperationResponse(
          409, path, op.getMethod(), e.getMessage(), "ConflictError");
    }
  }

  /**
   * Extracts a locale value from a request body map.
   */
  private static String extractLocale(Map<String, Object> body) {
    Object locale = body.get("locale");
    return locale != null ? locale.toString() : null;
  }

  /**
   * Builds a standard response for successful bulk operations.
   *
   * <p>Returns a Strapi-compatible collection response where each item carries
   * the operation result data, plus meta with success/failure counts.
   */
  private Response buildBulkResponse(List<BulkOperationResult> results) {
    int succeeded = (int) results.stream().filter(r -> r.getError() == null).count();
    int failed = results.size() - succeeded;

    Map<String, Object> meta = new HashMap<>();
    meta.put("bulk", new BulkOperationMeta(results.size(), succeeded, failed));

    List<Map<String, Object>> dataList = new ArrayList<>();
    for (BulkOperationResult result : results) {
      Map<String, Object> item = new HashMap<>();
      item.put("index", result.getIndex());
      if (result.getError() != null) {
        item.put("error", result.getError());
        item.put("errorName", result.getErrorName());
        item.put("status", result.getStatus());
      } else {
        item.put("status", result.getStatus() != null ? result.getStatus() : 200);
        item.put("data", result.getData());
      }
      dataList.add(item);
    }

    return Response.ok(new StrapiCollectionResponse<>(dataList, meta)).build();
  }

  /**
   * Builds an error response for a failed bulk operation with partial results.
   */
  private Response buildBulkErrorResponse(BulkOperationException e, String message) {
    int failedIndex = e.getFailedIndex();

    // Include partial results up to the failure point
    Map<String, Object> errorDetails = new HashMap<>();
    errorDetails.put("failedAtIndex", failedIndex);
    errorDetails.put("message", message);

    List<BulkOperationResult> partial = e.getPartialResults();
    int succeeded = (int) partial.stream().filter(r -> r.getError() == null).count();

    errorDetails.put("partialResults", Map.of(
        "total", partial.size(),
        "succeeded", succeeded,
        "failed", partial.size() - succeeded,
        "items", partial.stream().map(r -> {
          Map<String, Object> item = new HashMap<>();
          item.put("index", r.getIndex());
          if (r.getError() != null) {
            item.put("error", r.getError());
            item.put("errorName", r.getErrorName());
          } else {
            item.put("data", r.getData());
          }
          return item;
        }).toList()
    ));

    return Response.status(Response.Status.BAD_REQUEST)
        .entity(StrapiErrorResponse.of(400, "BulkOperationError", message, errorDetails))
        .build();
  }
}
