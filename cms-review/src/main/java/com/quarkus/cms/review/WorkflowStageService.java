package com.quarkus.cms.review;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for managing content stage transitions within review workflows.
 * <p>
 * Handles moving entries between workflow stages, enforcing role-based
 * permissions, and triggering audit and notification events on transitions.
 * </p>
 */
@ApplicationScoped
public class WorkflowStageService {

    @Inject
    WorkflowService workflowService;

    // ---- Stage Transitions ----

    /**
     * Moves an entry forward to the next stage in its workflow.
     *
     * @param documentId the entry's document ID
     * @param locale     the locale
     * @param userId     the user performing the transition
     * @param comment    optional comment
     * @return the updated assignment
     * @throws IllegalStateException if the entry is at the final stage or no workflow is assigned
     */
    @Transactional
    public EntryStageAssignment advanceToNextStage(String documentId, String locale,
                                                     Long userId, String comment) {
        EntryStageAssignment assignment = getAssignmentOrThrow(documentId, locale);
        WorkflowStage currentStage = WorkflowStage.findById(assignment.stageId);

        if (currentStage == null) {
            throw new IllegalStateException("Current stage not found for entry: " + documentId);
        }

        if (Boolean.TRUE.equals(currentStage.isFinal)) {
            throw new IllegalStateException(
                "Entry '" + documentId + "' is already at the final stage: " + currentStage.name);
        }

        WorkflowStage nextStage = WorkflowStage.findNextStage(assignment.workflowId, currentStage.stageOrder);
        if (nextStage == null) {
            throw new IllegalStateException("No next stage available for entry: " + documentId);
        }

        return transitionToStage(assignment, currentStage, nextStage, userId, comment, "advance");
    }

    /**
     * Moves an entry back to a previous stage (e.g. send back for revision).
     *
     * @param documentId the entry's document ID
     * @param locale     the locale
     * @param targetStageId the stage to move back to
     * @param userId     the user performing the transition
     * @param comment    required comment explaining why
     * @return the updated assignment
     */
    @Transactional
    public EntryStageAssignment moveToStage(String documentId, String locale,
                                              Long targetStageId, Long userId, String comment) {
        EntryStageAssignment assignment = getAssignmentOrThrow(documentId, locale);
        WorkflowStage currentStage = WorkflowStage.findById(assignment.stageId);
        WorkflowStage targetStage = WorkflowStage.findById(targetStageId);

        if (currentStage == null) {
            throw new IllegalStateException("Current stage not found");
        }
        if (targetStage == null) {
            throw new IllegalArgumentException("Target stage not found: " + targetStageId);
        }
        if (targetStage.id.equals(currentStage.id)) {
            throw new IllegalArgumentException("Entry is already at stage '" + targetStage.name + "'");
        }
        if (targetStage.workflowId != assignment.workflowId) {
            throw new IllegalArgumentException("Target stage does not belong to the same workflow");
        }

        return transitionToStage(assignment, currentStage, targetStage, userId, comment, "manual");
    }

    /**
     * Bulk-advances multiple entries to their next stages.
     *
     * @param transitions list of {documentId, locale} maps
     * @param userId      the user performing the transitions
     * @return summary of results
     */
    @Transactional
    public BulkTransitionResult bulkAdvance(List<Map<String, String>> transitions, Long userId) {
        int succeeded = 0;
        int failed = 0;
        StringBuilder errorSummary = new StringBuilder();

        for (Map<String, String> t : transitions) {
            String docId = t.get("documentId");
            String locale = t.getOrDefault("locale", "en");
            try {
                advanceToNextStage(docId, locale, userId, null);
                succeeded++;
            } catch (Exception e) {
                failed++;
                if (errorSummary.length() < 500) {
                    if (errorSummary.length() > 0) errorSummary.append("; ");
                    errorSummary.append(docId).append(": ").append(e.getMessage());
                }
            }
        }

        return new BulkTransitionResult(succeeded, failed, errorSummary.toString());
    }

    // ---- Permissions ----

    /**
     * Checks if a user role has permission to transition to a given stage.
     *
     * @param stage    the target stage
     * @param roleName the user's role (e.g. "editor", "admin")
     * @return true if the user can transition to this stage
     */
    public boolean canTransitionToStage(WorkflowStage stage, String roleName) {
        if (stage.permissions == null || stage.permissions.isEmpty()) {
            return true; // No restrictions = anyone can transition
        }
        Object permission = stage.permissions.get(roleName);
        if (permission instanceof Boolean) {
            return (Boolean) permission;
        }
        if (permission instanceof String) {
            return "true".equalsIgnoreCase((String) permission)
                || "can_transition".equalsIgnoreCase((String) permission);
        }
        return false;
    }

    /**
     * Returns the list of roles that can transition to a given stage.
     */
    public List<String> getPermittedRoles(WorkflowStage stage) {
        if (stage.permissions == null || stage.permissions.isEmpty()) {
            return List.of("*"); // All roles
        }
        return stage.permissions.entrySet().stream()
            .filter(e -> {
                Object v = e.getValue();
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof String) return "true".equalsIgnoreCase((String) v)
                    || "can_transition".equalsIgnoreCase((String) v);
                return false;
            })
            .map(Map.Entry::getKey)
            .toList();
    }

    // ---- Query helpers ----

    /** Gets all entries at a specific stage. */
    public List<EntryStageAssignment> getEntriesAtStage(Long stageId) {
        return EntryStageAssignment.findByStage(stageId);
    }

    /** Gets the stage name for a document. */
    public String getCurrentStageName(String documentId, String locale) {
        EntryStageAssignment assignment = EntryStageAssignment.findByDocument(documentId, locale);
        if (assignment == null) return null;
        WorkflowStage stage = WorkflowStage.findById(assignment.stageId);
        return stage != null ? stage.name : null;
    }

    /** Gets the current stage ID for a document. */
    public Long getCurrentStageId(String documentId, String locale) {
        EntryStageAssignment assignment = EntryStageAssignment.findByDocument(documentId, locale);
        return assignment != null ? assignment.stageId : null;
    }

    // ---- Internal helpers ----

    private EntryStageAssignment getAssignmentOrThrow(String documentId, String locale) {
        EntryStageAssignment assignment = EntryStageAssignment.findByDocument(documentId, locale);
        if (assignment == null) {
            throw new IllegalStateException(
                "No workflow assignment found for entry: " + documentId + " (locale: " + locale + ")");
        }
        return assignment;
    }

    private EntryStageAssignment transitionToStage(EntryStageAssignment assignment,
                                                     WorkflowStage fromStage,
                                                     WorkflowStage toStage,
                                                     Long userId, String comment,
                                                     String transitionType) {
        Long fromStageId = fromStage != null ? fromStage.id : null;

        // Update assignment
        assignment.stageId = toStage.id;
        assignment.updatedAt = Instant.now();
        assignment.persist();

        // Record history
        workflowService.recordTransition(
            assignment.documentId, assignment.contentType, assignment.locale,
            assignment.workflowId, fromStageId, toStage.id,
            userId, comment, transitionType);

        return assignment;
    }

    // ---- Result holder ----

    public static class BulkTransitionResult {
        public final int succeeded;
        public final int failed;
        public final String errorSummary;

        public BulkTransitionResult(int succeeded, int failed, String errorSummary) {
            this.succeeded = succeeded;
            this.failed = failed;
            this.errorSummary = errorSummary;
        }
    }
}
