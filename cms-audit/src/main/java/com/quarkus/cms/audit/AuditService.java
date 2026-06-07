package com.quarkus.cms.audit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for recording and querying audit log entries.
 * <p>
 * Provides a clean API for other modules to log content changes and
 * for the admin UI to retrieve change history with pagination and filtering.
 * <p>
 * Actions are identified by uppercase names: CREATE, UPDATE, PUBLISH,
 * UNPUBLISH, DELETE, DISCARD, RESTORE, and REVIEW_*.
 */
@ApplicationScoped
public class AuditService {

    // ---- Recording ----

    /**
     * Records a single audit log entry.
     *
     * @param documentId  the document that changed
     * @param entryId     the CmsEntry primary key (may be null for bulk ops)
     * @param contentType the content type UID
     * @param locale      the locale code
     * @param action      the action performed (e.g. CREATE, UPDATE, PUBLISH)
     * @param userId      the user who performed the action
     * @param changes     map of changed fields {@code {field: {"old": x, "new": y}}}
     * @param summary     optional human-readable change summary
     * @return the persisted audit log entry
     */
    @Transactional
    public CmsAuditLog record(String documentId, Long entryId, String contentType,
                               String locale, String action, Long userId,
                               Map<String, Object> changes, String summary) {
        var log = new CmsAuditLog();
        log.documentId = documentId;
        log.entryId = entryId;
        log.contentType = contentType;
        log.locale = locale != null ? locale : "en";
        log.action = action;
        log.userId = userId;
        log.changes = changes;
        log.summary = summary;
        log.createdAt = Instant.now();
        log.persist();
        return log;
    }

    /**
     * Records an audit entry with computed changes between old and new data.
     *
     * @param documentId  the document ID
     * @param entryId     the entry PK
     * @param contentType content type UID
     * @param locale      locale code
     * @param action      the action
     * @param userId      the acting user
     * @param oldData     the data map before the change
     * @param newData     the data map after the change
     * @return the persisted audit log entry
     */
    @Transactional
    public CmsAuditLog recordDiff(String documentId, Long entryId, String contentType,
                                   String locale, String action, Long userId,
                                   Map<String, Object> oldData, Map<String, Object> newData) {
        Map<String, Object> changes = computeDiff(oldData, newData);
        String summary = summarizeChanges(changes, action);
        return record(documentId, entryId, contentType, locale, action, userId, changes, summary);
    }

    // ---- Querying ----

    /** Returns all audit entries for a document, newest first. */
    public List<CmsAuditLog> getHistory(String documentId) {
        return CmsAuditLog.findByDocumentId(documentId);
    }

    /** Returns paginated audit entries for a content type. */
    public List<CmsAuditLog> getHistoryByContentType(String contentType, int page, int pageSize) {
        return CmsAuditLog.findByContentType(contentType, page, pageSize);
    }

    /** Returns audit entries for a specific user. */
    public List<CmsAuditLog> getHistoryByUser(Long userId) {
        return CmsAuditLog.findByUserId(userId);
    }

    /** Returns audit entries by action type. */
    public List<CmsAuditLog> getHistoryByAction(String action) {
        return CmsAuditLog.findByAction(action);
    }

    /** Returns recent audit entries across the entire system, newest first. */
    public List<CmsAuditLog> getRecentHistory(int limit) {
        limit = Math.min(Math.max(limit, 1), 200);
        return CmsAuditLog.find("order by createdAt desc")
            .page(0, limit)
            .list();
    }

    /** Counts audit entries for a document. */
    public long countByDocument(String documentId) {
        return CmsAuditLog.countByDocumentId(documentId);
    }

    /**
     * Deletes audit entries for a given document (e.g. when the document is deleted).
     * Use with caution — audit logs should generally be immutable.
     */
    @Transactional
    public long deleteHistory(String documentId) {
        return CmsAuditLog.delete("documentId", documentId);
    }

    // ---- Diff computation ----

    /**
     * Computes a diff between old and new data maps.
     * Returns a map where each key is a changed field name and the value
     * is a map of {@code {"old": oldValue, "new": newValue}}.
     */
    public static Map<String, Object> computeDiff(Map<String, Object> oldData,
                                                   Map<String, Object> newData) {
        Map<String, Object> diff = new LinkedHashMap<>();

        Set<String> allKeys = new LinkedHashSet<>();
        if (oldData != null) allKeys.addAll(oldData.keySet());
        if (newData != null) allKeys.addAll(newData.keySet());

        for (String key : allKeys) {
            Object oldVal = (oldData != null) ? oldData.get(key) : null;
            Object newVal = (newData != null) ? newData.get(key) : null;

            if (!Objects.equals(oldVal, newVal)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("old", oldVal);
                entry.put("new", newVal);
                diff.put(key, entry);
            }
        }

        return diff;
    }

    /**
     * Generates a human-readable summary from a changes map.
     */
    private static String summarizeChanges(Map<String, Object> changes, String action) {
        if (changes == null || changes.isEmpty()) {
            return action + " (no field changes)";
        }
        int changedCount = changes.size();
        if (changedCount == 1) {
            String fieldName = changes.keySet().iterator().next();
            return "Updated " + fieldName;
        }
        return "Updated " + changedCount + " fields";
    }
}
