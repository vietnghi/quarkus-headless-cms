package com.quarkus.cms.audit;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log entry recording a single content mutation.
 * <p>
 * Each row captures who changed what document, when, and what the
 * before/after state looked like. The changes are stored as a JSONB
 * column containing a map of changed field names to {@code {old, new}}
 * value pairs, enabling rich diff rendering in the admin UI.
 */
@Entity
@Table(name = "cms_audit_log", indexes = {
    @Index(name = "idx_audit_document", columnList = "document_id"),
    @Index(name = "idx_audit_content_type", columnList = "content_type"),
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_created", columnList = "created_at")
})
public class CmsAuditLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** The document that was changed (documentId). */
    @Column(name = "document_id", nullable = false, length = 36)
    public String documentId;

    /** The CmsEntry primary key (nullable for bulk operations). */
    @Column(name = "entry_id")
    public Long entryId;

    /** Content type UID (e.g. "api::article.article"). */
    @Column(name = "content_type", nullable = false, length = 100)
    public String contentType;

    /** Locale of the affected entry. */
    @Column(name = "locale", nullable = false, length = 10)
    public String locale = "en";

    /** Auditable action: CREATE, UPDATE, PUBLISH, UNPUBLISH, DELETE, DISCARD, etc. */
    @Column(name = "action", nullable = false, length = 50)
    public String action;

    /** The user who performed the action. */
    @Column(name = "user_id", nullable = false)
    public Long userId;

    /**
     * JSONB map of changed fields: {@code {"fieldName": {"old": ..., "new": ...}}}.
     * For CREATE actions, old values are null. For DELETE actions, new values are null.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    public Map<String, Object> changes;

    /** Brief human-readable summary of the change (e.g. "Updated title and body"). */
    @Column(name = "summary", length = 500)
    public String summary;

    /** Timestamp when the change occurred. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ---- Helper Finders ----

    /** Finds all audit entries for a document, newest first. */
    public static java.util.List<CmsAuditLog> findByDocumentId(String documentId) {
        return list("documentId = ?1 order by createdAt desc", documentId);
    }

    /** Finds audit entries for a given content type with pagination. */
    public static java.util.List<CmsAuditLog> findByContentType(String contentType,
                                                                  int page, int pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        return find("contentType = ?1 order by createdAt desc", contentType)
            .page(page, pageSize)
            .list();
    }

    /** Finds audit entries for a given user, newest first. */
    public static java.util.List<CmsAuditLog> findByUserId(Long userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    /** Finds audit entries by action type, newest first. */
    public static java.util.List<CmsAuditLog> findByAction(String action) {
        return list("action = ?1 order by createdAt desc", action);
    }

    /** Counts total audit entries for a document. */
    public static long countByDocumentId(String documentId) {
        return count("documentId", documentId);
    }
}
