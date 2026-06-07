package com.quarkus.cms.rest.service;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.rest.dto.BulkCreateEntry;
import com.quarkus.cms.rest.dto.BulkDeleteRequest;
import com.quarkus.cms.rest.dto.BulkOperationResult;
import com.quarkus.cms.rest.dto.BulkUpdateEntry;
import com.quarkus.cms.rest.dto.ContentEntryDto;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for bulk content operations with transactional rollback and
 * pre-validation.
 *
 * <p>All bulk methods execute within a single database transaction. Before
 * any write operation begins, all items are pre-validated to catch errors
 * early. If any individual operation fails, the entire batch is rolled back
 * to maintain atomicity. Results are returned per-item so the client can
 * identify which entries succeeded or failed.
 *
 * <p>Uses {@link CmsEntryRepository} directly (matching the ContentApiResource
 * pattern) rather than {@code EntityService}, avoiding content-type schema
 * validation that may not be available in all deployments.
 */
@ApplicationScoped
public class BulkOperationService {

  @Inject
  CmsEntryRepository repository;

  /**
   * Bulk-create entries. All entries are pre-validated before any are created.
   * If any entry fails validation or creation, the entire batch is rolled back.
   *
   * @param contentType the content type UID
   * @param entries     the list of entry data objects
   * @param locale      the locale (may be null)
   * @param userId      the creating user ID (may be null)
   * @return list of operation results with per-item status
   */
  @Transactional(rollbackOn = Exception.class)
  public List<BulkOperationResult> createBulk(
      String contentType, List<BulkCreateEntry> entries, String locale, Long userId) {

    // Pre-validate: check each entry has at least one data field
    List<BulkOperationResult> results = new ArrayList<>(entries.size());
    for (int i = 0; i < entries.size(); i++) {
      BulkCreateEntry entry = entries.get(i);
      if (entry.getDataFields() == null || entry.getDataFields().isEmpty()) {
        throw new BulkOperationException(
            "Bulk create failed at index " + i + ": entry data must not be empty", i, results);
      }
    }

    String resolvedLocale = locale != null ? locale : "en";

    for (int i = 0; i < entries.size(); i++) {
      BulkCreateEntry entry = entries.get(i);
      try {
        Map<String, Object> fields = new HashMap<>(entry.getDataFields());
        CmsEntry created;

        if (userId != null) {
          created = repository.createWithCreator(contentType, fields, resolvedLocale, userId);
        } else {
          created = repository.create(contentType, fields, resolvedLocale);
        }

        ContentEntryDto dto = ContentEntryDto.from(created);
        Map<String, Object> data = serializeDto(dto);

        results.add(BulkOperationResult.success(i, data));
        Log.debugf("Bulk create[%d]: created entry %s", i, created.documentId);
      } catch (BulkOperationException e) {
        // Re-throw pre-validation failures directly (already has empty partial results)
        throw e;
      } catch (Exception e) {
        Log.errorf(e, "Bulk create[%d] failed: %s", i, e.getMessage());
        throw new BulkOperationException(
            "Bulk create failed at index " + i + ": " + e.getMessage(), i, results);
      }
    }

    return results;
  }

  /**
   * Bulk-update entries. All document IDs are pre-validated before any
   * updates are applied. If any entry fails with an unexpected error
   * (not a validation error), the entire batch is rolled back.
   *
   * @param contentType the content type UID (used for validation)
   * @param updates     the list of update entries (documentId + data)
   * @param locale      the locale (may be null)
   * @param userId      the updating user ID (may be null)
   * @return list of operation results with per-item status
   */
  @Transactional(rollbackOn = Exception.class)
  public List<BulkOperationResult> updateBulk(
      String contentType, List<BulkUpdateEntry> updates, String locale, Long userId) {

    List<BulkOperationResult> results = new ArrayList<>(updates.size());

    // Pre-validate: check all document IDs are non-blank before applying any updates
    for (int i = 0; i < updates.size(); i++) {
      BulkUpdateEntry update = updates.get(i);
      if (update.getDocumentId() == null || update.getDocumentId().isBlank()) {
        throw new BulkOperationException(
            "Bulk update failed at index " + i + ": documentId must not be blank", i, results);
      }
    }

    for (int i = 0; i < updates.size(); i++) {
      BulkUpdateEntry update = updates.get(i);
      try {
        String itemLocale = update.getLocale() != null ? update.getLocale() : locale;

        CmsEntry updated = repository.update(
            update.getDocumentId(), update.getData(), userId, itemLocale);

        ContentEntryDto dto = ContentEntryDto.from(updated);
        Map<String, Object> data = serializeDto(dto);

        results.add(BulkOperationResult.success(i, data));
        Log.debugf("Bulk update[%d]: updated entry %s", i, update.getDocumentId());
      } catch (IllegalArgumentException e) {
        Log.errorf(e, "Bulk update[%d] failed for %s: %s",
            i, update.getDocumentId(), e.getMessage());
        results.add(BulkOperationResult.failure(
            i, 400, e.getMessage(), "ValidationError"));
      } catch (Exception e) {
        Log.errorf(e, "Bulk update[%d] failed for %s: %s",
            i, update.getDocumentId(), e.getMessage());
        throw new BulkOperationException(
            "Bulk update failed at index " + i + ": " + e.getMessage(), i, results);
      }
    }

    return results;
  }

  /**
   * Bulk-delete entries. All deletions run in one transaction — if any
   * fails, the whole batch is rolled back.
   *
   * @param contentType  the content type UID (used for logging)
   * @param deleteRequest the delete request with document IDs
   * @return list of operation results with per-item status
   */
  @Transactional(rollbackOn = Exception.class)
  public List<BulkOperationResult> deleteBulk(
      String contentType, BulkDeleteRequest deleteRequest) {

    List<String> documentIds = deleteRequest.getDocumentIds();
    List<BulkOperationResult> results = new ArrayList<>(documentIds.size());

    for (int i = 0; i < documentIds.size(); i++) {
      String documentId = documentIds.get(i);
      try {
        CmsEntry existing = repository.findByDocumentId(documentId);
        if (existing == null) {
          results.add(BulkOperationResult.failure(
              i, 404, "Entry not found: " + documentId, "NotFoundError"));
          continue; // skip, don't roll back for individual not-found
        }

        repository.delete(documentId);

        Map<String, Object> data = new HashMap<>();
        data.put("documentId", documentId);
        data.put("deleted", true);

        results.add(BulkOperationResult.success(i, data));
        Log.debugf("Bulk delete[%d]: deleted entry %s", i, documentId);
      } catch (Exception e) {
        Log.errorf(e, "Bulk delete[%d] failed for %s: %s",
            i, documentId, e.getMessage());
        throw new BulkOperationException(
            "Bulk delete failed at index " + i + ": " + e.getMessage(), i, results);
      }
    }

    return results;
  }

  /**
   * Serializes a ContentEntryDto to a flat map suitable for JSON response.
   */
  private static Map<String, Object> serializeDto(ContentEntryDto dto) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", dto.getId());
    map.put("documentId", dto.getDocumentId());
    map.put("locale", dto.getLocale());
    map.put("status", dto.getStatus());
    map.put("createdAt", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
    map.put("updatedAt", dto.getUpdatedAt() != null ? dto.getUpdatedAt().toString() : null);
    map.put("publishedAt", dto.getPublishedAt() != null ? dto.getPublishedAt().toString() : null);
    map.put("createdById", dto.getCreatedById());
    map.put("updatedById", dto.getUpdatedById());
    map.put("publishedById", dto.getPublishedById());
    if (dto.getDataFields() != null) {
      map.putAll(dto.getDataFields());
    }
    return map;
  }
}
