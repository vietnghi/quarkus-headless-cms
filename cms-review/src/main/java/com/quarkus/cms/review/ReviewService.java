package com.quarkus.cms.review;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.draft.model.ContentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing content review workflows.
 * <p>
 * Enables content teams to enforce a review gate before publication:
 * <ol>
 *   <li>Author submits draft → {@link ReviewStatus#PENDING}</li>
 *   <li>Reviewer approves → {@link ReviewStatus#APPROVED}</li>
 *   <li>Author can now publish the approved draft</li>
 *   <li>Alternative: reviewer rejects or requests changes</li>
 * </ol>
 * <p>
 * The service validates state transitions and enforces that only
 * a draft can be submitted for review, and only an approved review
 * allows publication via the {@code DraftPublishService}.
 */
@ApplicationScoped
public class ReviewService {

    // ---- Submit for Review ----

    /**
     * Submits a draft entry for review.
     * <p>
     * The entry must be in DRAFT status. An existing active review
     * (PENDING or CHANGES_REQUESTED) will be cancelled first.
     *
     * @param documentId the document to review
     * @param locale     the locale
     * @param requestedById the user submitting for review
     * @return the newly created review request
     * @throws IllegalStateException if the entry is not a draft
     */
    @Transactional
    public CmsReview submitForReview(String documentId, String locale, Long requestedById) {
        CmsEntry draft = CmsEntry.findByDocumentId(documentId, ContentStatus.DRAFT.getValue(), locale);
        if (draft == null) {
            throw new IllegalStateException(
                "No draft found to submit for review: documentId=" + documentId + " locale=" + locale);
        }

        // Cancel any existing active review
        CmsReview existing = CmsReview.findActiveReview(documentId, locale);
        if (existing != null) {
            existing.status = ReviewStatus.CANCELLED.getValue();
            existing.resolvedAt = Instant.now();
            existing.persist();
        }

        var review = new CmsReview();
        review.documentId = documentId;
        review.contentType = draft.contentType;
        review.locale = locale;
        review.status = ReviewStatus.PENDING.getValue();
        review.requestedById = requestedById;
        review.persist();

        return review;
    }

    /**
     * Submits a draft for review with an optional preferred reviewer.
     */
    @Transactional
    public CmsReview submitForReview(String documentId, String locale, Long requestedById, Long reviewerId) {
        CmsReview review = submitForReview(documentId, locale, requestedById);
        review.reviewerId = reviewerId;
        review.persist();
        return review;
    }

    // ---- Review Actions ----

    /**
     * Approves a pending review, allowing the author to publish.
     *
     * @param reviewId   the review request ID
     * @param reviewerId the user performing the review
     * @param comment    optional approval notes
     * @return the updated review
     * @throws IllegalStateException if the review is not in PENDING state
     */
    @Transactional
    public CmsReview approve(Long reviewId, Long reviewerId, String comment) {
        CmsReview review = findReviewOrThrow(reviewId);

        if (!ReviewStatus.PENDING.getValue().equals(review.status)) {
            throw new IllegalStateException(
                "Cannot approve a review with status '" + review.status
                + "'. Only pending reviews can be approved.");
        }

        review.status = ReviewStatus.APPROVED.getValue();
        review.reviewerId = reviewerId;
        review.comment = comment;
        review.resolvedAt = Instant.now();
        review.persist();

        return review;
    }

    /**
     * Rejects a pending review.
     *
     * @param reviewId   the review request ID
     * @param reviewerId the user performing the review
     * @param comment    required rejection reason
     * @return the updated review
     * @throws IllegalStateException if the review is not in PENDING state
     */
    @Transactional
    public CmsReview reject(Long reviewId, Long reviewerId, String comment) {
        CmsReview review = findReviewOrThrow(reviewId);

        if (!ReviewStatus.PENDING.getValue().equals(review.status)) {
            throw new IllegalStateException(
                "Cannot reject a review with status '" + review.status
                + "'. Only pending reviews can be rejected.");
        }

        review.status = ReviewStatus.REJECTED.getValue();
        review.reviewerId = reviewerId;
        review.comment = comment;
        review.resolvedAt = Instant.now();
        review.persist();

        return review;
    }

    /**
     * Requests changes on a pending review, sending it back to the author
     * for revision. The entry stays in draft and can be re-submitted later.
     *
     * @param reviewId   the review request ID
     * @param reviewerId the user requesting changes
     * @param comment    required description of changes needed
     * @return the updated review
     * @throws IllegalStateException if the review is not in PENDING state
     */
    @Transactional
    public CmsReview requestChanges(Long reviewId, Long reviewerId, String comment) {
        CmsReview review = findReviewOrThrow(reviewId);

        if (!ReviewStatus.PENDING.getValue().equals(review.status)) {
            throw new IllegalStateException(
                "Cannot request changes on a review with status '" + review.status
                + "'. Only pending reviews can be changed.");
        }

        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException(
                "A comment describing the required changes is mandatory.");
        }

        review.status = ReviewStatus.CHANGES_REQUESTED.getValue();
        review.reviewerId = reviewerId;
        review.comment = comment;
        review.persist();

        return review;
    }

    // ---- Queries ----

    /**
     * Gets the active review for a document, if any.
     *
     * @return the active review (PENDING or CHANGES_REQUESTED), or null
     */
    public CmsReview getActiveReview(String documentId, String locale) {
        return CmsReview.findActiveReview(documentId, locale);
    }

    /** Checks if a document has an active review. */
    public boolean hasActiveReview(String documentId, String locale) {
        return CmsReview.hasActiveReview(documentId, locale);
    }

    /**
     * Checks if a document has been approved and can be published.
     * The latest review must have status APPROVED and no active review exists.
     */
    public boolean isApprovedForPublish(String documentId, String locale) {
        // If there's an active review, it means a newer review cycle is pending
        if (CmsReview.hasActiveReview(documentId, locale)) {
            return false;
        }
        CmsReview latest = CmsReview.findLatestReview(documentId, locale);
        return latest != null && ReviewStatus.APPROVED.getValue().equals(latest.status);
    }

    /** Returns all reviews for a document, newest first. */
    public List<CmsReview> getReviews(String documentId, String locale) {
        return CmsReview.findByDocumentId(documentId, locale);
    }

    /** Returns the latest review for a document. */
    public CmsReview getLatestReview(String documentId, String locale) {
        return CmsReview.findLatestReview(documentId, locale);
    }

    /** Finds all reviews assigned to a specific reviewer. */
    public List<CmsReview> getReviewsByReviewer(Long reviewerId) {
        return CmsReview.findByReviewer(reviewerId);
    }

    /** Finds all reviews submitted by a specific user. */
    public List<CmsReview> getReviewsByRequestor(Long requestedById) {
        return CmsReview.findByRequestor(requestedById);
    }

    /** Finds reviews by status. */
    public List<CmsReview> getReviewsByStatus(String status) {
        return CmsReview.findByStatus(status);
    }

    /**
     * Cancels an active review (e.g. if the author decides to discard the draft).
     */
    @Transactional
    public void cancelReview(Long reviewId) {
        CmsReview review = findReviewOrThrow(reviewId);
        if (!review.status.equals(ReviewStatus.PENDING.getValue())
            && !review.status.equals(ReviewStatus.CHANGES_REQUESTED.getValue())) {
            throw new IllegalStateException(
                "Cannot cancel a review with status '" + review.status + "'");
        }
        review.status = ReviewStatus.CANCELLED.getValue();
        review.resolvedAt = Instant.now();
        review.persist();
    }

    // ---- Internal helpers ----

    private CmsReview findReviewOrThrow(Long reviewId) {
        CmsReview review = CmsReview.findById(reviewId);
        if (review == null) {
            throw new IllegalArgumentException("Review not found: " + reviewId);
        }
        return review;
    }
}
