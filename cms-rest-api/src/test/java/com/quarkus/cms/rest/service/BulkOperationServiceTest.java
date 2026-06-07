package com.quarkus.cms.rest.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.rest.dto.BulkCreateEntry;
import com.quarkus.cms.rest.dto.BulkDeleteRequest;
import com.quarkus.cms.rest.dto.BulkOperationResult;
import com.quarkus.cms.rest.dto.BulkUpdateEntry;

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
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BulkOperationService}.
 *
 * <p>Uses Mockito to mock {@link CmsEntryRepository}, verifying the service
 * logic — pre-validation, transactional rollback, per-item result reporting —
 * without needing a database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BulkOperationService")
class BulkOperationServiceTest {

  @Mock
  CmsEntryRepository repository;

  @InjectMocks
  BulkOperationService service;

  // ==========================================================================
  // Helpers
  // ==========================================================================

  /** Creates a CmsEntry instance with the given fields. Not persisted. */
  private CmsEntry createEntry(String documentId, String contentType, String locale,
      String title) {
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

  private CmsEntry createEntry(String documentId, String contentType, String locale,
      Map<String, Object> data) {
    CmsEntry entry = new CmsEntry();
    entry.documentId = documentId;
    entry.contentType = contentType;
    entry.locale = locale;
    entry.status = "draft";
    entry.versionNumber = 1;
    entry.data = new HashMap<>(data);
    entry.createdAt = java.time.Instant.now();
    entry.updatedAt = java.time.Instant.now();
    return entry;
  }

  private BulkCreateEntry bulkCreateEntry(String title) {
    BulkCreateEntry entry = new BulkCreateEntry();
    entry.setDataField("title", title);
    return entry;
  }

  private BulkUpdateEntry bulkUpdateEntry(String documentId, String title) {
    return new BulkUpdateEntry(documentId, Map.of("title", title));
  }

  // ==========================================================================
  // Bulk Create
  // ==========================================================================

  @Nested
  @DisplayName("createBulk")
  class CreateBulk {

    @Test
    @DisplayName("should create multiple entries successfully")
    void success() {
      String docId1 = UUID.randomUUID().toString();
      String docId2 = UUID.randomUUID().toString();
      CmsEntry entry1 = createEntry(docId1, "article", "en", "Article 1");
      CmsEntry entry2 = createEntry(docId2, "article", "en", "Article 2");

      when(repository.create(eq("article"), any(), eq("en")))
          .thenReturn(entry1)
          .thenReturn(entry2);

      List<BulkCreateEntry> entries = List.of(
          bulkCreateEntry("Article 1"),
          bulkCreateEntry("Article 2"));

      List<BulkOperationResult> results = service.createBulk("article", entries, "en", null);

      assertNotNull(results);
      assertEquals(2, results.size());
      assertNull(results.get(0).getError());
      assertNull(results.get(1).getError());
      assertEquals(200, results.get(0).getStatus().intValue());
      assertEquals(200, results.get(1).getStatus().intValue());
      assertEquals(docId1, results.get(0).getData().get("documentId"));
      assertEquals(docId2, results.get(1).getData().get("documentId"));

      verify(repository, times(2)).create(eq("article"), any(), eq("en"));
    }

    @Test
    @DisplayName("should pass userId to createWithCreator when provided")
    void withUserId() {
      String docId = UUID.randomUUID().toString();
      CmsEntry entry = createEntry(docId, "article", "en", "With User");

      when(repository.createWithCreator(eq("article"), any(), eq("en"), eq(42L)))
          .thenReturn(entry);

      List<BulkCreateEntry> entries = List.of(bulkCreateEntry("With User"));
      List<BulkOperationResult> results = service.createBulk("article", entries, "en", 42L);

      assertEquals(1, results.size());
      assertNull(results.get(0).getError());
      verify(repository).createWithCreator(eq("article"), any(), eq("en"), eq(42L));
      verify(repository, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("should use default locale when locale is null")
    void defaultLocale() {
      String docId = UUID.randomUUID().toString();
      CmsEntry entry = createEntry(docId, "article", "en", "Default Locale");

      when(repository.create(eq("article"), any(), eq("en")))
          .thenReturn(entry);

      List<BulkCreateEntry> entries = List.of(bulkCreateEntry("Default Locale"));
      List<BulkOperationResult> results = service.createBulk("article", entries, null, null);

      assertEquals(1, results.size());
      verify(repository).create(eq("article"), any(), eq("en"));
    }

    @Test
    @DisplayName("should throw BulkOperationException when entry has empty data fields")
    void preValidationEmptyData() {
      BulkCreateEntry emptyEntry = new BulkCreateEntry();
      // No data fields set

      List<BulkCreateEntry> entries = List.of(
          bulkCreateEntry("Valid"),
          emptyEntry);

      BulkOperationException ex = assertThrows(BulkOperationException.class,
          () -> service.createBulk("article", entries, "en", null));

      assertEquals(1, ex.getFailedIndex());
      assertTrue(ex.getMessage().contains("entry data must not be empty"));
      // Pre-validation failed before any entries were created
      assertEquals(0, ex.getPartialResults().size());
      verify(repository, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("should roll back entire batch when repository throws")
    void repositoryFailure() {
      when(repository.create(eq("article"), any(), eq("en")))
          .thenThrow(new RuntimeException("DB error"));

      List<BulkCreateEntry> entries = List.of(bulkCreateEntry("Failing"));

      BulkOperationException ex = assertThrows(BulkOperationException.class,
          () -> service.createBulk("article", entries, "en", null));

      assertEquals(0, ex.getFailedIndex());
      assertTrue(ex.getMessage().contains("DB error"));
      verify(repository).create(any(), any(), any());
    }
  }

  // ==========================================================================
  // Bulk Update
  // ==========================================================================

  @Nested
  @DisplayName("updateBulk")
  class UpdateBulk {

    @Test
    @DisplayName("should update multiple entries successfully")
    void success() {
      String docId1 = UUID.randomUUID().toString();
      String docId2 = UUID.randomUUID().toString();
      CmsEntry updated1 = createEntry(docId1, "article", "en", "Updated 1");
      CmsEntry updated2 = createEntry(docId2, "article", "en", "Updated 2");

      when(repository.update(eq(docId1), any(), isNull(), eq("en")))
          .thenReturn(updated1);
      when(repository.update(eq(docId2), any(), isNull(), eq("en")))
          .thenReturn(updated2);

      List<BulkUpdateEntry> updates = List.of(
          bulkUpdateEntry(docId1, "Updated 1"),
          bulkUpdateEntry(docId2, "Updated 2"));

      List<BulkOperationResult> results = service.updateBulk("article", updates, "en", null);

      assertEquals(2, results.size());
      assertNull(results.get(0).getError());
      assertNull(results.get(1).getError());
      assertEquals("Updated 1", results.get(0).getData().get("title"));
      assertEquals("Updated 2", results.get(1).getData().get("title"));
    }

    @Test
    @DisplayName("should report failure but not roll back for IllegalArgumentException")
    void validationFailureNoRollback() {
      String validDocId = UUID.randomUUID().toString();
      CmsEntry goodEntry = createEntry(validDocId, "article", "en", "Good");

      when(repository.update(eq(validDocId), any(), isNull(), eq("en")))
          .thenReturn(goodEntry);
      when(repository.update(eq("missing-doc"), any(), isNull(), eq("en")))
          .thenThrow(new IllegalArgumentException("Draft entry not found"));

      List<BulkUpdateEntry> updates = List.of(
          bulkUpdateEntry(validDocId, "Good"),
          new BulkUpdateEntry("missing-doc", Map.of("title", "Bad")));

      List<BulkOperationResult> results = service.updateBulk("article", updates, "en", null);

      assertEquals(2, results.size());
      assertNull(results.get(0).getError());
      assertEquals(200, results.get(0).getStatus().intValue());
      assertNotNull(results.get(1).getError());
      assertEquals(400, results.get(1).getStatus().intValue());
      assertTrue(results.get(1).getError().contains("Draft entry not found"));
    }

    @Test
    @DisplayName("should roll back on unexpected exception during update")
    void unexpectedFailureRollback() {
      when(repository.update(any(), any(), any(), any()))
          .thenThrow(new RuntimeException("Connection lost"));

      List<BulkUpdateEntry> updates = List.of(
          bulkUpdateEntry(UUID.randomUUID().toString(), "Fail"));

      BulkOperationException ex = assertThrows(BulkOperationException.class,
          () -> service.updateBulk("article", updates, "en", null));

      assertEquals(0, ex.getFailedIndex());
      assertTrue(ex.getMessage().contains("Connection lost"));
    }

    @Test
    @DisplayName("should pre-validate blank documentId before any updates")
    void preValidationBlankDocumentId() {
      List<BulkUpdateEntry> updates = List.of(
          bulkUpdateEntry(UUID.randomUUID().toString(), "Valid"),
          new BulkUpdateEntry("", Map.of("title", "Blank ID")));

      BulkOperationException ex = assertThrows(BulkOperationException.class,
          () -> service.updateBulk("article", updates, "en", null));

      assertEquals(1, ex.getFailedIndex());
      assertEquals(0, ex.getPartialResults().size());
      verify(repository, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should pre-validate null documentId before any updates")
    void preValidationNullDocumentId() {
      List<BulkUpdateEntry> updates = new ArrayList<>();
      updates.add(bulkUpdateEntry(UUID.randomUUID().toString(), "Valid"));
      updates.add(new BulkUpdateEntry(null, Map.of("title", "Null ID")));

      BulkOperationException ex = assertThrows(BulkOperationException.class,
          () -> service.updateBulk("article", updates, "en", null));

      assertEquals(1, ex.getFailedIndex());
      verify(repository, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should apply per-item locale override")
    void perItemLocale() {
      String docId = UUID.randomUUID().toString();
      BulkUpdateEntry entry = new BulkUpdateEntry(docId, Map.of("title", "French"));
      entry.setLocale("fr");

      CmsEntry updated = createEntry(docId, "article", "fr", "French");

      when(repository.update(eq(docId), any(), isNull(), eq("fr")))
          .thenReturn(updated);

      List<BulkOperationResult> results = service.updateBulk(
          "article", List.of(entry), null, null);

      assertEquals(1, results.size());
      verify(repository).update(eq(docId), any(), isNull(), eq("fr"));
    }
  }

  // ==========================================================================
  // Bulk Delete
  // ==========================================================================

  @Nested
  @DisplayName("deleteBulk")
  class DeleteBulk {

    @Test
    @DisplayName("should delete multiple entries successfully")
    void success() {
      String docId1 = UUID.randomUUID().toString();
      String docId2 = UUID.randomUUID().toString();
      CmsEntry entry1 = createEntry(docId1, "article", "en", "A");
      CmsEntry entry2 = createEntry(docId2, "article", "en", "B");

      when(repository.findByDocumentId(docId1)).thenReturn(entry1);
      when(repository.findByDocumentId(docId2)).thenReturn(entry2);

      BulkDeleteRequest request = new BulkDeleteRequest(List.of(docId1, docId2));
      List<BulkOperationResult> results = service.deleteBulk("article", request);

      assertEquals(2, results.size());
      assertNull(results.get(0).getError());
      assertNull(results.get(1).getError());
      assertEquals(Boolean.TRUE, results.get(0).getData().get("deleted"));
      assertEquals(Boolean.TRUE, results.get(1).getData().get("deleted"));

      verify(repository).delete(docId1);
      verify(repository).delete(docId2);
    }

    @Test
    @DisplayName("should report 404 when entry not found and continue without rollback")
    void notFoundContinues() {
      String docId1 = UUID.randomUUID().toString();
      String docId2 = UUID.randomUUID().toString();
      CmsEntry entry1 = createEntry(docId1, "article", "en", "A");

      when(repository.findByDocumentId(docId1)).thenReturn(entry1);
      when(repository.findByDocumentId(docId2)).thenReturn(null);

      BulkDeleteRequest request = new BulkDeleteRequest(List.of(docId1, docId2));
      List<BulkOperationResult> results = service.deleteBulk("article", request);

      assertEquals(2, results.size());
      assertNull(results.get(0).getError());
      assertNotNull(results.get(1).getError());
      assertEquals(404, results.get(1).getStatus().intValue());
      assertTrue(results.get(1).getError().contains("Entry not found"));

      verify(repository).delete(docId1);
      verify(repository, never()).delete(docId2);
    }

    @Test
    @DisplayName("should roll back on unexpected exception during delete")
    void unexpectedFailureRollback() {
      String docId = UUID.randomUUID().toString();
      CmsEntry entry = createEntry(docId, "article", "en", "A");

      when(repository.findByDocumentId(docId)).thenReturn(entry);
      doThrow(new RuntimeException("Disk full"))
          .when(repository).delete(docId);

      BulkDeleteRequest request = new BulkDeleteRequest(List.of(docId));

      BulkOperationException ex = assertThrows(BulkOperationException.class,
          () -> service.deleteBulk("article", request));

      assertEquals(0, ex.getFailedIndex());
      assertTrue(ex.getMessage().contains("Disk full"));
    }
  }

  // ==========================================================================
  // DTO unit tests
  // ==========================================================================

  @Nested
  @DisplayName("DTOs")
  class DTOs {

    @Test
    @DisplayName("BulkCreateEntry should store and retrieve data fields")
    void bulkCreateEntryFields() {
      BulkCreateEntry entry = new BulkCreateEntry();
      entry.setDataField("title", "Hello");
      entry.setDataField("category", "tech");

      assertEquals("Hello", entry.getDataFields().get("title"));
      assertEquals("tech", entry.getDataFields().get("category"));
      assertEquals(2, entry.getDataFields().size());
    }

    @Test
    @DisplayName("BulkCreateEntry should return empty map when no fields set")
    void bulkCreateEntryEmpty() {
      BulkCreateEntry entry = new BulkCreateEntry();
      assertTrue(entry.getDataFields().isEmpty());
    }

    @Test
    @DisplayName("BulkUpdateEntry should store and retrieve fields")
    void bulkUpdateEntryFields() {
      BulkUpdateEntry entry = new BulkUpdateEntry("doc-1", Map.of("title", "Updated"));
      entry.setLocale("fr");

      assertEquals("doc-1", entry.getDocumentId());
      assertEquals(Map.of("title", "Updated"), entry.getData());
      assertEquals("fr", entry.getLocale());
    }

    @Test
    @DisplayName("BulkDeleteRequest should store document IDs")
    void bulkDeleteRequestFields() {
      BulkDeleteRequest request = new BulkDeleteRequest(List.of("id1", "id2"));
      assertEquals(2, request.getDocumentIds().size());
      assertTrue(request.getDocumentIds().contains("id1"));
      assertTrue(request.getDocumentIds().contains("id2"));
    }

    @Test
    @DisplayName("BulkOperationResult success factory should set status 200")
    void bulkOperationResultSuccess() {
      Map<String, Object> data = Map.of("documentId", "abc");
      BulkOperationResult result = BulkOperationResult.success(0, data);

      assertEquals(0, result.getIndex());
      assertEquals(200, result.getStatus().intValue());
      assertEquals(data, result.getData());
      assertNull(result.getError());
      assertNull(result.getErrorName());
    }

    @Test
    @DisplayName("BulkOperationResult failure factory should set error details")
    void bulkOperationResultFailure() {
      BulkOperationResult result = BulkOperationResult.failure(1, 400, "Bad request", "ValidationError");

      assertEquals(1, result.getIndex());
      assertEquals(400, result.getStatus().intValue());
      assertEquals("Bad request", result.getError());
      assertEquals("ValidationError", result.getErrorName());
      assertNull(result.getData());
    }
  }
}
