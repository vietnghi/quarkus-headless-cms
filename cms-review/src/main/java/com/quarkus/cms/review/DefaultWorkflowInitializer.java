package com.quarkus.cms.review;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Initializes the default review workflow on application startup.
 * <p>
 * If no workflow exists (fresh installation), creates the default
 * "Draft → Published" workflow. If a default workflow already exists,
 * ensures it has the correct stages.
 * </p>
 */
@ApplicationScoped
public class DefaultWorkflowInitializer {

    @Inject
    WorkflowService workflowService;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        try {
            CmsWorkflow existingDefault = CmsWorkflow.findDefault();
            if (existingDefault != null) {
                // Ensure it has at least Draft → Published stages
                long stageCount = WorkflowStage.countByWorkflowId(existingDefault.id);
                if (stageCount < 2) {
                    Log.infov("Default workflow '{0}' has only {1} stage(s). Rebuilding...",
                        existingDefault.name, stageCount);
                    rebuildDefaultWorkflow(existingDefault);
                }
                Log.infov("Default review workflow found: '{0}' ({1} stages)",
                    existingDefault.name, stageCount);
                return;
            }

            // No workflow at all — check if we already have any workflow
            long anyWorkflow = CmsWorkflow.count();
            if (anyWorkflow == 0) {
                Log.info("No review workflows found. Creating default 'Draft → Published' workflow.");
                CmsWorkflow defaultWf = workflowService.createSimpleWorkflow(
                    "Default (Draft → Published)", true);
                Log.infov("Created default workflow: '{0}' (id={1})",
                    defaultWf.name, defaultWf.id);
            } else {
                Log.info("Review workflows exist but none is marked as default. "
                    + "Marking the first one as default.");
                CmsWorkflow first = CmsWorkflow.listAll().get(0);
                first.isDefault = true;
                first.persist();
            }
        } catch (Exception e) {
            Log.errorv("Failed to initialize default review workflow: {0}", e.getMessage());
        }
    }

    @Transactional
    void rebuildDefaultWorkflow(CmsWorkflow workflow) {
        // Clear existing stages
        WorkflowStage.delete("workflowId", workflow.id);

        // Create Draft stage
        WorkflowStage draft = new WorkflowStage();
        draft.workflowId = workflow.id;
        draft.name = "Draft";
        draft.stageOrder = 0;
        draft.color = "#666687";
        draft.isFinal = false;
        draft.allowsEditing = true;
        draft.persist();

        // Create Published stage
        WorkflowStage published = new WorkflowStage();
        published.workflowId = workflow.id;
        published.name = "Published";
        published.stageOrder = 1;
        published.color = "#28A745";
        published.isFinal = true;
        published.allowsEditing = false;
        published.persist();
    }
}
