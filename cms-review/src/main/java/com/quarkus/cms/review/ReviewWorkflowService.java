package com.quarkus.cms.review;

import com.quarkus.cms.audit.AuditService;
import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;
import com.quarkus.cms.webhooks.service.LifecycleEventBus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Integrated service connecting review workflows, stage transitions, audit logging,
 * and webhook notifications.
 * <p>
 * Orchestrates the full content approval lifecycle:
 * <ol>
 *   <li>Submit for review → creates CmsReview + advances to next stage</li>
 *   <li>Approve/reject/request-changes → updates CmsReview + moves to appropriate stage</li>
 *   <li>Audit trail via AuditService + WorkflowStageHistory</li>
 *   <li>Webhook notifications via LifecycleEventBus</li>
 * </ol>
 * </p>
 */
@ApplicationScoped
public class ReviewWorkflowService {

    @Inject
    ReviewService reviewService;

    @Inject
    WorkflowService workflowService;

    @Inject
    WorkflowStageService stageService;

    @Inject
    AuditService auditService;

    @Inject
    LifecycleEventBus eventBus;

    // ---- Submit for Review with Workflow ----

    /**
     * Submits an entry for review, creating a review request and advancing
     * the entry to the next workflow stage.
     *
     * @param documentId    the document to review
     * @param contentType   the content type
     * @param locale        the locale
     * @param requestedById the user submitting for review
     * @return the created review request
     */
    @Transactional
    public CmsReview submitForReview(String documentId, String contentType,
                                      String locale, Long requestedById) {
        // Ensure the entry is assigned to a workflow
        workflowService.assignEntryToInitialStage(documentId, contentType, locale, requestedById);

        // Submit the review
        CmsReview review = reviewService.submitForReview(documentId, locale, requestedById);

        // Advance to the next workflow stage (from Draft to In Review / next stage)
        try {
            EntryStageAssignment assignment = stageService.advanceToNextStage(
                documentId, locale, requestedById, "Submitted for review");
            fireReviewEvent(documentId, contentType, locale, requestedById,
                "submit_for_review", assignment);
        } catch (Exception e) {
            // Stage advancement is best-effort; the review was still created
            java.util.logging.Logger.getLogger(getClass().getName())
                .warning("Could not advance workflow stage: " + e.getMessage());
        }

        // Audit log
        auditService.record(documentId, null, contentType, locale,
            "REVIEW_SUBMIT", requestedById,
            Map.of("reviewId", Map.of("new", review.id)),
            "Submitted for review");

        return review;
    }

    /**
     * Submits for review with a preferred reviewer.
     */
    @Transactional
    public CmsReview submitForReview(String documentId, String contentType,
                                      String locale, Long requestedById, Long reviewerId) {
        CmsReview review = submitForReview(documentId, contentType, locale, requestedById);
        review.reviewerId = reviewerId;
        review.persist();
        return review;
    }

    // ---- Review Actions with Workflow ----

    /**
     * Approves a review and advances the entry to the appropriate stage.
     *
     * @param reviewId   the review request ID
     * @param reviewerId the reviewer
     * @param comment    optional approval notes
     * @return the updated review
     */
    @Transactional
    public CmsReview approve(Long reviewId, Long reviewerId, String comment) {
        CmsReview review = reviewService.approve(reviewId, reviewerId, comment);

        // Advance the entry's workflow stage
        try {
            EntryStageAssignment assignment = EntryStageAssignment.findByDocument(
                review.documentId, review.locale);
            if (assignment != null) {
                stageService.advanceToNextStage(review.documentId, review.locale,
                    reviewerId, comment != null ? comment : "Approved");
                fireReviewEvent(review.documentId, review.contentType, review.locale,
                    reviewerId, "approved", assignment);
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .warning("Could not advance workflow stage after approval: " + e.getMessage());
        }

        // Audit log
        auditService.record(review.documentId, null, review.contentType, review.locale,
            "REVIEW_APPROVED", reviewerId,
            Map.of("reviewId", Map.of("new", review.id),
                "status", Map.of("old", "pending", "new", "approved")),
            "Review approved" + (comment != null ? ": " + comment : ""));

        return review;
    }

    /**
     * Rejects a review and moves the entry back to the previous stage.
     *
     * @param reviewId   the review request ID
     * @param reviewerId the reviewer
     * @param comment    rejection reason
     * @return the updated review
     */
    @Transactional
    public CmsReview reject(Long reviewId, Long reviewerId, String comment) {
        CmsReview review = reviewService.reject(reviewId, reviewerId, comment);

        // Move back to initial stage
        moveBackToDraft(review, reviewerId, comment, "rejected");

        // Audit log
        auditService.record(review.documentId, null, review.contentType, review.locale,
            "REVIEW_REJECTED", reviewerId,
            Map.of("reviewId", Map.of("new", review.id),
                "status", Map.of("old", "pending", "new", "rejected")),
            "Review rejected" + (comment != null ? ": " + comment : ""));

        return review;
    }

    /**
     * Requests changes on a review and moves the entry back for revision.
     *
     * @param reviewId   the review request ID
     * @param reviewerId the reviewer
     * @param comment    required change description
     * @return the updated review
     */
    @Transactional
    public CmsReview requestChanges(Long reviewId, Long reviewerId, String comment) {
        CmsReview review = reviewService.requestChanges(reviewId, reviewerId, comment);

        // Move back to initial stage for revision
        moveBackToDraft(review, reviewerId, comment, "changes_requested");

        // Audit log
        auditService.record(review.documentId, null, review.contentType, review.locale,
            "REVIEW_CHANGES_REQUESTED", reviewerId,
            Map.of("reviewId", Map.of("new", review.id),
                "status", Map.of("old", "pending", "new", "changes_requested")),
            "Changes requested" + (comment != null ? ": " + comment : ""));

        return review;
    }

    // ---- Stage Transitions with Audit ----

    /**
     * Manually moves an entry to a specific workflow stage.
     */
    @Transactional
    public EntryStageAssignment moveEntryToStage(String documentId, String contentType,
                                                   String locale, Long targetStageId,
                                                   Long userId, String comment) {
        // Ensure assigned
        workflowService.assignEntryToInitialStage(documentId, contentType, locale, userId);

        EntryStageAssignment result = stageService.moveToStage(
            documentId, locale, targetStageId, userId, comment);

        // Audit
        auditService.record(documentId, null, contentType, locale,
            "STAGE_TRANSITION", userId,
            Map.of("toStageId", Map.of("new", targetStageId)),
            comment != null ? comment : "Manual stage transition");

        return result;
    }

    // ---- Bulk Operations ----

    /**
     * Bulk submits multiple entries for review.
     */
    @Transactional
    public List<Map<String, Object>> bulkSubmitForReview(
        List<String> documentIds, String contentType, String locale, Long userId) {
        return documentIds.stream().map(docId -> {
            try {
                CmsReview review = submitForReview(docId, contentType, locale, userId);
                return Map.<String, Object>of(
                    "documentId", docId, "success", true, "reviewId", review.id);
            } catch (Exception e) {
                return Map.<String, Object>of(
                    "documentId", docId, "success", false, "error", e.getMessage());
            }
        }).toList();
    }

    // ---- Query ----

    /** Gets full workflow status for an entry. */
    public Map<String, Object> getWorkflowStatus(String documentId, String locale) {
        EntryStageAssignment assignment = EntryStageAssignment.findByDocument(documentId, locale);
        if (assignment == null) {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("documentId", documentId);
            result.put("locale", locale);
            result.put("assigned", false);
            return result;
        }

        WorkflowStage currentStage = WorkflowStage.findById(assignment.stageId);
        CmsWorkflow workflow = CmsWorkflow.findById(assignment.workflowId);
        CmsReview activeReview = CmsReview.findActiveReview(documentId, locale);

        // Get available next stages
        List<WorkflowStage> allStages = WorkflowStage.findByWorkflowId(assignment.workflowId);
        WorkflowStage nextStage = currentStage != null
            ? WorkflowStage.findNextStage(assignment.workflowId, currentStage.stageOrder) : null;

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("locale", locale);
        result.put("assigned", true);
        result.put("workflowId", assignment.workflowId);
        result.put("workflowName", workflow != null ? workflow.name : null);
        result.put("currentStageId", assignment.stageId);
        result.put("currentStageName", currentStage != null ? currentStage.name : null);
        result.put("currentStageColor", currentStage != null ? currentStage.color : null);
        result.put("isFinalStage", currentStage != null ? currentStage.isFinal : false);
        result.put("allowsEditing", currentStage != null ? currentStage.allowsEditing : true);
        result.put("nextStageId", nextStage != null ? nextStage.id : null);
        result.put("nextStageName", nextStage != null ? nextStage.name : null);
        result.put("hasActiveReview", activeReview != null);
        result.put("activeReviewId", activeReview != null ? activeReview.id : null);
        result.put("activeReviewStatus", activeReview != null ? activeReview.status : null);
        result.put("allStages", allStages.stream().map(s -> {
            Map<String, Object> sm = new java.util.HashMap<>();
            sm.put("id", s.id);
            sm.put("name", s.name);
            sm.put("order", s.stageOrder);
            sm.put("color", s.color);
            sm.put("isFinal", s.isFinal);
            return sm;
        }).toList());
        return result;
    }

    // ---- Internal ----

    private void moveBackToDraft(CmsReview review, Long userId, String comment, String reason) {
        try {
            EntryStageAssignment assignment = EntryStageAssignment.findByDocument(
                review.documentId, review.locale);
            if (assignment != null) {
                WorkflowStage initialStage = WorkflowStage.findInitialStage(assignment.workflowId);
                if (initialStage != null && !initialStage.id.equals(assignment.stageId)) {
                    stageService.moveToStage(review.documentId, review.locale,
                        initialStage.id, userId,
                        comment != null ? comment : "Review " + reason);
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .warning("Could not move entry back to draft after " + reason + ": " + e.getMessage());
        }
    }

    private void fireReviewEvent(String documentId, String contentType, String locale,
                                  Long userId, String action,
                                  EntryStageAssignment assignment) {
        try {
            String eventKey = "review." + action;
            eventBus.fireAfterUpdate(contentType, documentId,
                Map.of("reviewAction", action,
                    "currentStageId", assignment != null ? assignment.stageId : null),
                locale, userId);
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                .warning("Could not fire review event: " + e.getMessage());
        }
    }
}
