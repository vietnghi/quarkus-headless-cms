package com.quarkus.cms.review;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing Review Workflow definitions and their stages.
 * <p>
 * Supports CRUD operations on workflows and stages, default workflow
 * lifecycle, and workflow-to-content-type mapping.
 * </p>
 */
@ApplicationScoped
public class WorkflowService {

    // ---- Workflow CRUD ----

    /**
     * Creates a new workflow with its initial set of stages.
     *
     * @param name        workflow name
     * @param description optional description
     * @param contentTypes content types this workflow applies to (empty = any)
     * @param stageNames  ordered list of stage names (at least one required)
     * @param isDefault   whether this is the default workflow
     * @return the created workflow
     */
    @Transactional
    public CmsWorkflow createWorkflow(String name, String description,
                                       List<String> contentTypes,
                                       List<String> stageNames,
                                       boolean isDefault) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workflow name is required");
        }
        if (stageNames == null || stageNames.isEmpty()) {
            throw new IllegalArgumentException("At least one stage is required");
        }

        // If setting as default, unset any existing default
        if (isDefault) {
            clearExistingDefault();
        }

        CmsWorkflow workflow = new CmsWorkflow();
        workflow.name = name.trim();
        workflow.description = description != null ? description.trim() : null;
        workflow.contentTypes = contentTypes != null ? new ArrayList<>(contentTypes) : new ArrayList<>();
        workflow.isDefault = isDefault;
        workflow.active = true;
        workflow.persist();

        // Create stages
        for (int i = 0; i < stageNames.size(); i++) {
            WorkflowStage stage = new WorkflowStage();
            stage.workflowId = workflow.id;
            stage.name = stageNames.get(i).trim();
            stage.stageOrder = i;
            stage.isFinal = (i == stageNames.size() - 1);
            stage.allowsEditing = !stage.isFinal; // Final stage typically doesn't allow editing
            if (i == 0) {
                stage.color = "#666687"; // Gray for initial/draft
            } else if (stage.isFinal) {
                stage.color = "#28A745"; // Green for final/published
            } else {
                stage.color = "#4945FF"; // Blue for review stages
            }
            stage.persist();
        }

        return workflow;
    }

    /**
     * Creates a simple two-stage workflow (Draft → Published).
     * Used as the default for non-configured content types.
     */
    @Transactional
    public CmsWorkflow createSimpleWorkflow(String name, boolean isDefault) {
        return createWorkflow(name, "Simple two-stage workflow: Draft → Published",
            new ArrayList<>(), List.of("Draft", "Published"), isDefault);
    }

    /**
     * Updates a workflow's basic properties.
     */
    @Transactional
    public CmsWorkflow updateWorkflow(Long workflowId, String name, String description,
                                       List<String> contentTypes, Boolean active) {
        CmsWorkflow workflow = findWorkflowOrThrow(workflowId);

        if (name != null && !name.isBlank()) {
            workflow.name = name.trim();
        }
        if (description != null) {
            workflow.description = description.trim();
        }
        if (contentTypes != null) {
            workflow.contentTypes = new ArrayList<>(contentTypes);
        }
        if (active != null) {
            workflow.active = active;
        }
        workflow.persist();
        return workflow;
    }

    /**
     * Sets a workflow as the default, clearing any existing default.
     */
    @Transactional
    public void setAsDefault(Long workflowId) {
        clearExistingDefault();
        CmsWorkflow workflow = findWorkflowOrThrow(workflowId);
        workflow.isDefault = true;
        workflow.persist();
    }

    /**
     * Deletes a workflow and all its stages and assignments.
     * Cannot delete if there are entries assigned to it.
     */
    @Transactional
    public void deleteWorkflow(Long workflowId) {
        CmsWorkflow workflow = findWorkflowOrThrow(workflowId);

        long entryCount = EntryStageAssignment.countByWorkflow(workflowId);
        if (entryCount > 0) {
            throw new IllegalStateException(
                "Cannot delete workflow '" + workflow.name
                    + "': " + entryCount + " entries are still assigned to it. "
                    + "Reassign them first.");
        }

        // Delete stages
        WorkflowStage.delete("workflowId", workflowId);
        // Delete history
        WorkflowStageHistory.delete("workflowId", workflowId);
        // Delete workflow
        workflow.delete();
    }

    // ---- Stage Management ----

    /**
     * Adds a new stage to an existing workflow at the specified position.
     *
     * @param workflowId the workflow
     * @param name       stage name
     * @param afterOrder insert after this position (null = append)
     * @param color      optional hex color
     * @return the created stage
     */
    @Transactional
    public WorkflowStage addStage(Long workflowId, String name, Integer afterOrder, String color) {
        CmsWorkflow workflow = findWorkflowOrThrow(workflowId);

        List<WorkflowStage> stages = WorkflowStage.findByWorkflowId(workflowId);
        int newOrder = (afterOrder != null) ? afterOrder + 1 : stages.size();

        // Shift existing stages after the insertion point
        for (WorkflowStage s : stages) {
            if (s.stageOrder >= newOrder) {
                s.stageOrder = s.stageOrder + 1;
                s.persist();
            }
        }

        WorkflowStage stage = new WorkflowStage();
        stage.workflowId = workflowId;
        stage.name = name.trim();
        stage.stageOrder = newOrder;
        stage.color = color != null ? color : "#4945FF";
        stage.isFinal = false;
        stage.allowsEditing = true;
        stage.persist();

        // Recalculate isFinal
        recalculateFinalFlag(workflowId);

        return stage;
    }

    /**
     * Updates a stage's properties.
     */
    @Transactional
    public WorkflowStage updateStage(Long stageId, String name, String color,
                                      Boolean isFinal, Boolean allowsEditing,
                                      Map<String, Object> permissions) {
        WorkflowStage stage = findStageOrThrow(stageId);

        if (name != null && !name.isBlank()) {
            stage.name = name.trim();
        }
        if (color != null) {
            stage.color = color;
        }
        if (isFinal != null) {
            stage.isFinal = isFinal;
            if (isFinal) {
                // If this is now the final stage, unmark any other final stage
                List<WorkflowStage> siblings = WorkflowStage.findByWorkflowId(stage.workflowId);
                for (WorkflowStage s : siblings) {
                    if (!s.id.equals(stageId) && s.isFinal) {
                        s.isFinal = false;
                        s.persist();
                    }
                }
            }
        }
        if (allowsEditing != null) {
            stage.allowsEditing = allowsEditing;
        }
        if (permissions != null) {
            stage.permissions = new LinkedHashMap<>(permissions);
        }
        stage.persist();
        return stage;
    }

    /**
     * Reorders a stage within its workflow.
     */
    @Transactional
    public void reorderStage(Long stageId, int newOrder) {
        WorkflowStage stage = findStageOrThrow(stageId);
        List<WorkflowStage> stages = WorkflowStage.findByWorkflowId(stage.workflowId);

        // Remove from current position
        stages.removeIf(s -> s.id.equals(stageId));

        // Clamp
        newOrder = Math.max(0, Math.min(newOrder, stages.size()));

        // Insert at new position
        stages.add(newOrder, stage);

        // Re-assign orders
        for (int i = 0; i < stages.size(); i++) {
            stages.get(i).stageOrder = i;
            stages.get(i).persist();
        }

        recalculateFinalFlag(stage.workflowId);
    }

    /**
     * Removes a stage from a workflow.
     * Entries at this stage will be moved to the previous stage.
     */
    @Transactional
    public void removeStage(Long stageId) {
        WorkflowStage stage = findStageOrThrow(stageId);
        Long workflowId = stage.workflowId;

        // Find previous stage to move entries
        List<WorkflowStage> stages = WorkflowStage.findByWorkflowId(workflowId);
        WorkflowStage prevStage = null;
        for (WorkflowStage s : stages) {
            if (s.stageOrder < stage.stageOrder) {
                prevStage = s;
            }
        }

        // Move entries at this stage to the previous stage (or first stage)
        List<EntryStageAssignment> assignments = EntryStageAssignment.findByStage(stageId);
        Long moveToStageId = prevStage != null ? prevStage.id : stages.get(0).id;
        for (EntryStageAssignment assignment : assignments) {
            recordTransition(assignment.documentId, assignment.contentType,
                assignment.locale, workflowId, stageId, moveToStageId,
                assignment.assignedById, "Stage '" + stage.name + "' was removed",
                "auto_assign");
            assignment.stageId = moveToStageId;
            assignment.persist();
        }

        // Delete the stage
        stage.delete();

        // Recalculate order and final flag for remaining stages
        List<WorkflowStage> remaining = WorkflowStage.findByWorkflowId(workflowId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).stageOrder = i;
            remaining.get(i).persist();
        }
        recalculateFinalFlag(workflowId);
    }

    // ---- Stage Transitions ----

    /**
     * Records a stage transition in the history log.
     */
    @Transactional
    public WorkflowStageHistory recordTransition(String documentId, String contentType,
                                                   String locale, Long workflowId,
                                                   Long fromStageId, Long toStageId,
                                                   Long userId, String comment,
                                                   String transitionType) {
        String fromStageName = null;
        String toStageName;

        if (fromStageId != null) {
            WorkflowStage fromStage = WorkflowStage.findById(fromStageId);
            fromStageName = fromStage != null ? fromStage.name : null;
        }
        WorkflowStage toStage = WorkflowStage.findById(toStageId);
        toStageName = toStage != null ? toStage.name : "Unknown";

        WorkflowStageHistory history = new WorkflowStageHistory();
        history.documentId = documentId;
        history.contentType = contentType;
        history.locale = locale;
        history.workflowId = workflowId;
        history.fromStageId = fromStageId;
        history.toStageId = toStageId;
        history.fromStageName = fromStageName;
        history.toStageName = toStageName;
        history.transitionType = transitionType;
        history.userId = userId;
        history.comment = comment;
        history.persist();

        return history;
    }

    // ---- Queries ----

    /** Gets a workflow by ID. */
    public CmsWorkflow getWorkflow(Long workflowId) {
        return findWorkflowOrThrow(workflowId);
    }

    /** Lists all workflows. */
    public List<CmsWorkflow> listWorkflows() {
        return CmsWorkflow.listAll();
    }

    /** Lists active workflows. */
    public List<CmsWorkflow> listActiveWorkflows() {
        return CmsWorkflow.listActive();
    }

    /** Gets the default workflow, creating one if none exists. */
    public CmsWorkflow getOrCreateDefaultWorkflow() {
        CmsWorkflow defaultWf = CmsWorkflow.findDefault();
        if (defaultWf == null) {
            return createSimpleWorkflow("Default (Draft → Published)", true);
        }
        return defaultWf;
    }

    /** Resolves the appropriate workflow for a content type. */
    public CmsWorkflow resolveWorkflowForContentType(String contentType) {
        CmsWorkflow wf = CmsWorkflow.findByContentType(contentType);
        if (wf != null) {
            return wf;
        }
        return getOrCreateDefaultWorkflow();
    }

    /** Gets all stages for a workflow. */
    public List<WorkflowStage> getStages(Long workflowId) {
        return WorkflowStage.findByWorkflowId(workflowId);
    }

    /** Gets the initial stage of a workflow. */
    public WorkflowStage getInitialStage(Long workflowId) {
        return WorkflowStage.findInitialStage(workflowId);
    }

    /** Gets the next stage after the current one. */
    public WorkflowStage getNextStage(Long workflowId, Integer currentOrder) {
        return WorkflowStage.findNextStage(workflowId, currentOrder);
    }

    /** Gets stage history for a document. */
    public List<WorkflowStageHistory> getStageHistory(String documentId, String locale) {
        return WorkflowStageHistory.findByDocument(documentId, locale);
    }

    /** Gets the current stage assignment for a document. */
    public EntryStageAssignment getCurrentAssignment(String documentId, String locale) {
        return EntryStageAssignment.findByDocument(documentId, locale);
    }

    /**
     * Assigns an entry to the initial stage of its resolved workflow.
     * Creates the initial EntryStageAssignment and records a history entry.
     */
    @Transactional
    public EntryStageAssignment assignEntryToInitialStage(String documentId, String contentType,
                                                           String locale, Long userId) {
        // Check if already assigned
        EntryStageAssignment existing = EntryStageAssignment.findByDocument(documentId, locale);
        if (existing != null) {
            return existing;
        }

        CmsWorkflow workflow = resolveWorkflowForContentType(contentType);
        WorkflowStage initialStage = getInitialStage(workflow.id);
        if (initialStage == null) {
            throw new IllegalStateException(
                "No initial stage found for workflow '" + workflow.name + "'");
        }

        EntryStageAssignment assignment = new EntryStageAssignment();
        assignment.documentId = documentId;
        assignment.contentType = contentType;
        assignment.locale = locale;
        assignment.workflowId = workflow.id;
        assignment.stageId = initialStage.id;
        assignment.assignedById = userId;
        assignment.persist();

        // Record history
        recordTransition(documentId, contentType, locale, workflow.id,
            null, initialStage.id, userId, "Entry created, assigned to initial stage",
            "auto_assign");

        return assignment;
    }

    // ---- Internal Helpers ----

    private CmsWorkflow findWorkflowOrThrow(Long workflowId) {
        CmsWorkflow workflow = CmsWorkflow.findById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        return workflow;
    }

    private WorkflowStage findStageOrThrow(Long stageId) {
        WorkflowStage stage = WorkflowStage.findById(stageId);
        if (stage == null) {
            throw new IllegalArgumentException("Stage not found: " + stageId);
        }
        return stage;
    }

    private void clearExistingDefault() {
        CmsWorkflow existingDefault = CmsWorkflow.findDefault();
        if (existingDefault != null) {
            existingDefault.isDefault = false;
            existingDefault.persist();
        }
    }

    private void recalculateFinalFlag(Long workflowId) {
        List<WorkflowStage> stages = WorkflowStage.findByWorkflowId(workflowId);
        for (int i = 0; i < stages.size(); i++) {
            boolean isLast = (i == stages.size() - 1);
            if (stages.get(i).isFinal != isLast) {
                stages.get(i).isFinal = isLast;
                stages.get(i).persist();
            }
        }
    }
}
