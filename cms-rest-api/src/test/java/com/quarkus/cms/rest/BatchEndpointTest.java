package com.quarkus.cms.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.rest.dto.BatchRequest;
import com.quarkus.cms.rest.dto.BatchRequest.BatchOperation;
import com.quarkus.cms.rest.dto.BatchResponse;
import com.quarkus.cms.rest.dto.BatchResponse.BatchOperationResponse;
import com.quarkus.cms.rest.dto.BulkCreateEntry;
import com.quarkus.cms.rest.dto.ContentEntryDto;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.service.BulkOperationException;
import com.quarkus.cms.rest.service.BulkOperationService;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the batch endpoint in {@link BulkOperationsResource}.
 *
 * <p>Tests the {@code POST /api/batch} endpoint logic — operation routing,
 * path validation, method dispatch, error handling, and DTO construction.
 * Uses Mockito to mock {@link BulkOperationService} and
 * {@link CmsEntryRepository} so the batch routing logic is tested without
 * needing a database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BulkOperationsResource — batch endpoint")
class BatchEndpointTest {

  @Mock
  BulkOperationService bulkService;

  @Mock
  CmsEntryRepository repository;

  @InjectMocks
  BulkOperationsResource resource;

  // ==========================================================================
  // DTO tests
  // ==========================================================================

  @Nested
  @DisplayName("DTOs")
  class DTOs {

    @Test
    @DisplayName("BatchRequest should store and retrieve operations")
    void batchRequestOperations() {
      BatchOperation op = new BatchOperation("POST", "/api/article", Map.of("title", "Test"));
      BatchRequest request = new BatchRequest(List.of(op));

      assertEquals(1, request.getRequests().size());
      assertEquals("POST", request.getRequests().get(0).getMethod());
      assertEquals("/api/article", request.getRequests().get(0).getPath());
      assertEquals(Map.of("title", "Test"), request.getRequests().get(0).getBody());
    }

    @Test
    @DisplayName("BatchOperation should support queryParams")
    void batchOperationQueryParams() {
      BatchOperation op = new BatchOperation("GET", "/api/articles", null);
      op.setQueryParams(Map.of("locale", "fr"));

      assertEquals("fr", op.getQueryParams().get("locale"));
    }

    @Test
    @DisplayName("BatchResponse should store operation results and meta")
    void batchResponseResults() {
      BatchOperationResponse opResp = new BatchOperationResponse(
          201, "/api/article", "POST", Map.of("documentId", "abc-123"));
      Map<String, Object> meta = new HashMap<>();
      meta.put("total", 1);
      meta.put("succeeded", 1);
      meta.put("failed", 0);
      BatchResponse response = new BatchResponse(List.of(opResp), meta);

      assertEquals(1, response.getResponses().size());
      assertEquals(1, response.getMeta().get("total"));
      assertEquals(1, response.getMeta().get("succeeded"));
      assertEquals(0, response.getMeta().get("failed"));
    }

    @Test
    @DisplayName("BatchOperationResponse should store error details")
    void batchOperationResponseError() {
      BatchOperationResponse errorResp = new BatchOperationResponse(
          400, "/api/article", "POST", "Invalid data", "ValidationError");

      assertEquals(400, errorResp.getStatus());
      assertEquals("/api/article", errorResp.getPath());
      assertEquals("POST", errorResp.getMethod());
      assertEquals("Invalid data", errorResp.getError());
      assertEquals("ValidationError", errorResp.getErrorName());
      assertNull(errorResp.getBody());
    }

    @Test
    @DisplayName("BatchOperationResponse should store success body")
    void batchOperationResponseSuccess() {
      Map<String, Object> body = Map.of("documentId", "abc-123", "title", "Hello");
      BatchOperationResponse successResp = new BatchOperationResponse(
          201, "/api/article", "POST", body);

      assertEquals(201, successResp.getStatus());
      assertEquals(body, successResp.getBody());
      assertNull(successResp.getError());
      assertNull(successResp.getErrorName());
    }

    @Test
    @DisplayName("BatchOperation default constructor should allow empty creation")
    void batchOperationDefaultConstructor() {
      BatchOperation op = new BatchOperation();
      op.setMethod("DELETE");
      op.setPath("/api/article/doc-1");

      assertEquals("DELETE", op.getMethod());
      assertEquals("/api/article/doc-1", op.getPath());
      assertNull(op.getBody());
      assertNull(op.getQueryParams());
    }

    @Test
    @DisplayName("BatchOperationResponse default constructor should allow empty creation")
    void batchResponseDefaultConstructor() {
      BatchOperationResponse resp = new BatchOperationResponse();
      resp.setStatus(200);
      resp.setPath("/api/article");
      resp.setMethod("POST");

      assertEquals(200, resp.getStatus());
      assertEquals("/api/article", resp.getPath());
    }
  }

  // ==========================================================================
  // Batch — Create operations
  // ==========================================================================

  @Nested
  @DisplayName("batch create operations")
  class BatchCreate {

    @Test
    @DisplayName("should create a single entry via batch POST")
    void singleCreate() {
      String docId = UUID.randomUUID().toString();
      CmsEntry entry = createEntry(docId, "article", "en", "Single Create");
      when(repository.create(eq("article"), any(), isNull()))
          .thenReturn(entry);

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", Map.of("title", "Single Create"))));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(201, response.getResponses().get(0).getStatus());
      assertEquals(1, response.getMeta().get("succeeded"));
      assertEquals(0, response.getMeta().get("failed"));
    }

    @Test
    @DisplayName("should perform bulk create via batch when body contains data array")
    void bulkCreateViaBatch() {
      when(bulkService.createBulk(eq("article"), anyList(), isNull(), isNull()))
          .thenReturn(List.of(
              createResult(0, Map.of("documentId", "doc-1", "title", "Bulk 1")),
              createResult(1, Map.of("documentId", "doc-2", "title", "Bulk 2"))));

      Map<String, Object> body = new HashMap<>();
      body.put("data", List.of(Map.of("title", "Bulk 1"), Map.of("title", "Bulk 2")));

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article/bulk", body)));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(200, response.getResponses().get(0).getStatus());
      assertEquals(1, response.getMeta().get("succeeded"));
    }

    @Test
    @DisplayName("should handle create with locale parameter")
    void createWithLocale() {
      String docId = UUID.randomUUID().toString();
      CmsEntry entry = createEntry(docId, "article", "fr", "French Article");
      when(repository.create(eq("article"), any(), eq("fr")))
          .thenReturn(entry);

      Map<String, Object> body = new HashMap<>();
      body.put("title", "French Article");
      body.put("locale", "fr");

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", body)));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(201, response.getResponses().get(0).getStatus());
      verify(repository).create(eq("article"), any(), eq("fr"));
    }
  }

  // ==========================================================================
  // Batch — Update and Delete operations
  // ==========================================================================

  @Nested
  @DisplayName("batch update operations")
  class BatchUpdate {

    @Test
    @DisplayName("should update a single entry via batch PUT")
    void singleUpdate() {
      String docId = UUID.randomUUID().toString();
      CmsEntry updated = createEntry(docId, "article", "en", "Updated");
      when(repository.update(eq(docId), any(), isNull(), isNull()))
          .thenReturn(updated);

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("PUT", "/api/article/" + docId, Map.of("title", "Updated"))));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(200, response.getResponses().get(0).getStatus());
      assertEquals(1, response.getMeta().get("succeeded"));
    }

    @Test
    @DisplayName("should return 400 for bulk update via batch")
    void bulkUpdateNotSupported() {
      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("PUT", "/api/article/bulk", Map.of())));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(400, response.getResponses().get(0).getStatus());
      assertTrue(response.getResponses().get(0).getError()
          .contains("Use PUT /api/{contentType}/bulk directly"));
    }
  }

  @Nested
  @DisplayName("batch delete operations")
  class BatchDelete {

    @Test
    @DisplayName("should delete an entry via batch DELETE")
    void singleDelete() {
      String docId = UUID.randomUUID().toString();
      doNothing().when(repository).delete(docId);

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("DELETE", "/api/article/" + docId, Map.of())));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(200, response.getResponses().get(0).getStatus());
      assertEquals(1, response.getMeta().get("succeeded"));
      verify(repository).delete(docId);
    }

    @Test
    @DisplayName("should return 400 for bulk delete via batch")
    void bulkDeleteNotSupported() {
      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("DELETE", "/api/article/bulk", Map.of())));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(400, response.getResponses().get(0).getStatus());
      assertTrue(response.getResponses().get(0).getError()
          .contains("Use DELETE /api/{contentType}/bulk directly"));
    }
  }

  // ==========================================================================
  // Batch — Error handling
  // ==========================================================================

  @Nested
  @DisplayName("error handling")
  class ErrorHandling {

    @Test
    @DisplayName("should reject unsupported HTTP methods")
    void unsupportedMethod() {
      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("OPTIONS", "/api/article", Map.of())));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(400, response.getResponses().get(0).getStatus());
      assertTrue(response.getResponses().get(0).getError().contains("Unsupported method"));
      assertEquals("ValidationError", response.getResponses().get(0).getErrorName());
    }

    @Test
    @DisplayName("should reject nested batch operations")
    void nestedBatchRejected() {
      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/batch", Map.of())));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(400, response.getResponses().get(0).getStatus());
      assertTrue(response.getResponses().get(0).getError()
          .contains("Nested batch operations are not supported"));
    }

    @Test
    @DisplayName("should reject paths not starting with /api/")
    void invalidPath() {
      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("GET", "/not-api/path", Map.of())));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(400, response.getResponses().get(0).getStatus());
      assertTrue(response.getResponses().get(0).getError()
          .contains("Path must start with /api/"));
    }

    @Test
    @DisplayName("should return 404 for unsupported subpaths")
    void unsupportedSubPath() {
      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article/unknown/action", Map.of())));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(404, response.getResponses().get(0).getStatus());
    }

    @Test
    @DisplayName("should handle IllegalArgumentException from repository")
    void illegalArgumentFromRepository() {
      when(repository.create(anyString(), any(), any()))
          .thenThrow(new IllegalArgumentException("Invalid content type"));

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", Map.of("title", "Fail"))));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(400, response.getResponses().get(0).getStatus());
      assertEquals("ValidationError", response.getResponses().get(0).getErrorName());
    }

    @Test
    @DisplayName("should handle IllegalStateException from repository")
    void illegalStateFromRepository() {
      when(repository.create(anyString(), any(), any()))
          .thenThrow(new IllegalStateException("Entry already exists"));

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", Map.of("title", "Conflict"))));

      BatchResponse response = executeBatch(request);

      assertEquals(1, response.getResponses().size());
      assertEquals(409, response.getResponses().get(0).getStatus());
      assertEquals("ConflictError", response.getResponses().get(0).getErrorName());
    }

    @Test
    @DisplayName("should stop processing after first failed operation")
    void stopOnFirstFailure() {
      // First operation must succeed — mock the repository
      String docId = UUID.randomUUID().toString();
      CmsEntry entry = createEntry(docId, "article", "en", "Good");
      when(repository.create(anyString(), any(), any()))
          .thenReturn(entry);

      BatchRequest request = new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", Map.of("title", "Good")),
          new BatchOperation("OPTIONS", "/api/article", Map.of()),
          new BatchOperation("POST", "/api/article", Map.of("title", "Should Not Run"))));

      BatchResponse response = executeBatch(request);

      assertEquals(2, response.getResponses().size());
      assertEquals(1, response.getMeta().get("succeeded"));
      assertEquals(1, response.getMeta().get("failed"));
    }
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  /**
   * Executes a batch request through the resource's public API and returns
   * the parsed BatchResponse.
   */
  @SuppressWarnings("unchecked")
  private BatchResponse executeBatch(BatchRequest request) {
    Response jaxRsResponse = resource.batch(request).await().indefinitely();
    Object entity = jaxRsResponse.getEntity();
    assertNotNull(entity, "batch() must return an entity");

    if (entity instanceof BatchResponse) {
      return (BatchResponse) entity;
    }

    throw new AssertionError(
        "Expected BatchResponse entity but got: " + entity.getClass().getName()
        + " — value: " + entity);
  }

  private com.quarkus.cms.rest.dto.BulkOperationResult createResult(
      int index, Map<String, Object> data) {
    return com.quarkus.cms.rest.dto.BulkOperationResult.success(index, data);
  }

  private static CmsEntry createEntry(String documentId, String contentType,
      String locale, String title) {
    CmsEntry entry = new CmsEntry();
    entry.documentId = documentId;
    entry.contentType = contentType;
    entry.locale = locale;
    entry.status = "draft";
    entry.versionNumber = 1;
    entry.data = new HashMap<>(Map.of("title", title));
    entry.createdAt = java.time.Instant.now();
    entry.updatedAt = java.time.Instant.now();
    return entry;
  }
}
