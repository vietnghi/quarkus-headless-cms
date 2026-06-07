package com.quarkus.cms.runtime;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.CmsQueryBuilder;
import com.quarkus.cms.core.query.SortOrder;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.draft.DraftPublishService;
import com.quarkus.cms.draft.model.ContentStatus;
import com.quarkus.cms.webhooks.service.LifecycleEventBus;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Core content entry service for the CMS.
 *
 * <p>Manages CRUD operations on content entries using the hybrid document-on-RDBMS schema.
 * Delegates to {@link DraftPublishService} for draft/publish lifecycle and {@link CmsSchemaService}
 * for schema-aware validation.
 *
 * <p>This is the primary runtime service consumed by the REST and Admin APIs.
 */
@ApplicationScoped
public class CmsContentService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CmsConfig config;
  private final CmsSchemaService schemaService;
  private final DraftPublishService draftPublishService;
  private final LifecycleEventBus lifecycleEventBus;

  @Inject
  public CmsContentService(
      CmsConfig config,
      CmsSchemaService schemaService,
      DraftPublishService draftPublishService,
      LifecycleEventBus lifecycleEventBus) {
    this.config = config;
    this.schemaService = schemaService;
    this.draftPublishService = draftPublishService;
    this.lifecycleEventBus = lifecycleEventBus;
  }

  /**
   * Creates a new content entry as a draft.
   *
   * @param contentType the content-type UID
   * @param payload JSON payload for the entry
   * @return the created entry's document ID
   */
  @Transactional
  public String createEntry(String contentType, String payload) {
    ContentTypeDefinition ct = validateContentType(contentType);
    Map<String, Object> data = parseJson(payload);

    lifecycleEventBus.fireBeforeCreate(contentType, data, config.defaultLocale(), null);

    CmsEntry entry =
        draftPublishService.createDraft(contentType, data, config.defaultLocale(), null);

    lifecycleEventBus.fireAfterCreate(
        contentType, entry.documentId, entry.data, config.defaultLocale(), null);

    Log.infof("Created entry %s for content type %s", entry.documentId, contentType);
    return entry.documentId;
  }

  /**
   * Creates a new content entry with explicit data, locale, and user ID.
   *
   * @param contentType the content-type UID
   * @param data the field data map
   * @param locale the locale code
   * @param userId the creating user ID (nullable)
   * @return the created entry
   */
  @Transactional
  public CmsEntry createEntry(
      String contentType, Map<String, Object> data, String locale, Long userId) {
    validateContentType(contentType);

    String loc = locale != null ? locale : config.defaultLocale();

    lifecycleEventBus.fireBeforeCreate(contentType, data, loc, userId);

    CmsEntry entry = draftPublishService.createDraft(contentType, data, loc, userId);

    lifecycleEventBus.fireAfterCreate(contentType, entry.documentId, entry.data, loc, userId);

    return entry;
  }

  /**
   * Retrieves a content entry by document ID.
   *
   * @param contentType the content-type UID (used for validation)
   * @param id the document ID
   * @return entry payload as JSON, or empty if not found
   */
  public Optional<String> getEntry(String contentType, String id) {
    // Try draft first, then published
    CmsEntry entry = draftPublishService.getDraft(id, config.defaultLocale());
    if (entry == null) {
      entry = draftPublishService.getPublished(id, config.defaultLocale());
    }
    if (entry == null) {
      lifecycleEventBus.fireAfterFindOne(contentType, id, null, config.defaultLocale(), null);
      return Optional.empty();
    }

    lifecycleEventBus.fireAfterFindOne(contentType, id, entry.data, config.defaultLocale(), null);

    try {
      return Optional.of(MAPPER.writeValueAsString(entry.data));
    } catch (JsonProcessingException e) {
      Log.errorf("Failed to serialize entry %s: %s", id, e.getMessage());
      return Optional.empty();
    }
  }

  /** Retrieves a content entry as a CmsEntry object. */
  public CmsEntry getEntryObject(String documentId, String locale) {
    CmsEntry entry = draftPublishService.getDraft(documentId, locale);
    if (entry == null) {
      entry = draftPublishService.getPublished(documentId, locale);
    }
    return entry;
  }

  /**
   * Updates a content entry's data.
   *
   * @param contentType the content-type UID
   * @param id the document ID
   * @param payload partial or full JSON payload to merge
   * @return true if updated, false if not found
   */
  @Transactional
  public boolean updateEntry(String contentType, String id, String payload) {
    validateContentType(contentType);

    Map<String, Object> data = parseJson(payload);

    lifecycleEventBus.fireBeforeUpdate(contentType, id, data, config.defaultLocale(), null);

    try {
      draftPublishService.updateDraft(id, data, config.defaultLocale(), null);

      lifecycleEventBus.fireAfterUpdate(contentType, id, data, config.defaultLocale(), null);

      Log.debugf("Updated entry: %s", id);
      return true;
    } catch (IllegalStateException e) {
      Log.warnf("Entry not found for update: %s", id);
      return false;
    }
  }

  /** Updates a content entry with explicit data map and locale. */
  @Transactional
  public CmsEntry updateEntry(
      String documentId, Map<String, Object> data, String locale, Long userId) {
    String loc = locale != null ? locale : config.defaultLocale();
    CmsEntry existing = getEntryObject(documentId, loc);

    lifecycleEventBus.fireBeforeUpdate(
        existing != null ? existing.contentType : "unknown", documentId, data, loc, userId);

    CmsEntry entry = draftPublishService.updateDraft(documentId, data, loc, userId);

    lifecycleEventBus.fireAfterUpdate(entry.contentType, documentId, data, loc, userId);

    return entry;
  }

  /** Publishes a draft entry. */
  @Transactional
  public CmsEntry publishEntry(String documentId, String locale, Long userId) {
    String loc = locale != null ? locale : config.defaultLocale();
    CmsEntry existing = getEntryObject(documentId, loc);
    String contentType = existing != null ? existing.contentType : "unknown";

    lifecycleEventBus.fireBeforePublish(contentType, documentId, loc, userId);

    CmsEntry entry = draftPublishService.publish(documentId, loc, userId);

    lifecycleEventBus.fireAfterPublish(entry.contentType, documentId, entry.data, loc, userId);

    return entry;
  }

  /** Unpublishes a published entry. */
  @Transactional
  public void unpublishEntry(String documentId, String locale) {
    String loc = locale != null ? locale : config.defaultLocale();
    CmsEntry existing = getEntryObject(documentId, loc);
    String contentType = existing != null ? existing.contentType : "unknown";

    lifecycleEventBus.fireBeforeUnpublish(contentType, documentId, loc, null);

    draftPublishService.unpublish(documentId, loc);

    lifecycleEventBus.fireAfterUnpublish(contentType, documentId, loc, null);
  }

  /** Discards a draft, preserving the published version. */
  @Transactional
  public void discardDraft(String documentId, String locale) {
    draftPublishService.discardDraft(documentId, locale != null ? locale : config.defaultLocale());
  }

  /**
   * Deletes a content entry (all versions).
   *
   * @param contentType the content-type UID
   * @param id the document ID
   * @return true if deleted, false if not found
   */
  @Transactional
  public boolean deleteEntry(String contentType, String id) {
    CmsEntry entry = getEntryObject(id, config.defaultLocale());
    if (entry == null) {
      return false;
    }

    lifecycleEventBus.fireBeforeDelete(contentType, id, config.defaultLocale(), null);

    // Delete all versions
    long deleted = CmsEntry.delete("documentId", id);

    lifecycleEventBus.fireAfterDelete(contentType, id, config.defaultLocale(), null);

    Log.infof("Deleted entry %s (%d rows removed)", id, deleted);
    return deleted > 0;
  }

  /**
   * Lists entries for a content type with filtering, sorting, and pagination.
   *
   * @param contentType the content-type UID
   * @param query a map of query parameters (locale, status, filters, sort, page, pageSize)
   * @return a map with "results" (list of entry data maps) and "pagination" metadata
   */
  public Map<String, Object> listEntries(String contentType, Map<String, Object> query) {
    validateContentType(contentType);

    lifecycleEventBus.fireBeforeFindMany(contentType);

    CmsQuery q = buildQuery(contentType, query);

    List<CmsEntry> entries = CmsQueryBuilder.list(q);
    long total = CmsQueryBuilder.count(q);

    lifecycleEventBus.fireAfterFindMany(contentType, entries.size());

    List<Map<String, Object>> results =
        entries.stream()
            .map(
                e -> {
                  Map<String, Object> m = new HashMap<>();
                  m.put("documentId", e.documentId);
                  m.put("contentType", e.contentType);
                  m.put("locale", e.locale);
                  m.put("status", e.status);
                  m.put("versionNumber", e.versionNumber);
                  m.put("data", e.data);
                  m.put("createdAt", e.createdAt != null ? e.createdAt.toString() : null);
                  m.put("updatedAt", e.updatedAt != null ? e.updatedAt.toString() : null);
                  m.put("publishedAt", e.publishedAt != null ? e.publishedAt.toString() : null);
                  return m;
                })
            .toList();

    Map<String, Object> result = new HashMap<>();
    result.put("results", results);
    result.put(
        "pagination",
        Map.of(
            "page",
            q.getPage(),
            "pageSize",
            q.getPageSize(),
            "pageCount",
            (int) Math.ceil((double) total / q.getPageSize()),
            "total",
            total));

    return result;
  }

  /** Returns all versions of a document. */
  public List<CmsEntry> getVersions(String documentId, String locale) {
    return draftPublishService.getVersions(
        documentId, locale != null ? locale : config.defaultLocale());
  }

  /** Checks whether a document has unpublished draft changes. */
  public boolean hasUnpublishedChanges(String documentId, String locale) {
    return draftPublishService.hasUnpublishedChanges(
        documentId, locale != null ? locale : config.defaultLocale());
  }

  /** Retrieves the single-type entry for a content type. */
  public CmsEntry getSingleTypeEntry(String contentType, String locale) {
    CmsEntry draft =
        CmsEntry.find(
                "contentType = ?1 and status = ?2 and locale = ?3",
                contentType,
                ContentStatus.DRAFT.getValue(),
                locale != null ? locale : config.defaultLocale())
            .firstResult();
    if (draft != null) return draft;

    return (CmsEntry)
        CmsEntry.find(
                "contentType = ?1 and status = ?2 and locale = ?3 order by versionNumber desc",
                contentType,
                ContentStatus.PUBLISHED.getValue(),
                locale != null ? locale : config.defaultLocale())
            .firstResult();
  }

  public CmsConfig getConfig() {
    return config;
  }

  public CmsSchemaService getSchemaService() {
    return schemaService;
  }

  // ---- Private helpers ----

  private ContentTypeDefinition validateContentType(String contentType) {
    ContentTypeDefinition ct = schemaService.getContentType(contentType);
    if (ct == null) {
      throw new IllegalArgumentException("Unknown content type: " + contentType);
    }
    return ct;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJson(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON payload: " + e.getMessage(), e);
    }
  }

  private CmsQuery buildQuery(String contentType, Map<String, Object> query) {
    CmsQuery q = new CmsQuery(contentType);

    if (query.containsKey("locale")) {
      q.setLocale((String) query.get("locale"));
    } else {
      q.setLocale(config.defaultLocale());
    }

    if (query.containsKey("status")) {
      q.setStatus((String) query.get("status"));
    }

    if (query.containsKey("page")) {
      q.setPage(((Number) query.get("page")).intValue());
    }

    if (query.containsKey("pageSize")) {
      q.setPageSize(((Number) query.get("pageSize")).intValue());
    }

    if (query.containsKey("sort")) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> sortList = (List<Map<String, Object>>) query.get("sort");
      for (Map<String, Object> s : sortList) {
        String field = (String) s.get("field");
        String dir = (String) s.get("direction");
        q.addSort(
            field,
            "desc".equalsIgnoreCase(dir) ? SortOrder.Direction.DESC : SortOrder.Direction.ASC);
      }
    }

    return q;
  }
}
