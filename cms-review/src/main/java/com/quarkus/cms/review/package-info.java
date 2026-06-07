/**
 * Review Workflows module for content approval processes and stage-based workflows.
 * <p>
 * Provides a complete content review lifecycle matching Strapi v5 enterprise
 * review-workflows:
 * </p>
 * <ul>
 *   <li><strong>Workflow definitions</strong> ({@link com.quarkus.cms.review.CmsWorkflow}) —
 *       configurable pipelines with ordered stages (Draft → Review → Published)</li>
 *   <li><strong>Stage management</strong> ({@link com.quarkus.cms.review.WorkflowStage}) —
 *       role-based permissions per stage, colors, reordering</li>
 *   <li><strong>Content assignment</strong> ({@link com.quarkus.cms.review.EntryStageAssignment}) —
 *       tracks each entry-locale's current position in a workflow</li>
 *   <li><strong>Stage transitions</strong> ({@link com.quarkus.cms.review.WorkflowStageService}) —
 *       advance, move-back, bulk operations with permission enforcement</li>
 *   <li><strong>Review requests</strong> ({@link com.quarkus.cms.review.CmsReview},
 *       {@link com.quarkus.cms.review.ReviewService}) — submit, approve, reject,
 *       request changes, cancel</li>
 *   <li><strong>Integrated workflow+review</strong> ({@link com.quarkus.cms.review.ReviewWorkflowService}) —
 *       orchestrates reviews with stage transitions, audit logging, and webhook events</li>
 *   <li><strong>Audit trail</strong> ({@link com.quarkus.cms.review.WorkflowStageHistory}) —
 *       immutable history of every stage transition</li>
 *   <li><strong>Default workflow</strong> ({@link com.quarkus.cms.review.DefaultWorkflowInitializer}) —
 *       auto-creates Draft → Published on startup for non-configured content types</li>
 * </ul>
 */
package com.quarkus.cms.review;
