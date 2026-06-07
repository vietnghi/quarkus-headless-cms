package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.review.*;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin REST API for Review Workflow definitions and stage management.
 * <p>
 * Mirrors Strapi v5's review-workflows admin API, providing CRUD for
 * workflows and stages, stage transitions, entry assignment, and
 * workflow status queries.
 * </p>
 * All endpoints require admin authentication.
 */
@Path("/admin/review-workflows")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminReviewWorkflowsResource {

    @Inject
    WorkflowService workflowService;

    @Inject
    WorkflowStageService stageService;

    @Inject
    ReviewWorkflowService reviewWorkflowService;

    // ================================================================
    //  Workflow CRUD
    // ================================================================

    /**
     * Lists all workflows.
     */
    @GET
    @PermissionCheck("admin::review-workflows.read")
    public Response listWorkflows() {
        List<CmsWorkflow> workflows = workflowService.listWorkflows();
        List<Map<String, Object>> result = workflows.stream()
            .map(this::workflowToMap)
            .toList();
        return Response.ok(new StrapiCollectionResponse<>(result)).build();
    }

    /**
     * Gets a single workflow with its stages.
     */
    @GET
    @Path("/{workflowId}")
    @PermissionCheck("admin::review-workflows.read")
    public Response getWorkflow(@PathParam("workflowId") Long workflowId) {
        try {
            CmsWorkflow workflow = workflowService.getWorkflow(workflowId);
            Map<String, Object> data = workflowToMap(workflow);
            data.put("stages", workflowService.getStages(workflowId).stream()
                .map(this::stageToMap)
                .toList());
            return Response.ok(new StrapiSingleResponse<>(data)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    /**
     * Creates a new workflow.
     */
    @POST
    @PermissionCheck("admin::review-workflows.create")
    public Response createWorkflow(Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String description = (String) body.get("description");
            @SuppressWarnings("unchecked")
            List<String> contentTypes = (List<String>) body.get("contentTypes");
            @SuppressWarnings("unchecked")
            List<String> stageNames = (List<String>) body.get("stageNames");
            boolean isDefault = Boolean.TRUE.equals(body.get("isDefault"));

            CmsWorkflow workflow = workflowService.createWorkflow(
                name, description, contentTypes, stageNames, isDefault);

            Map<String, Object> data = workflowToMap(workflow);
            data.put("stages", workflowService.getStages(workflow.id).stream()
                .map(this::stageToMap)
                .toList());
            return Response.status(Response.Status.CREATED)
                .entity(new StrapiSingleResponse<>(data)).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Updates an existing workflow.
     */
    @PUT
    @Path("/{workflowId}")
    @PermissionCheck("admin::review-workflows.update")
    public Response updateWorkflow(@PathParam("workflowId") Long workflowId,
                                    Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String description = (String) body.get("description");
            @SuppressWarnings("unchecked")
            List<String> contentTypes = (List<String>) body.get("contentTypes");
            Boolean active = body.containsKey("active")
                ? Boolean.TRUE.equals(body.get("active")) : null;

            CmsWorkflow workflow = workflowService.updateWorkflow(
                workflowId, name, description, contentTypes, active);

            // Handle set-as-default
            if (Boolean.TRUE.equals(body.get("isDefault"))) {
                workflowService.setAsDefault(workflowId);
            }

            Map<String, Object> data = workflowToMap(workflow);
            data.put("stages", workflowService.getStages(workflow.id).stream()
                .map(this::stageToMap)
                .toList());
            return Response.ok(new StrapiSingleResponse<>(data)).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    /**
     * Deletes a workflow.
     */
    @DELETE
    @Path("/{workflowId}")
    @PermissionCheck("admin::review-workflows.delete")
    public Response deleteWorkflow(@PathParam("workflowId") Long workflowId) {
        try {
            workflowService.deleteWorkflow(workflowId);
            return Response.ok(Map.of("deleted", true, "workflowId", workflowId)).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(StrapiErrorResponse.of(409, "ConflictError", e.getMessage()))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    /**
     * Sets a workflow as the default.
     */
    @POST
    @Path("/{workflowId}/set-default")
    @PermissionCheck("admin::review-workflows.update")
    public Response setAsDefault(@PathParam("workflowId") Long workflowId) {
        try {
            workflowService.setAsDefault(workflowId);
            return Response.ok(Map.of("setAsDefault", true, "workflowId", workflowId)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    // ================================================================
    //  Stage Management
    // ================================================================

    /**
     * Lists all stages for a workflow.
     */
    @GET
    @Path("/{workflowId}/stages")
    @PermissionCheck("admin::review-workflows.read")
    public Response listStages(@PathParam("workflowId") Long workflowId) {
        try {
            List<WorkflowStage> stages = workflowService.getStages(workflowId);
            return Response.ok(new StrapiCollectionResponse<>(
                stages.stream().map(this::stageToMap).toList())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    /**
     * Adds a new stage to a workflow.
     */
    @POST
    @Path("/{workflowId}/stages")
    @PermissionCheck("admin::review-workflows.update")
    public Response addStage(@PathParam("workflowId") Long workflowId,
                              Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            Integer afterOrder = body.containsKey("afterOrder")
                ? ((Number) body.get("afterOrder")).intValue() : null;
            String color = (String) body.get("color");

            WorkflowStage stage = workflowService.addStage(
                workflowId, name, afterOrder, color);
            return Response.status(Response.Status.CREATED)
                .entity(new StrapiSingleResponse<>(stageToMap(stage))).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Updates a stage.
     */
    @PUT
    @Path("/{workflowId}/stages/{stageId}")
    @PermissionCheck("admin::review-workflows.update")
    public Response updateStage(@PathParam("workflowId") Long workflowId,
                                 @PathParam("stageId") Long stageId,
                                 Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String color = (String) body.get("color");
            Boolean isFinal = body.containsKey("isFinal")
                ? Boolean.TRUE.equals(body.get("isFinal")) : null;
            Boolean allowsEditing = body.containsKey("allowsEditing")
                ? Boolean.TRUE.equals(body.get("allowsEditing")) : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> permissions = (Map<String, Object>) body.get("permissions");

            WorkflowStage stage = workflowService.updateStage(
                stageId, name, color, isFinal, allowsEditing, permissions);
            return Response.ok(new StrapiSingleResponse<>(stageToMap(stage))).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    /**
     * Reorders a stage within its workflow.
     */
    @PUT
    @Path("/{workflowId}/stages/{stageId}/reorder")
    @PermissionCheck("admin::review-workflows.update")
    public Response reorderStage(@PathParam("workflowId") Long workflowId,
                                  @PathParam("stageId") Long stageId,
                                  Map<String, Object> body) {
        try {
            int newOrder = ((Number) body.get("newOrder")).intValue();
            workflowService.reorderStage(stageId, newOrder);
            return Response.ok(Map.of("reordered", true, "stageId", stageId,
                "newOrder", newOrder)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    /**
     * Removes a stage from a workflow.
     */
    @DELETE
    @Path("/{workflowId}/stages/{stageId}")
    @PermissionCheck("admin::review-workflows.update")
    public Response removeStage(@PathParam("workflowId") Long workflowId,
                                 @PathParam("stageId") Long stageId) {
        try {
            workflowService.removeStage(stageId);
            return Response.ok(Map.of("removed", true, "stageId", stageId)).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(StrapiErrorResponse.of(409, "ConflictError", e.getMessage()))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        }
    }

    // ================================================================
    //  Stage Transitions & Content Workflow Status
    // ================================================================

    /**
     * Advances an entry to the next workflow stage.
     */
    @POST
    @Path("/content-entries/{documentId}/advance")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response advanceEntry(@PathParam("documentId") String documentId,
                                  Map<String, Object> body) {
        try {
            String locale = (String) body.getOrDefault("locale", "en");
            Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : null;
            String comment = (String) body.get("comment");

            EntryStageAssignment assignment = stageService.advanceToNextStage(
                documentId, locale, userId, comment);
            return Response.ok(new StrapiSingleResponse<>(Map.of(
                "documentId", documentId,
                "newStageId", assignment.stageId,
                "newStageName", stageService.getCurrentStageName(documentId, locale)
            ))).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "StateError", e.getMessage()))
                .build();
        }
    }

    /**
     * Moves an entry to a specific stage.
     */
    @POST
    @Path("/content-entries/{documentId}/move-to-stage")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response moveEntryToStage(@PathParam("documentId") String documentId,
                                      Map<String, Object> body) {
        try {
            String locale = (String) body.getOrDefault("locale", "en");
            String contentType = (String) body.get("contentType");
            Long targetStageId = body.get("targetStageId") != null
                ? ((Number) body.get("targetStageId")).longValue() : null;
            Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : null;
            String comment = (String) body.get("comment");

            if (targetStageId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "targetStageId is required"))
                    .build();
            }
            if (contentType == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "contentType is required"))
                    .build();
            }

            EntryStageAssignment assignment = reviewWorkflowService.moveEntryToStage(
                documentId, contentType, locale, targetStageId, userId, comment);
            return Response.ok(new StrapiSingleResponse<>(Map.of(
                "documentId", documentId,
                "stageId", assignment.stageId,
                "stageName", stageService.getCurrentStageName(documentId, locale)
            ))).build();

        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
                .build();
        }
    }

    /**
     * Gets the workflow status for an entry.
     */
    @GET
    @Path("/content-entries/{documentId}/status")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response getEntryWorkflowStatus(@PathParam("documentId") String documentId,
                                            @QueryParam("locale") @DefaultValue("en") String locale) {
        Map<String, Object> status = reviewWorkflowService.getWorkflowStatus(documentId, locale);
        return Response.ok(new StrapiSingleResponse<>(status)).build();
    }

    /**
     * Gets stage history for an entry.
     */
    @GET
    @Path("/content-entries/{documentId}/history")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.read")
    public Response getEntryStageHistory(@PathParam("documentId") String documentId,
                                          @QueryParam("locale") @DefaultValue("en") String locale) {
        List<WorkflowStageHistory> history = workflowService.getStageHistory(documentId, locale);
        return Response.ok(new StrapiCollectionResponse<>(
            history.stream().map(h -> Map.of(
                "id", h.id,
                "fromStageId", h.fromStageId,
                "fromStageName", h.fromStageName,
                "toStageId", h.toStageId,
                "toStageName", h.toStageName,
                "transitionType", h.transitionType,
                "userId", h.userId,
                "comment", h.comment,
                "createdAt", h.createdAt != null ? h.createdAt.toString() : null
            )).toList()
        )).build();
    }

    /**
     * Gets counts of entries per stage for dashboard.
     */
    @GET
    @Path("/stage-counts")
    @PermissionCheck("admin::dashboard.read")
    public Response getStageCounts() {
        List<CmsWorkflow> workflows = workflowService.listActiveWorkflows();
        List<Map<String, Object>> workflowCounts = workflows.stream().map(wf -> {
            List<WorkflowStage> stages = workflowService.getStages(wf.id);
            List<Map<String, Object>> stageCounts = stages.stream().map(stage -> {
                long count = EntryStageAssignment.countByStage(stage.id);
                return Map.<String, Object>of(
                    "stageId", stage.id,
                    "stageName", stage.name,
                    "color", stage.color != null ? stage.color : "#4945FF",
                    "entryCount", count
                );
            }).toList();
            return Map.<String, Object>of(
                "workflowId", wf.id,
                "workflowName", wf.name,
                "isDefault", wf.isDefault,
                "stages", stageCounts
            );
        }).toList();

        return Response.ok(new StrapiCollectionResponse<>(workflowCounts)).build();
    }

    // ================================================================
    //  Review Actions (submit/approve/reject/request-changes)
    // ================================================================

    /**
     * Submits an entry for review.
     */
    @POST
    @Path("/review-actions/submit")
    @PermissionCheck(actionTemplate = "admin::content-manager.{contentType}.update")
    public Response submitForReview(Map<String, Object> body) {
        try {
            String documentId = (String) body.get("documentId");
            String contentType = (String) body.get("contentType");
            String locale = (String) body.getOrDefault("locale", "en");
            Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : null;
            Long reviewerId = body.get("reviewerId") != null
                ? ((Number) body.get("reviewerId")).longValue() : null;

            if (documentId == null || contentType == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "documentId and contentType are required"))
                    .build();
            }

            CmsReview review;
            if (reviewerId != null) {
                review = reviewWorkflowService.submitForReview(
                    documentId, contentType, locale, userId, reviewerId);
            } else {
                review = reviewWorkflowService.submitForReview(
                    documentId, contentType, locale, userId);
            }
            return Response.status(Response.Status.CREATED)
                .entity(new StrapiSingleResponse<>(Map.of(
                    "reviewId", review.id,
                    "status", review.status,
                    "documentId", review.documentId,
                    "locale", review.locale,
                    "currentStage", stageService.getCurrentStageName(documentId, locale)
                ))).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "StateError", e.getMessage()))
                .build();
        }
    }

    /**
     * Approves a review.
     */
    @POST
    @Path("/review-actions/approve")
    @PermissionCheck("admin::review-workflows.approve")
    public Response approveReview(Map<String, Object> body) {
        try {
            Long reviewId = body.get("reviewId") != null
                ? ((Number) body.get("reviewId")).longValue() : null;
            Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : null;
            String comment = (String) body.get("comment");

            if (reviewId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "reviewId is required"))
                    .build();
            }

            CmsReview review = reviewWorkflowService.approve(reviewId, userId, comment);
            return Response.ok(new StrapiSingleResponse<>(Map.of(
                "reviewId", review.id,
                "status", review.status,
                "currentStage", reviewWorkflowService.getWorkflowStatus(
                    review.documentId, review.locale).get("currentStageName")
            ))).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "StateError", e.getMessage()))
                .build();
        }
    }

    /**
     * Rejects a review.
     */
    @POST
    @Path("/review-actions/reject")
    @PermissionCheck("admin::review-workflows.reject")
    public Response rejectReview(Map<String, Object> body) {
        try {
            Long reviewId = body.get("reviewId") != null
                ? ((Number) body.get("reviewId")).longValue() : null;
            Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : null;
            String comment = (String) body.get("comment");

            if (reviewId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "reviewId is required"))
                    .build();
            }

            CmsReview review = reviewWorkflowService.reject(reviewId, userId, comment);
            return Response.ok(new StrapiSingleResponse<>(Map.of(
                "reviewId", review.id,
                "status", review.status,
                "currentStage", reviewWorkflowService.getWorkflowStatus(
                    review.documentId, review.locale).get("currentStageName")
            ))).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "StateError", e.getMessage()))
                .build();
        }
    }

    /**
     * Requests changes on a review.
     */
    @POST
    @Path("/review-actions/request-changes")
    @PermissionCheck("admin::review-workflows.reject")
    public Response requestChanges(Map<String, Object> body) {
        try {
            Long reviewId = body.get("reviewId") != null
                ? ((Number) body.get("reviewId")).longValue() : null;
            Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue() : null;
            String comment = (String) body.get("comment");

            if (reviewId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(StrapiErrorResponse.of(400, "ValidationError",
                        "reviewId is required"))
                    .build();
            }

            CmsReview review = reviewWorkflowService.requestChanges(reviewId, userId, comment);
            return Response.ok(new StrapiSingleResponse<>(Map.of(
                "reviewId", review.id,
                "status", review.status,
                "currentStage", reviewWorkflowService.getWorkflowStatus(
                    review.documentId, review.locale).get("currentStageName")
            ))).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(StrapiErrorResponse.of(400, "StateError", e.getMessage()))
                .build();
        }
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Map<String, Object> workflowToMap(CmsWorkflow wf) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", wf.id);
        map.put("name", wf.name);
        map.put("description", wf.description);
        map.put("contentTypes", wf.contentTypes);
        map.put("isDefault", wf.isDefault);
        map.put("active", wf.active);
        map.put("createdAt", wf.createdAt != null ? wf.createdAt.toString() : null);
        map.put("updatedAt", wf.updatedAt != null ? wf.updatedAt.toString() : null);
        return map;
    }

    private Map<String, Object> stageToMap(WorkflowStage stage) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", stage.id);
        map.put("workflowId", stage.workflowId);
        map.put("name", stage.name);
        map.put("stageOrder", stage.stageOrder);
        map.put("color", stage.color);
        map.put("permissions", stage.permissions);
        map.put("isFinal", stage.isFinal);
        map.put("allowsEditing", stage.allowsEditing);
        map.put("createdAt", stage.createdAt != null ? stage.createdAt.toString() : null);
        map.put("updatedAt", stage.updatedAt != null ? stage.updatedAt.toString() : null);
        return map;
    }
}
