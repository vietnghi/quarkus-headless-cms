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

    // ---- Filtered Querying ----

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

    /**
     * Deletes audit entries older than the given number of days.
     *
     * @param days age threshold in days (entries older than this are deleted)
     * @return number of deleted entries
     */
    @Transactional
    public long deleteOlderThan(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be positive");
        }
        java.time.Instant cutoff = java.time.Instant.now()
            .minus(days, java.time.temporal.ChronoUnit.DAYS);
        return CmsAuditLog.delete("createdAt < ?1", cutoff);
    }

    /**
     * Deletes audit entries matching optional filters (for admin purge).
     * At least olderThanDays must be provided to prevent accidental full-table purges.
     *
     * @param action        optional action filter
     * @param userId        optional user id filter
     * @param contentType   optional content type filter
     * @param olderThanDays delete entries older than this many days (required)
     * @return number of deleted entries
     */
    @Transactional
    public long deleteFiltered(String action, Long userId, String contentType, int olderThanDays) {
        if (olderThanDays <= 0) {
            throw new IllegalArgumentException("olderThanDays must be positive");
        }
        var fb = new FilterQueryBuilder();
        java.time.Instant cutoff = java.time.Instant.now()
            .minus(olderThanDays, java.time.temporal.ChronoUnit.DAYS);
        fb.addLt("createdAt", cutoff);
        fb.addEq("action", action);
        fb.addEq("userId", userId);
        fb.addEq("contentType", contentType);
        return CmsAuditLog.delete(fb.getQuery(), fb.getParams());
    }

    // ---- Paginated Filtered Querying ----

    /**
     * Builds a HQL query string and ordered parameter list from filter criteria.
     * The query starts with "1=1" and appends numbered parameters (?1, ?2, ...).
     */
    private static class FilterQueryBuilder {
        final StringBuilder hql = new StringBuilder("1=1");
        final List<Object> params = new ArrayList<>();
        int paramIndex = 0;

        void addEq(String field, Object value) {
            if (value == null) return;
            if (value instanceof String s && s.isBlank()) return;
            paramIndex++;
            hql.append(" and ").append(field).append(" = ?").append(paramIndex);
            params.add(value);
        }

        void addGte(String field, Object value) {
            if (value == null) return;
            paramIndex++;
            hql.append(" and ").append(field).append(" >= ?").append(paramIndex);
            params.add(value);
        }

        void addLte(String field, Object value) {
            if (value == null) return;
            paramIndex++;
            hql.append(" and ").append(field).append(" <= ?").append(paramIndex);
            params.add(value);
        }

        void addLt(String field, Object value) {
            if (value == null) return;
            paramIndex++;
            hql.append(" and ").append(field).append(" < ?").append(paramIndex);
            params.add(value);
        }

        String getQuery() { return hql.toString(); }
        Object[] getParams() { return params.toArray(); }
    }

    /**
     * Searches audit log entries with combined filters and pagination.
     *
     * @param action      optional action type filter
     * @param userId      optional user id filter
     * @param contentType optional content type filter
     * @param startDate   optional start date (ISO-8601) for createdAt range
     * @param endDate     optional end date (ISO-8601) for createdAt range
     * @param page        zero-based page number
     * @param pageSize    items per page (max 100)
     * @return filtered list of CmsAuditLog entries, newest first
     */
    public List<CmsAuditLog> findFiltered(String action, Long userId, String contentType,
                                           String startDate, String endDate,
                                           int page, int pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        page = Math.max(page, 0);

        var fb = new FilterQueryBuilder();
        applyFilters(fb, action, userId, contentType, startDate, endDate);
        fb.hql.append(" order by createdAt desc");

        return CmsAuditLog.find(fb.getQuery(), fb.getParams())
            .page(page, pageSize)
            .list();
    }

    /**
     * Counts audit log entries matching the given filters.
     *
     * @param action      optional action type filter
     * @param userId      optional user id filter
     * @param contentType optional content type filter
     * @param startDate   optional start date (ISO-8601)
     * @param endDate     optional end date (ISO-8601)
     * @return count of matching entries
     */
    public long countFiltered(String action, Long userId, String contentType,
                               String startDate, String endDate) {
        var fb = new FilterQueryBuilder();
        applyFilters(fb, action, userId, contentType, startDate, endDate);
        return CmsAuditLog.count(fb.getQuery(), fb.getParams());
    }

    private void applyFilters(FilterQueryBuilder fb, String action, Long userId,
                               String contentType, String startDate, String endDate) {
        fb.addEq("action", action);
        fb.addEq("userId", userId);
        fb.addEq("contentType", contentType);
        if (startDate != null && !startDate.isBlank()) {
            try {
                fb.addGte("createdAt", java.time.Instant.parse(startDate));
            } catch (Exception e) {
                // ignore invalid date, query will just not filter
            }
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
                fb.addLte("createdAt", java.time.Instant.parse(endDate));
            } catch (Exception e) {
                // ignore invalid date
            }
        }
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
