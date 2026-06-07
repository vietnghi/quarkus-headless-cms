package com.quarkus.cms.review;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A content review request linking a reviewer to a specific entry.
 * <p>
 * Supports the full review lifecycle:
 * <ol>
 *   <li>Submit draft → status = PENDING</li>
 *   <li>Reviewer approves → status = APPROVED (entry can be published)</li>
 *   <li>Reviewer rejects → status = REJECTED (entry stays in draft)</li>
 *   <li>Reviewer requests changes → status = CHANGES_REQUESTED (author revises)</li>
 *   <li>After changes, author re-submits → new review cycle</li>
 * </ol>
 */
@Entity
@Table(name = "cms_reviews", indexes = {
    @Index(name = "idx_review_document", columnList = "document_id, locale"),
    @Index(name = "idx_review_reviewer", columnList = "reviewer_id"),
    @Index(name = "idx_review_status", columnList = "status"),
    @Index(name = "idx_review_created", columnList = "created_at")
})
public class CmsReview extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** The document under review. */
    @Column(name = "document_id", nullable = false, length = 36)
    public String documentId;

    /** Content type UID (denormalized for query convenience). */
    @Column(name = "content_type", nullable = false, length = 100)
    public String contentType;

    /** Locale of the entry under review. */
    @Column(name = "locale", nullable = false, length = 10)
    public String locale = "en";

    /** Current review status. */
    @Column(name = "status", nullable = false, length = 20)
    public String status = ReviewStatus.PENDING.getValue();

    /** The user who submitted the entry for review. */
    @Column(name = "requested_by_id", nullable = false)
    public Long requestedById;

    /** The assigned reviewer (null until one picks it up or is assigned). */
    @Column(name = "reviewer_id")
    public Long reviewerId;

    /** Review comment (approval/rejection/change request notes). */
    @Column(name = "comment", length = 2000)
    public String comment;

    /** Timestamps. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    @Column(name = "resolved_at")
    public Instant resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- Helper Finders ----

    /** Finds the active (PENDING or CHANGES_REQUESTED) review for a document, if any. */
    public static CmsReview findActiveReview(String documentId, String locale) {
        return find("documentId = ?1 and locale = ?2 and status in ('pending', 'changes_requested')",
            documentId, locale).firstResult();
    }

    /** Finds all review requests for a document, newest first. */
    public static java.util.List<CmsReview> findByDocumentId(String documentId, String locale) {
        return list("documentId = ?1 and locale = ?2 order by createdAt desc",
            documentId, locale);
    }

    /** Finds all reviews assigned to (or completed by) a specific reviewer. */
    public static java.util.List<CmsReview> findByReviewer(Long reviewerId) {
        return list("reviewerId = ?1 order by createdAt desc", reviewerId);
    }

    /** Finds all reviews submitted by a specific user. */
    public static java.util.List<CmsReview> findByRequestor(Long requestedById) {
        return list("requestedById = ?1 order by createdAt desc", requestedById);
    }

    /** Finds reviews by status. */
    public static java.util.List<CmsReview> findByStatus(String status) {
        return list("status = ?1 order by createdAt desc", status);
    }

    /** Finds the most recent review for a document. */
    public static CmsReview findLatestReview(String documentId, String locale) {
        return find("documentId = ?1 and locale = ?2 order by createdAt desc",
            documentId, locale).firstResult();
    }

    /** Checks if a document has an active review. */
    public static boolean hasActiveReview(String documentId, String locale) {
        return count("documentId = ?1 and locale = ?2 and status in ('pending', 'changes_requested')",
            documentId, locale) > 0;
    }
}
