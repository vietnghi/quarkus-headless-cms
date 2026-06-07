package com.quarkus.cms.review;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Records every stage transition for audit purposes.
 * <p>
 * Immutable log of when an entry moved from one workflow stage to another,
 * who performed the transition, and any comment provided.
 * This is the workflow-specific audit trail (complementing the general
 * {@link com.quarkus.cms.audit.CmsAuditLog} entries).
 * </p>
 */
@Entity
@Table(name = "cms_workflow_stage_history", indexes = {
    @Index(name = "idx_stage_history_doc", columnList = "document_id, locale"),
    @Index(name = "idx_stage_history_workflow", columnList = "workflow_id"),
    @Index(name = "idx_stage_history_stage", columnList = "to_stage_id"),
    @Index(name = "idx_stage_history_user", columnList = "user_id"),
    @Index(name = "idx_stage_history_created", columnList = "created_at")
})
public class WorkflowStageHistory extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** The document that moved between stages. */
    @Column(name = "document_id", nullable = false, length = 36)
    public String documentId;

    /** Content type UID. */
    @Column(name = "content_type", nullable = false, length = 100)
    public String contentType;

    /** Locale of the entry. */
    @Column(name = "locale", nullable = false, length = 10)
    public String locale = "en";

    /** The workflow this transition belongs to. */
    @Column(name = "workflow_id", nullable = false)
    public Long workflowId;

    /** The stage the entry came from (nullable for initial assignment). */
    @Column(name = "from_stage_id")
    public Long fromStageId;

    /** The stage the entry moved to. */
    @Column(name = "to_stage_id", nullable = false)
    public Long toStageId;

    /** Name of the from-stage (denormalized for queries). */
    @Column(name = "from_stage_name", length = 100)
    public String fromStageName;

    /** Name of the to-stage (denormalized for queries). */
    @Column(name = "to_stage_name", nullable = false, length = 100)
    public String toStageName;

    /** The type of transition (submit, approve, reject, request_changes, auto_assign, manual). */
    @Column(name = "transition_type", nullable = false, length = 30)
    public String transitionType = "manual";

    /** The user who performed the transition. */
    @Column(name = "user_id", nullable = false)
    public Long userId;

    /** Optional comment about the transition. */
    @Column(name = "comment", length = 2000)
    public String comment;

    /** Timestamp when the transition occurred. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ---- Queries ----

    /** Finds all stage transitions for a document, newest first. */
    public static java.util.List<WorkflowStageHistory> findByDocument(String documentId, String locale) {
        return list("documentId = ?1 and locale = ?2 order by createdAt desc",
            documentId, locale);
    }

    /** Finds all stage transitions for a workflow, newest first. */
    public static java.util.List<WorkflowStageHistory> findByWorkflow(Long workflowId) {
        return list("workflowId = ?1 order by createdAt desc", workflowId);
    }

    /** Finds all stage transitions for a specific stage, newest first. */
    public static java.util.List<WorkflowStageHistory> findByStage(Long stageId) {
        return list("toStageId = ?1 order by createdAt desc", stageId);
    }

    /** Finds all transitions performed by a user, newest first. */
    public static java.util.List<WorkflowStageHistory> findByUser(Long userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    /** Finds transitions by type (e.g. "approve", "reject"). */
    public static java.util.List<WorkflowStageHistory> findByTransitionType(String transitionType) {
        return list("transitionType = ?1 order by createdAt desc", transitionType);
    }

    /** Counts transitions to a specific stage. */
    public static long countByToStage(Long stageId) {
        return count("toStageId", stageId);
    }
}
