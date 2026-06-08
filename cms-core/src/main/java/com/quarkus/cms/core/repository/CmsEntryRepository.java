package com.quarkus.cms.core.repository;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.CmsQueryBuilder;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository for dynamic content entries stored in the {@code cms_entries} table.
 *
 * <p>Wraps the active-record {@link CmsEntry} entity with transactional CRUD operations,
 * draft/publish lifecycle management, and relation cascade handling.
 */
@ApplicationScoped
public class CmsEntryRepository {

  /** Creates a new content entry with a generated document ID. */
  @Transactional
  public CmsEntry create(String contentType, Map<String, Object> data, String locale) {
    CmsEntry entry = new CmsEntry();
    entry.documentId = UUID.randomUUID().toString();
    entry.contentType = contentType;
    entry.locale = locale != null ? locale : "en";
    entry.status = "draft";
    entry.data = data;
    entry.persist();
    Log.debugf(
        "Created entry: %s (type=%s, locale=%s)", entry.documentId, contentType, entry.locale);
    return entry;
  }

  /** Creates an entry with an explicit creator. */
  @Transactional
  public CmsEntry createWithCreator(
      String contentType, Map<String, Object> data, String locale, Long createdById) {
    CmsEntry entry = create(contentType, data, locale);
    entry.createdById = createdById;
    entry.persist();
    return entry;
  }

  /** Finds a single entry by document ID. */
  public CmsEntry findByDocumentId(String documentId) {
    return CmsEntry.find("documentId", documentId).firstResult();
  }

  /** Finds a published entry by document ID and locale. */
  public CmsEntry findPublished(String documentId, String locale) {
    return CmsEntry.findByDocumentId(documentId, "published", locale);
  }

  /** Finds a draft entry by document ID and locale. */
  public CmsEntry findDraft(String documentId, String locale) {
    return CmsEntry.findByDocumentId(documentId, "draft", locale);
  }

  /** Lists entries matching the given query parameters. */
  public List<CmsEntry> list(CmsQuery query) {
    return CmsQueryBuilder.list(query);
  }

  /** Counts entries matching the given query parameters. */
  public long count(CmsQuery query) {
    return CmsQueryBuilder.count(query);
  }

  /** Updates the data payload of an existing draft entry. */
  @Transactional
  public CmsEntry update(String documentId, Map<String, Object> data, Long updatedById) {
    return update(documentId, data, updatedById, null);
  }

  /** Updates the data payload of an existing draft entry for a specific locale. */
  @Transactional
  public CmsEntry update(String documentId, Map<String, Object> data, Long updatedById, String locale) {
    String resolvedLocale = locale != null ? locale : "en";
    CmsEntry entry = findDraft(documentId, resolvedLocale);
    if (entry == null) {
      throw new IllegalArgumentException("Draft entry not found for documentId=" + documentId + " locale=" + resolvedLocale);
    }
    entry.data = data;
    if (updatedById != null) {
      entry.updatedById = updatedById;
    }
    entry.persist();
    Log.debugf("Updated entry: %s", documentId);
    return entry;
  }

  /** Publishes a draft entry (creates a published version). */
  @Transactional
  public CmsEntry publish(String documentId, Long publishedById) {
    return publish(documentId, publishedById, null);
  }

  /** Publishes a draft entry for a specific locale. */
  @Transactional
  public CmsEntry publish(String documentId, Long publishedById, String locale) {
    String resolvedLocale = locale != null ? locale : "en";
    CmsEntry draft = findDraft(documentId, resolvedLocale);
    if (draft == null) {
      throw new IllegalArgumentException("Draft entry not found for documentId=" + documentId + " locale=" + resolvedLocale);
    }

    // Delete existing published version
    CmsEntry published = findPublished(documentId, draft.locale);
    if (published != null) {
      published.delete();
    }

    // Create published copy
    CmsEntry publishedCopy = new CmsEntry();
    publishedCopy.documentId = draft.documentId;
    publishedCopy.contentType = draft.contentType;
    publishedCopy.locale = draft.locale;
    publishedCopy.status = "published";
    publishedCopy.versionNumber = draft.versionNumber;
    publishedCopy.data = draft.data;
    publishedCopy.createdAt = draft.createdAt;
    publishedCopy.createdById = draft.createdById;
    publishedCopy.publishedAt = Instant.now();
    publishedCopy.publishedById = publishedById;
    publishedCopy.persist();

    // Update draft: increment version
    draft.versionNumber++;
    draft.persist();

    Log.infof("Published entry: %s (type=%s)", documentId, draft.contentType);
    return publishedCopy;
  }

  /** Unpublishes an entry (removes the published version). */
  @Transactional
  public void unpublish(String documentId) {
    unpublish(documentId, null);
  }

  /** Unpublishes an entry for a specific locale. */
  @Transactional
  public void unpublish(String documentId, String locale) {
    String resolvedLocale = locale != null ? locale : "en";
    CmsEntry published = findPublished(documentId, resolvedLocale);
    if (published != null) {
      published.delete();
      Log.infof("Unpublished entry: %s (locale=%s)", documentId, resolvedLocale);
    }
  }

  /** Deletes all versions of a document and cascades to relations. */
  @Transactional
  public void delete(String documentId) {
    // Cascade: remove all relations involving this document
    CmsRelation.delete("sourceDocumentId = ?1 or targetDocumentId = ?1", documentId);

    // Remove all entry rows for this document
    long deleted = CmsEntry.delete("documentId", documentId);
    Log.infof("Deleted entry %s (%d rows removed)", documentId, deleted);
  }

  /** Returns all versions of a document across locales and statuses. */
  public List<CmsEntry> findDocumentVersions(String documentId) {
    return CmsEntry.findDocumentVersions(documentId);
  }
}
