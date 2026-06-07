package com.quarkus.cms.review;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Assigns a content entry (documentId + locale) to a specific workflow stage.
 * <p>
 * Each entry-locale pair has exactly one current stage assignment at any time.
 * When an entry is created, it's assigned to the initial stage of its workflow.
 * Stage transitions update this assignment and record history.
 * </p>
 */
@Entity
@Table(name = "cms_entry_stage_assignments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_entry_stage", columnNames = {"document_id", "locale"})
}, indexes = {
    @Index(name = "idx_entry_stage_doc", columnList = "document_id, locale"),
    @Index(name = "idx_entry_stage_stage", columnList = "stage_id"),
    @Index(name = "idx_entry_stage_workflow", columnList = "workflow_id")
})
public class EntryStageAssignment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** The document assigned to a stage. */
    @Column(name = "document_id", nullable = false, length = 36)
    public String documentId;

    /** Content type UID (denormalized for query convenience). */
    @Column(name = "content_type", nullable = false, length = 100)
    public String contentType;

    /** Locale of the entry. */
    @Column(name = "locale", nullable = false, length = 10)
    public String locale = "en";

    /** The current workflow this entry follows. */
    @Column(name = "workflow_id", nullable = false)
    public Long workflowId;

    /** The current stage this entry is in. */
    @Column(name = "stage_id", nullable = false)
    public Long stageId;

    /** Timestamps. */
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    @Column(name = "assigned_by_id")
    public Long assignedById;

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

    /** Finds the current stage assignment for a document in a locale. */
    public static EntryStageAssignment findByDocument(String documentId, String locale) {
        return find("documentId = ?1 and locale = ?2", documentId, locale).firstResult();
    }

    /** Finds all entries at a specific workflow stage. */
    public static java.util.List<EntryStageAssignment> findByStage(Long stageId) {
        return list("stageId = ?1", stageId);
    }

    /** Finds all entries in a specific workflow. */
    public static java.util.List<EntryStageAssignment> findByWorkflow(Long workflowId) {
        return list("workflowId = ?1", workflowId);
    }

    /** Counts entries at a specific stage. */
    public static long countByStage(Long stageId) {
        return count("stageId", stageId);
    }

    /** Counts entries by workflow. */
    public static long countByWorkflow(Long workflowId) {
        return count("workflowId", workflowId);
    }

    /** Counts entries at a specific stage within a content type. */
    public static long countByStageAndContentType(Long stageId, String contentType) {
        return count("stageId = ?1 and contentType = ?2", stageId, contentType);
    }

    /** Deletes the assignment for a document (e.g. on entry deletion). */
    public static long deleteByDocument(String documentId) {
        return delete("documentId", documentId);
    }
}
