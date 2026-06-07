package com.quarkus.cms.admin.api.service;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.relation.RelationService;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.draft.DraftPublishService;
import com.quarkus.cms.draft.model.ContentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;

/**
 * Admin-level service for content management operations.
 *
 * Wraps {@link DraftPublishService}, {@link RelationService}, and direct
 * {@link CmsEntry} queries to provide a unified admin API boundary:
 * list/filter entries, CRUD, publish lifecycle, version history,
 * bulk operations, and import/export.
 */
@ApplicationScoped
public class ContentManagerService {

    @Inject
    DraftPublishService draftPublishService;

    @Inject
    RelationService relationService;

    @Inject
    SchemaStorageService schemaStorageService;

    // ---- Query / List ----

    /**
     * Counts entries matching the given filters.
     */
    public long countEntries(String contentType, String status, String locale) {
        return buildQuery(contentType, status, locale, true).count();
    }

    /**
     * Lists entries with pagination for the admin content manager.
     */
    public List<CmsEntry> listEntries(String contentType, String status, String locale,
                                       int page, int pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        return buildQuery(contentType, status, locale, false)
            .page(page, pageSize)
            .list();
    }

    /**
     * Lists ALL entries for a given content type (no pagination).
     */
    public List<CmsEntry> listAllEntries(String contentType, String locale) {
        return buildQuery(contentType, null, locale, false).list();
    }

    /**
     * Retrieves a specific entry by document ID and locale.
     */
    public CmsEntry getEntry(String documentId, String locale) {
        CmsEntry entry = draftPublishService.getDraft(documentId, locale);
        if (entry != null) return entry;
        entry = draftPublishService.getPublished(documentId, locale);
        if (entry != null) return entry;
        return (CmsEntry) CmsEntry.find(
            "documentId = ?1 and locale = ?2 and status <> ?3 order by versionNumber desc",
            documentId, locale, ContentStatus.DRAFT.getValue()).firstResult();
    }

    /**
     * Retrieves the entry for a single-type content type.
     */
    public CmsEntry getSingleTypeEntry(String contentType, String locale) {
        CmsEntry draft = CmsEntry.find(
            "contentType = ?1 and status = ?2 and locale = ?3",
            contentType, ContentStatus.DRAFT.getValue(), locale).firstResult();
        if (draft != null) return draft;
        return CmsEntry.find(
            "contentType = ?1 and status = ?2 and locale = ?3 order by versionNumber desc",
            contentType, ContentStatus.PUBLISHED.getValue(), locale).firstResult();
    }

    // ---- Create / Update ----

    @Transactional
    public CmsEntry createEntry(String contentType, Map<String, Object> data,
                                 String locale, Long userId) {
        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) {
            throw new IllegalArgumentException("Unknown content type: " + contentType);
        }

        if (ct.isSingleType()) {
            CmsEntry existing = getSingleTypeEntry(contentType, locale);
            if (existing != null) {
                throw new IllegalStateException(
                    "Single type '" + contentType + "' already has an entry for locale '" + locale + "'");
            }
        }

        return draftPublishService.createDraft(contentType, data, locale, userId);
    }

    @Transactional
    public CmsEntry updateEntry(String documentId, Map<String, Object> data,
                                 String locale, Long userId) {
        return draftPublishService.updateDraft(documentId, data, locale, userId);
    }

    @Transactional
    public CmsEntry upsertSingleType(String contentType, Map<String, Object> data,
                                      String locale, Long userId) {
        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) {
            throw new IllegalArgumentException("Unknown content type: " + contentType);
        }
        if (!ct.isSingleType()) {
            throw new IllegalArgumentException(
                "upsertSingleType can only be used with single-type content types, got: " + contentType);
        }

        CmsEntry existing = getSingleTypeEntry(contentType, locale);
        if (existing != null) {
            return draftPublishService.updateDraft(existing.documentId, data, locale, userId);
        }
        return draftPublishService.createDraft(contentType, data, locale, userId);
    }

    // ---- Publish / Unpublish / Discard ----

    @Transactional
    public CmsEntry publishEntry(String documentId, String locale, Long userId) {
        return draftPublishService.publish(documentId, locale, userId);
    }

    @Transactional
    public void unpublishEntry(String documentId, String locale) {
        draftPublishService.unpublish(documentId, locale);
    }

    @Transactional
    public void discardDraft(String documentId, String locale) {
        draftPublishService.discardDraft(documentId, locale);
    }

    // ---- Delete ----

    @Transactional
    public long deleteDocument(String documentId) {
        long relationCount = relationService.removeAllForDocument(documentId);
        long deleted = CmsEntry.delete("documentId", documentId);
        return deleted;
    }

    // ---- Bulk Operations ---- //

    /**
     * Bulk publishes multiple entries.
     */
    @Transactional
    public List<Map<String, Object>> bulkPublish(List<String> documentIds, String locale, Long userId) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String docId : documentIds) {
            Map<String, Object> result = new HashMap<>();
            result.put("documentId", docId);
            try {
                CmsEntry published = draftPublishService.publish(docId, locale, userId);
                result.put("success", true);
                result.put("versionNumber", published.versionNumber);
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    /**
     * Bulk unpublishes multiple entries.
     */
    @Transactional
    public List<Map<String, Object>> bulkUnpublish(List<String> documentIds, String locale) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String docId : documentIds) {
            Map<String, Object> result = new HashMap<>();
            result.put("documentId", docId);
            try {
                draftPublishService.unpublish(docId, locale);
                result.put("success", true);
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    /**
     * Bulk deletes multiple entries.
     */
    @Transactional
    public List<Map<String, Object>> bulkDelete(List<String> documentIds) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String docId : documentIds) {
            Map<String, Object> result = new HashMap<>();
            result.put("documentId", docId);
            try {
                long deleted = CmsEntry.delete("documentId", docId);
                result.put("success", deleted > 0);
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    // ---- Import ---- //

    /**
     * Imports entries into a content type.
     *
     * @param contentType the content type UID
     * @param entries     list of entry data maps
     * @param locale      locale for the entries
     * @param mode        "create" to always create new, "upsert" to update if documentId matches
     * @return list of results with documentId and success/error
     */
    @Transactional
    public List<Map<String, Object>> importEntries(
            String contentType, List<Map<String, Object>> entries,
            String locale, String mode) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> entryData : entries) {
            Map<String, Object> result = new HashMap<>();
            try {
                if ("upsert".equals(mode) && entryData.containsKey("documentId")) {
                    String docId = (String) entryData.get("documentId");
                    CmsEntry existing = getEntry(docId, locale);
                    if (existing != null) {
                        CmsEntry updated = draftPublishService.updateDraft(docId, entryData, locale, 1L);
                        result.put("documentId", updated.documentId);
                        result.put("action", "updated");
                    } else {
                        CmsEntry created = draftPublishService.createDraft(contentType, entryData, locale, 1L);
                        result.put("documentId", created.documentId);
                        result.put("action", "created");
                    }
                } else {
                    CmsEntry created = draftPublishService.createDraft(contentType, entryData, locale, 1L);
                    result.put("documentId", created.documentId);
                    result.put("action", "created");
                }
                result.put("success", true);
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    // ---- Version History ----

    public List<CmsEntry> getVersions(String documentId, String locale) {
        return draftPublishService.getVersions(documentId, locale);
    }

    public CmsEntry getVersion(String documentId, int versionNumber, String locale) {
        return draftPublishService.getVersion(documentId, versionNumber, locale);
    }

    public boolean hasUnpublishedChanges(String documentId, String locale) {
        return draftPublishService.hasUnpublishedChanges(documentId, locale);
    }

    // ---- Relations ----

    @Transactional
    public CmsRelation attachRelation(String sourceDocumentId, String sourceType,
                                       String targetDocumentId, String targetType,
                                       String fieldName, int orderIndex) {
        return relationService.attach(sourceDocumentId, sourceType,
            targetDocumentId, targetType, fieldName, orderIndex);
    }

    @Transactional
    public void detachRelation(String sourceDocumentId, String fieldName, String targetDocumentId) {
        relationService.detach(sourceDocumentId, fieldName, targetDocumentId);
    }

    public List<CmsRelation> findRelations(String sourceDocumentId, String fieldName) {
        if (fieldName != null && !fieldName.isBlank()) {
            return relationService.findRelations(sourceDocumentId, fieldName);
        }
        return CmsRelation.list("sourceDocumentId", sourceDocumentId);
    }

    @Transactional
    public void reorderRelations(String sourceDocumentId, String fieldName,
                                  List<String> orderedTargetIds) {
        relationService.reorder(sourceDocumentId, fieldName, orderedTargetIds);
    }

    // ---- Internal helpers ----

    private io.quarkus.hibernate.orm.panache.PanacheQuery<CmsEntry> buildQuery(
        String contentType, String status, String locale, boolean countOnly) {

        StringBuilder hql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        hql.append("contentType = :contentType");
        params.put("contentType", contentType);

        if (status != null && !status.isBlank()) {
            hql.append(" and status = :status");
            params.put("status", status.toLowerCase());
        }

        if (locale != null && !locale.isBlank()) {
            hql.append(" and locale = :locale");
            params.put("locale", locale);
        } else {
            hql.append(" and locale = :locale");
            params.put("locale", "en");
        }

        String query = hql.toString();
        if (!countOnly) {
            query += " order by updatedAt desc";
        }

        return CmsEntry.find(query, params);
    }
}
