/**
 * Webhooks and Lifecycle Hooks module for the Quarkus Headless CMS.
 *
 * <h2>Lifecycle Hooks</h2>
 *
 * <p>CDI event-based lifecycle hook system similar to Strapi's lifecycle callbacks. Fires {@link
 * com.quarkus.cms.webhooks.event.LifecycleEvent} at twelve points (before/after for CREATE, UPDATE,
 * DELETE, FIND_ONE, FIND_MANY, PUBLISH, UNPUBLISH). Custom observers use {@code @Observes
 * LifecycleEvent} to react.
 *
 * <h2>Webhooks</h2>
 *
 * <p>HTTP callback dispatching on content lifecycle events with:
 *
 * <ul>
 *   <li>CRUD management of webhook registrations
 *   <li>Strapi v5-compatible payload format
 *   <li>Non-blocking delivery via Vert.x HTTP client
 *   <li>Exponential backoff retry with configurable max attempts
 *   <li>Per-delivery logging (status, HTTP response, timing)
 *   <li>HMAC-SHA256 payload signatures for security
 * </ul>
 *
 * @see com.quarkus.cms.webhooks.service.LifecycleEventBus
 * @see com.quarkus.cms.webhooks.resource.AdminWebhooksResource
 */
package com.quarkus.cms.webhooks;
