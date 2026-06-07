package com.quarkus.cms.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.rest.dto.BatchRequest;
import com.quarkus.cms.rest.dto.BatchRequest.BatchOperation;
import com.quarkus.cms.rest.dto.BatchResponse;
import com.quarkus.cms.rest.dto.BulkOperationResult;
import com.quarkus.cms.rest.dto.ContentEntryDto;
import com.quarkus.cms.rest.service.BulkOperationService;

import jakarta.ws.rs.core.Response;

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
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the batch endpoint routing logic in {@link BulkOperationsResource}.
 *
 * <p>Tests the private {@code executeBatchOperation} method indirectly through
 * the public {@code batch()} endpoint, mocking the underlying service and
 * repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BulkOperationsResource - batch routing")
class BulkOperationsResourceTest {

  @InjectMocks
  BulkOperationsResource resource;

  @Mock
  BulkOperationService bulkService;

  @Mock
  CmsEntryRepository repository;

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private CmsEntry createEntry(String documentId, String contentType, String locale,
      String title) {
    CmsEntry entry = new CmsEntry();
    entry.documentId = documentId;
    entry.contentType = contentType;
    entry.locale = locale;
    entry.status = "draft";
    entry.versionNumber = 1;
    entry.data = new HashMap<>(Map.of("title", title));
    return entry;
  }

  private BatchResponse executeBatch(BatchRequest request) {
    Response response = resource.batch(request).await().indefinitely();
    assertEquals(200, response.getStatus());
    return (BatchResponse) response.getEntity();
  }

  // ==========================================================================
  // POST routing
  // ==========================================================================

  @Nested
  @DisplayName("POST routing")
  class PostRouting {

    @Test
    @DisplayName("single create should return 201 status with entry data")
    void singleCreate() {
      String docId = UUID.randomUUID().toString();
      CmsEntry created = createEntry(docId, "article", "en", "New Entry");
      when(repository.create(eq("article"), any(), isNull())).thenReturn(created);

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", Map.of("title", "New Entry"))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(201, result.getResponses().get(0).getStatus());
      assertEquals("POST", result.getResponses().get(0).getMethod());
      assertEquals("/api/article", result.getResponses().get(0).getPath());
      assertEquals(1, result.getMeta().get("succeeded"));
      assertEquals(0, result.getMeta().get("failed"));

      verify(repository).create(eq("article"), any(), isNull());
    }

    @Test
    @DisplayName("bulk create should return 200 status with per-item results")
    void bulkCreate() {
      BulkOperationResult r1 = BulkOperationResult.success(0,
          Map.of("documentId", UUID.randomUUID().toString(), "title", "First"));
      BulkOperationResult r2 = BulkOperationResult.success(1,
          Map.of("documentId", UUID.randomUUID().toString(), "title", "Second"));
      when(bulkService.createBulk(eq("article"), anyList(), isNull(), isNull()))
          .thenReturn(List.of(r1, r2));

      Map<String, Object> body = new HashMap<>();
      body.put("data", List.of(Map.of("title", "First"), Map.of("title", "Second")));
      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", body)
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(200, result.getResponses().get(0).getStatus());
      assertEquals(1, result.getMeta().get("succeeded"));
      assertEquals(0, result.getMeta().get("failed"));

      verify(bulkService).createBulk(eq("article"), anyList(), isNull(), isNull());
      verify(repository, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("unsupported subpath should return 404")
    void unsupportedSubPath() {
      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article/invalid/extra", Map.of())
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(404, result.getResponses().get(0).getStatus());
      assertNotNull(result.getResponses().get(0).getError());
      assertTrue(result.getResponses().get(0).getError().contains("Unsupported path"));
      assertEquals(1, result.getMeta().get("failed"));
      assertEquals(0, result.getMeta().get("succeeded"));

      verify(repository, never()).create(any(), any(), any());
    }
  }

  // ==========================================================================
  // PUT routing
  // ==========================================================================

  @Nested
  @DisplayName("PUT routing")
  class PutRouting {

    @Test
    @DisplayName("single update should return 200 with updated data")
    void singleUpdate() {
      String docId = UUID.randomUUID().toString();
      CmsEntry updated = createEntry(docId, "article", "en", "Updated Title");
      when(repository.update(eq(docId), any(), isNull(), isNull())).thenReturn(updated);

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("PUT", "/api/article/" + docId,
              Map.of("title", "Updated Title"))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(200, result.getResponses().get(0).getStatus());
      assertEquals("PUT", result.getResponses().get(0).getMethod());
      assertEquals(1, result.getMeta().get("succeeded"));

      verify(repository).update(eq(docId), any(), isNull(), isNull());
    }

    @Test
    @DisplayName("bulk update should return 400 (not supported via batch)")
    void bulkUpdateRejected() {
      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("PUT", "/api/article/bulk",
              Map.of("data", List.of(Map.of("documentId", "x", "data", Map.of()))))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(400, result.getResponses().get(0).getStatus());
      assertTrue(result.getResponses().get(0).getError()
          .contains("Use PUT /api/{contentType}/bulk directly"));

      verify(repository, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should treat subpath as document ID for single update")
    void singleUpdateWithComplexPath() {
      // The code treats any subpath after contentType as a document ID
      String docId = "extra";
      CmsEntry updated = createEntry(UUID.randomUUID().toString(), "article", "en", "Result");
      when(repository.update(eq(docId), any(), isNull(), isNull())).thenReturn(updated);

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("PUT", "/api/article/extra", Map.of("title", "x"))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(200, result.getResponses().get(0).getStatus());
      verify(repository).update(eq(docId), any(), isNull(), isNull());
    }
  }

  // ==========================================================================
  // DELETE routing
  // ==========================================================================

  @Nested
  @DisplayName("DELETE routing")
  class DeleteRouting {

    @Test
    @DisplayName("single delete should return 200 with deleted flag")
    void singleDelete() {
      String docId = UUID.randomUUID().toString();

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("DELETE", "/api/article/" + docId, Map.of())
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(200, result.getResponses().get(0).getStatus());
      assertEquals("DELETE", result.getResponses().get(0).getMethod());
      assertEquals(1, result.getMeta().get("succeeded"));

      verify(repository).delete(docId);
    }

    @Test
    @DisplayName("bulk delete should return 400 (not supported via batch)")
    void bulkDeleteRejected() {
      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("DELETE", "/api/article/bulk",
              Map.of("documentIds", List.of("a", "b")))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(400, result.getResponses().get(0).getStatus());
      assertTrue(result.getResponses().get(0).getError()
          .contains("Use DELETE /api/{contentType}/bulk directly"));

      verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("should treat subpath as document ID for single delete")
    void singleDeleteWithComplexPath() {
      // The code treats any subpath after contentType as a document ID
      String docId = "extra";

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("DELETE", "/api/article/extra", Map.of())
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(200, result.getResponses().get(0).getStatus());
      assertEquals("DELETE", result.getResponses().get(0).getMethod());
      assertEquals(1, result.getMeta().get("succeeded"));
      verify(repository).delete(docId);
    }
  }

  // ==========================================================================
  // Validation / error routing
  // ==========================================================================

  @Nested
  @DisplayName("validation and error handling")
  class ValidationAndError {

    @Test
    @DisplayName("should reject unsupported methods")
    void unsupportedMethod() {
      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("OPTIONS", "/api/article", Map.of())
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(400, result.getResponses().get(0).getStatus());
      assertTrue(result.getResponses().get(0).getError().contains("Unsupported method"));
      assertEquals("ValidationError", result.getResponses().get(0).getErrorName());
      assertEquals(1, result.getMeta().get("failed"));
    }

    @Test
    @DisplayName("should reject paths not starting with /api/")
    void invalidPath() {
      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("POST", "/not-api/path", Map.of("title", "x"))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(400, result.getResponses().get(0).getStatus());
      assertTrue(result.getResponses().get(0).getError().contains("Path must start with /api/"));
      assertEquals("ValidationError", result.getResponses().get(0).getErrorName());
    }

    @Test
    @DisplayName("should reject nested batch operations")
    void nestedBatchRejected() {
      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("POST", "/api/batch", Map.of())
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(400, result.getResponses().get(0).getStatus());
      assertTrue(result.getResponses().get(0).getError()
          .contains("Nested batch operations are not supported"));
      assertEquals("ValidationError", result.getResponses().get(0).getErrorName());
    }

    @Test
    @DisplayName("should return 400 ValidationError on IllegalArgumentException from repository")
    void illegalArgumentFromRepository() {
      String docId = UUID.randomUUID().toString();
      when(repository.update(eq(docId), any(), isNull(), isNull()))
          .thenThrow(new IllegalArgumentException("Draft entry not found"));

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("PUT", "/api/article/" + docId, Map.of("title", "x"))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(400, result.getResponses().get(0).getStatus());
      assertEquals("ValidationError", result.getResponses().get(0).getErrorName());
      assertTrue(result.getResponses().get(0).getError().contains("Draft entry not found"));
    }

    @Test
    @DisplayName("should return 409 ConflictError on IllegalStateException from repository")
    void illegalStateFromRepository() {
      String docId = UUID.randomUUID().toString();
      when(repository.update(eq(docId), any(), isNull(), isNull()))
          .thenThrow(new IllegalStateException("Entry is locked"));

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("PUT", "/api/article/" + docId, Map.of("title", "x"))
      )));

      assertEquals(1, result.getResponses().size());
      assertEquals(409, result.getResponses().get(0).getStatus());
      assertEquals("ConflictError", result.getResponses().get(0).getErrorName());
      assertTrue(result.getResponses().get(0).getError().contains("Entry is locked"));
    }

    @Test
    @DisplayName("should stop processing and break on first operation failure")
    void stopOnFirstFailure() {
      // First op succeeds, second op has invalid path → stop at second
      String docId = UUID.randomUUID().toString();
      CmsEntry created = createEntry(docId, "article", "en", "First");
      when(repository.create(eq("article"), any(), isNull())).thenReturn(created);

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", Map.of("title", "First")),
          new BatchOperation("POST", "/invalid/path", Map.of()),
          new BatchOperation("POST", "/api/article", Map.of("title", "Third"))
      )));

      assertEquals(2, result.getResponses().size()); // Only first + failed; third not attempted
      assertEquals(201, result.getResponses().get(0).getStatus());
      assertEquals(400, result.getResponses().get(1).getStatus());
      assertEquals(1, result.getMeta().get("succeeded"));
      assertEquals(1, result.getMeta().get("failed"));
      assertEquals(2, result.getMeta().get("total"));
    }

    @Test
    @DisplayName("should recover OperationResponse body content for successful POST")
    void postCreateBodyContent() {
      String docId = UUID.randomUUID().toString();
      CmsEntry created = createEntry(docId, "article", "en", "Content Check");
      when(repository.create(eq("article"), any(), isNull())).thenReturn(created);

      BatchResponse result = executeBatch(new BatchRequest(List.of(
          new BatchOperation("POST", "/api/article", Map.of("title", "Content Check"))
      )));

      assertNotNull(result.getResponses().get(0).getBody());
      assertEquals(201, result.getResponses().get(0).getStatus());
    }
  }
}
