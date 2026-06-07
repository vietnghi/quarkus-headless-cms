package com.quarkus.cms.review;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A Review Workflow definition, grouping a set of ordered stages that
 * content entries pass through on their way to publication.
 * <p>
 * Each workflow has a name, an ordered list of stages (Draft → Review → Published),
 * and optionally applies to a set of content type UIDs. A default workflow exists
 * for content types not explicitly assigned to any workflow.
 * </p>
 * <p>
 * Strapi-compatible: mirrors the enterprise review-workflows plugin.
 * </p>
 */
@Entity
@Table(name = "cms_workflows", indexes = {
    @Index(name = "idx_workflow_name", columnList = "name"),
    @Index(name = "idx_workflow_default", columnList = "is_default")
})
public class CmsWorkflow extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Human-readable workflow name (e.g. "Simple Approval", "Editorial Review"). */
    @Column(name = "name", nullable = false, length = 100)
    public String name;

    /** Optional description of this workflow's purpose. */
    @Column(name = "description", length = 500)
    public String description;

    /**
     * JSONB list of content type UIDs this workflow applies to.
     * Empty list means it can be used by any content type (the default workflow).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_types", columnDefinition = "jsonb")
    public List<String> contentTypes = new ArrayList<>();

    /**
     * Whether this is the default workflow for content types not explicitly configured.
     * There should be exactly one default workflow in the system.
     */
    @Column(name = "is_default", nullable = false)
    public Boolean isDefault = false;

    /** Whether this workflow is active and can be assigned. */
    @Column(name = "active", nullable = false)
    public Boolean active = true;

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

    /** Finds the default workflow. */
    public static CmsWorkflow findDefault() {
        return find("isDefault = ?1 and active = ?2", true, true).firstResult();
    }

    /** Finds all active workflows. */
    public static java.util.List<CmsWorkflow> listActive() {
        return list("active = ?1 order by name", true);
    }

    /** Finds the workflow that covers a given content type UID, or null. */
    public static CmsWorkflow findByContentType(String contentType) {
        // First try exact match in contentTypes list
        List<CmsWorkflow> workflows = list("active = ?1", true);
        for (CmsWorkflow wf : workflows) {
            if (wf.contentTypes != null && wf.contentTypes.contains(contentType)) {
                return wf;
            }
        }
        return null;
    }

    /** Finds all workflows (including inactive). */
    public static java.util.List<CmsWorkflow> listAll() {
        return list("order by name");
    }
}
