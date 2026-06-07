package com.quarkus.cms.review;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A single stage within a Review Workflow.
 * <p>
 * Stages are ordered within a workflow and represent steps in the content
 * approval pipeline (e.g. Draft → Review → Published). Each stage can have
 * role-based permissions controlling which roles can transition content to
 * this stage.
 * </p>
 */
@Entity
@Table(name = "cms_workflow_stages", indexes = {
    @Index(name = "idx_stage_workflow", columnList = "workflow_id"),
    @Index(name = "idx_stage_order", columnList = "workflow_id, stage_order")
})
public class WorkflowStage extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Parent workflow. */
    @Column(name = "workflow_id", nullable = false)
    public Long workflowId;

    /** Stage display name (e.g. "Draft", "In Review", "Approved", "Published"). */
    @Column(name = "name", nullable = false, length = 100)
    public String name;

    /** Stage order within the workflow (0-based). */
    @Column(name = "stage_order", nullable = false)
    public Integer stageOrder = 0;

    /** Hex color for UI display (e.g. "#4945FF"). */
    @Column(name = "color", length = 7)
    public String color = "#4945FF";

    /**
     * JSONB map of role → permission for transitioning content TO this stage.
     * <p>
     * Format: {@code {"role_name": "can_transition"}} or more granular:
     * {@code {"editor": true, "admin": true}}.
     * If empty or null, any content manager can transition to this stage.
     * </p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    public Map<String, Object> permissions;

    /** Whether this is the initial (first) stage of the workflow. */
    @Transient
    public boolean isInitial;

    /** Whether this is the final stage (content is considered published/complete). */
    @Column(name = "is_final", nullable = false)
    public Boolean isFinal = false;

    /** Whether this stage allows editing of the content entry. */
    @Column(name = "allows_editing", nullable = false)
    public Boolean allowsEditing = true;

    /** Timestamps. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- Queries ----

    /** Finds all stages for a workflow, ordered by stageOrder. */
    public static java.util.List<WorkflowStage> findByWorkflowId(Long workflowId) {
        return list("workflowId = ?1 order by stageOrder", workflowId);
    }

    /** Finds the initial/first stage of a workflow. */
    public static WorkflowStage findInitialStage(Long workflowId) {
        return find("workflowId = ?1 order by stageOrder", workflowId).firstResult();
    }

    /** Finds the next stage after a given stage order. */
    public static WorkflowStage findNextStage(Long workflowId, Integer currentOrder) {
        return find("workflowId = ?1 and stageOrder > ?2 order by stageOrder",
            workflowId, currentOrder).firstResult();
    }

    /** Finds the final stage of a workflow. */
    public static WorkflowStage findFinalStage(Long workflowId) {
        return find("workflowId = ?1 and isFinal = ?2", workflowId, true).firstResult();
    }

    /** Finds stages by workflow ID with permission check data. */
    public static List<WorkflowStage> findStagesWithPermissions(Long workflowId) {
        return list("workflowId = ?1 order by stageOrder", workflowId);
    }

    /** Counts stages in a workflow. */
    public static long countByWorkflowId(Long workflowId) {
        return count("workflowId", workflowId);
    }

    /** Reorders a stage. */
    public static void updateOrder(Long stageId, int newOrder) {
        update("stageOrder = ?1 where id = ?2", newOrder, stageId);
    }
}
