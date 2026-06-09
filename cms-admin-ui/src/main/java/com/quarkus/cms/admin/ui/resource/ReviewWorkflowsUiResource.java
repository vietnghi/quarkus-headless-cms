package com.quarkus.cms.admin.ui.resource;

import com.quarkus.cms.review.*;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side rendered admin UI for Review Workflows.
 * <p>
 * Provides pages for:
 * <ul>
 *   <li>Listing, creating, editing, and deleting workflows</li>
 *   <li>Managing stages within each workflow (reorder, color, permissions)</li>
 *   <li>Viewing entry workflow status and stage history</li>
 * </ul>
 * Uses Qute templates + HTMX + Alpine.js.
 */
@Path("/admin/review-workflows")
@Produces(MediaType.TEXT_HTML)
public class ReviewWorkflowsUiResource {

    @Inject
    @Location("admin/review-workflows/list.html")
    Template listTemplate;

    @Inject
    @Location("admin/review-workflows/edit.html")
    Template editTemplate;

    @Inject
    WorkflowService workflowService;

    @Inject
    WorkflowStageService stageService;

    @Inject
    ReviewWorkflowService reviewWorkflowService;

    // ================================================================
    //  Page: List Workflows
    // ================================================================

    @GET
    public TemplateInstance list(
            @QueryParam("created") String created,
            @QueryParam("saved") String saved,
            @QueryParam("deleted") String deleted,
            @QueryParam("error") String error) {
        List<CmsWorkflow> workflows = workflowService.listWorkflows();
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (CmsWorkflow wf : workflows) {
            Map<String, Object> item = workflowToMap(wf);
            long stageCount = WorkflowStage.countByWorkflowId(wf.id);
            long entryCount = EntryStageAssignment.countByWorkflow(wf.id);
            item.put("stageCount", stageCount);
            item.put("entryCount", entryCount);
            enriched.add(item);
        }

        String flashMessage = null;
        if ("true".equals(created)) {
            flashMessage = "Workflow created successfully.";
        } else if ("true".equals(saved)) {
            flashMessage = "Workflow updated successfully.";
        } else if ("true".equals(deleted)) {
            flashMessage = "Workflow deleted successfully.";
        }

        return listTemplate
            .data("title", "Review Workflows")
            .data("workflows", enriched)
            .data("flashMessage", flashMessage)
            .data("error", error);
    }

    // ================================================================
    //  Page: Create Workflow Form
    // ================================================================

    @GET
    @Path("/create")
    public TemplateInstance createForm() {
        return editTemplate
            .data("title", "Create Review Workflow")
            .data("workflow", null)
            .data("stages", List.of())
            .data("isNew", true)
            .data("contentTypesJson", "[]");
    }

    // ================================================================
    //  Page: Edit Workflow Form
    // ================================================================

    @GET
    @Path("/{id}/edit")
    public TemplateInstance editForm(@PathParam("id") Long id) {
        CmsWorkflow workflow = workflowService.getWorkflow(id);
        List<WorkflowStage> stages = workflowService.getStages(id);
        List<Map<String, Object>> stageMaps = stages.stream()
            .map(this::stageToMap)
            .collect(Collectors.toList());

        return editTemplate
            .data("title", "Edit: " + workflow.name)
            .data("workflow", workflowToMap(workflow))
            .data("stages", stageMaps)
            .data("isNew", false);
    }

    // ================================================================
    //  Action: Create Workflow
    // ================================================================

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createWorkflowFromForm(
            @FormParam("name") String name,
            @FormParam("description") String description,
            @FormParam("contentTypesJson") String contentTypesJson,
            @FormParam("stageNames") String stageNames,
            @FormParam("stageColors") String stageColors,
            @FormParam("isDefault") String isDefault) {

        try {
            List<String> contentTypes = parseJsonArray(contentTypesJson);
            List<String> stages = parseNames(stageNames);
            boolean defaultFlag = "on".equals(isDefault);

            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Workflow name is required");
            }
            if (stages.isEmpty()) {
                throw new IllegalArgumentException("At least one stage is required");
            }

            CmsWorkflow workflow = workflowService.createWorkflow(
                name, description, contentTypes, stages, defaultFlag);

            // Apply stage colors if provided
            List<String> colors = parseNames(stageColors);
            List<WorkflowStage> createdStages = WorkflowStage.findByWorkflowId(workflow.id);
            for (int i = 0; i < createdStages.size() && i < colors.size(); i++) {
                String color = colors.get(i);
                if (color != null && !color.isBlank()) {
                    WorkflowStage stage = createdStages.get(i);
                    stage.color = color;
                    stage.persist();
                }
            }

            return Response.seeOther(URI.create("/admin/review-workflows?created=true"))
                .build();

        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create(
                "/admin/review-workflows/create?error=" + e.getMessage()))
                .build();
        }
    }

    // ================================================================
    //  Action: Update Workflow
    // ================================================================

    @POST
    @Path("/{id}/update")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateWorkflowFromForm(
            @PathParam("id") Long id,
            @FormParam("name") String name,
            @FormParam("description") String description,
            @FormParam("contentTypesJson") String contentTypesJson,
            @FormParam("active") String active,
            @FormParam("isDefault") String isDefault) {

        try {
            List<String> contentTypes = parseJsonArray(contentTypesJson);
            Boolean isActive = active != null ? "on".equals(active) : null;

            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Workflow name is required");
            }

            workflowService.updateWorkflow(id, name, description, contentTypes, isActive);

            if ("on".equals(isDefault)) {
                workflowService.setAsDefault(id);
            }

            return Response.seeOther(URI.create("/admin/review-workflows?saved=true"))
                .build();

        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create(
                "/admin/review-workflows/" + id + "/edit?error=" + e.getMessage()))
                .build();
        }
    }

    // ================================================================
    //  Action: Delete Workflow
    // ================================================================

    @POST
    @Path("/{id}/delete")
    public Response deleteWorkflow(@PathParam("id") Long id) {
        try {
            workflowService.deleteWorkflow(id);
            return Response.seeOther(URI.create("/admin/review-workflows?deleted=true"))
                .build();
        } catch (IllegalStateException e) {
            return Response.seeOther(URI.create(
                "/admin/review-workflows?error=" + e.getMessage()))
                .build();
        }
    }

    // ================================================================
    //  HTMX Fragments: Stage Management
    // ================================================================

    /**
     * Removes a stage. HTMX fragment returning the updated stage list.
     */
    @POST
    @Path("/{workflowId}/stages/{stageId}/remove")
    @Produces(MediaType.TEXT_HTML)
    public Response removeStage(
            @PathParam("workflowId") Long workflowId,
            @PathParam("stageId") Long stageId) {
        try {
            workflowService.removeStage(stageId);
            return Response.ok("").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("<div class=\"text-red-600 text-sm p-2\">" + e.getMessage() + "</div>")
                .build();
        }
    }

    // ================================================================
    //  Content Manager Integration: Workflow status component
    // ================================================================

    /**
     * Returns an HTML fragment with the workflow stage badge for an entry.
     * Used by the Content Manager edit page via HTMX.
     */
    @GET
    @Path("/content-entry/{documentId}/stage-badge")
    @Produces(MediaType.TEXT_HTML)
    public Response stageBadge(
            @PathParam("documentId") String documentId,
            @QueryParam("locale") @DefaultValue("en") String locale) {
        try {
            Map<String, Object> status = reviewWorkflowService.getWorkflowStatus(documentId, locale);
            String stageName = (String) status.getOrDefault("currentStageName", "—");
            String color = (String) status.getOrDefault("currentStageColor", "#6B7280");
            boolean assigned = Boolean.TRUE.equals(status.get("assigned"));

            if (!assigned) {
                return Response.ok("<span class=\"text-xs text-gray-400\">No workflow</span>").build();
            }

            String html = "<span class=\"inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium\" " +
                          "style=\"background-color: " + color + "20; color: " + color + "\">" +
                          "<span class=\"w-1.5 h-1.5 rounded-full\" style=\"background-color: " + color + "\"></span>" +
                          stageName +
                          "</span>";
            return Response.ok(html).build();
        } catch (Exception e) {
            return Response.ok("<span class=\"text-xs text-gray-400\">—</span>").build();
        }
    }

    /**
     * Returns an HTML fragment with the stage transition history for an entry.
     */
    @GET
    @Path("/content-entry/{documentId}/history")
    @Produces(MediaType.TEXT_HTML)
    public Response stageHistory(
            @PathParam("documentId") String documentId,
            @QueryParam("locale") @DefaultValue("en") String locale) {
        try {
            List<WorkflowStageHistory> history = workflowService.getStageHistory(documentId, locale);
            StringBuilder html = new StringBuilder();

            if (history.isEmpty()) {
                html.append("<div class=\"text-sm text-gray-400 text-center py-4\">No stage transitions yet.</div>");
            } else {
                html.append("<div class=\"space-y-3\">");
                for (WorkflowStageHistory h : history) {
                    String color = resolveStageColor(h.toStageId);
                    html.append("<div class=\"flex items-start gap-3\">");
                    html.append("  <div class=\"flex flex-col items-center\">");
                    html.append("    <div class=\"w-2.5 h-2.5 rounded-full\" style=\"background-color: ").append(color).append("\"></div>");
                    html.append("    <div class=\"w-0.5 flex-1 bg-gray-200 min-h-[24px]\"></div>");
                    html.append("  </div>");
                    html.append("  <div class=\"flex-1 min-w-0\">");
                    html.append("    <div class=\"flex items-center gap-2\">");
                    html.append("      <span class=\"text-sm font-medium text-gray-900\">").append(escapeHtml(h.toStageName)).append("</span>");
                    html.append("      <span class=\"text-xs px-1.5 py-0.5 rounded bg-gray-100 text-gray-500 capitalize\">").append(escapeHtml(h.transitionType)).append("</span>");
                    html.append("    </div>");
                    if (h.comment != null && !h.comment.isBlank()) {
                        html.append("    <p class=\"text-xs text-gray-500 mt-0.5\">").append(escapeHtml(h.comment)).append("</p>");
                    }
                    html.append("    <p class=\"text-xs text-gray-400 mt-0.5\">").append(h.createdAt != null ? h.createdAt.toString() : "").append("</p>");
                    html.append("  </div>");
                    html.append("</div>");
                }
                html.append("</div>");
            }

            return Response.ok(html.toString()).build();
        } catch (Exception e) {
            return Response.ok("<div class=\"text-sm text-red-500\">Error loading history: " + escapeHtml(e.getMessage()) + "</div>").build();
        }
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Map<String, Object> workflowToMap(CmsWorkflow wf) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", wf.id);
        map.put("name", wf.name);
        map.put("description", wf.description);
        map.put("contentTypes", wf.contentTypes != null ? wf.contentTypes : List.of());
        map.put("isDefault", wf.isDefault != null && wf.isDefault);
        map.put("active", wf.active != null && wf.active);
        map.put("createdAt", wf.createdAt != null ? wf.createdAt.toString() : null);
        map.put("updatedAt", wf.updatedAt != null ? wf.updatedAt.toString() : null);
        return map;
    }

    private Map<String, Object> stageToMap(WorkflowStage stage) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", stage.id);
        map.put("workflowId", stage.workflowId);
        map.put("name", stage.name);
        map.put("stageOrder", stage.stageOrder);
        map.put("color", stage.color != null ? stage.color : "#4945FF");
        map.put("isFinal", stage.isFinal != null && stage.isFinal);
        map.put("allowsEditing", stage.allowsEditing != null && stage.allowsEditing);
        return map;
    }

    private String resolveStageColor(Long stageId) {
        if (stageId == null) return "#6B7280";
        WorkflowStage stage = WorkflowStage.findById(stageId);
        return stage != null && stage.color != null ? stage.color : "#6B7280";
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            // Simple JSON array parse: ["a","b","c"]
            String trimmed = json.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                if (inner.isBlank()) return List.of();
                return Arrays.stream(inner.split(","))
                    .map(s -> s.trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private List<String> parseNames(String names) {
        if (names == null || names.isBlank()) return List.of();
        return Arrays.stream(names.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
