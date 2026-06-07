package com.quarkus.cms.webhooks.resource;

import com.quarkus.cms.webhooks.dto.WebhookConfig;
import com.quarkus.cms.webhooks.entity.CmsWebhook;
import com.quarkus.cms.webhooks.entity.CmsWebhookDelivery;
import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;
import com.quarkus.cms.webhooks.service.WebhookDispatcher;
import com.quarkus.cms.webhooks.service.WebhookService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin REST resource for webhook configuration management.
 *
 * <p>Provides CRUD operations on webhook registrations and access to delivery logs for monitoring
 * webhook health.
 *
 * <p>Base path: {@code /admin/webhooks}
 */
@Path("/admin/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminWebhooksResource {

  private final WebhookService webhookService;
  private final WebhookDispatcher webhookDispatcher;

  @Inject
  public AdminWebhooksResource(WebhookService webhookService, WebhookDispatcher webhookDispatcher) {
    this.webhookService = webhookService;
    this.webhookDispatcher = webhookDispatcher;
  }

  /** Lists all webhook registrations. */
  @GET
  public Response listAll() {
    List<CmsWebhook> webhooks = webhookService.listAll();
    return Response.ok(wrap("data", webhooks)).build();
  }

  /** Gets a single webhook by ID. */
  @GET
  @Path("/{id}")
  public Response getById(@PathParam("id") Long id) {
    CmsWebhook webhook = webhookService.findById(id);
    if (webhook == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(wrapError("Webhook not found", "id " + id + " does not exist"))
          .build();
    }
    return Response.ok(wrap("data", webhook)).build();
  }

  /** Registers a new webhook. */
  @POST
  public Response create(@Valid WebhookConfig config) {
    CmsWebhook webhook = webhookService.create(config, null);
    return Response.status(Response.Status.CREATED).entity(wrap("data", webhook)).build();
  }

  /** Updates an existing webhook. */
  @PUT
  @Path("/{id}")
  public Response update(@PathParam("id") Long id, @Valid WebhookConfig config) {
    try {
      CmsWebhook webhook = webhookService.update(id, config);
      return Response.ok(wrap("data", webhook)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(wrapError("Webhook not found", e.getMessage()))
          .build();
    }
  }

  /** Enables a webhook. */
  @POST
  @Path("/{id}/enable")
  public Response enable(@PathParam("id") Long id) {
    try {
      webhookService.enable(id);
      return Response.ok(wrap("data", Map.of("id", id, "enabled", true))).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(wrapError("Webhook not found", e.getMessage()))
          .build();
    }
  }

  /** Disables a webhook. */
  @POST
  @Path("/{id}/disable")
  public Response disable(@PathParam("id") Long id) {
    try {
      webhookService.disable(id);
      return Response.ok(wrap("data", Map.of("id", id, "enabled", false))).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(wrapError("Webhook not found", e.getMessage()))
          .build();
    }
  }

  /** Deletes a webhook permanently. */
  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id) {
    webhookService.delete(id);
    return Response.ok(wrap("data", Map.of("id", id, "deleted", true))).build();
  }

  /** Returns the delivery log for a webhook. */
  @GET
  @Path("/{id}/deliveries")
  public Response getDeliveries(
      @PathParam("id") Long id, @QueryParam("sinceHours") @DefaultValue("24") int sinceHours) {
    CmsWebhook webhook = webhookService.findById(id);
    if (webhook == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(wrapError("Webhook not found", "id " + id + " does not exist"))
          .build();
    }

    List<CmsWebhookDelivery> deliveries = webhookService.getDeliveryLog(id);
    Instant cutoff = Instant.now().minus(sinceHours, ChronoUnit.HOURS);

    List<CmsWebhookDelivery> recent =
        deliveries.stream()
            .filter(d -> d.createdAt != null && d.createdAt.isAfter(cutoff))
            .toList();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("data", recent);
    result.put("total", recent.size());
    return Response.ok(result).build();
  }

  /** Triggers a test dispatch to a webhook endpoint. */
  @POST
  @Path("/{id}/test")
  public Response testDispatch(@PathParam("id") Long id) {
    CmsWebhook webhook = webhookService.findById(id);
    if (webhook == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(wrapError("Webhook not found", "id " + id + " does not exist"))
          .build();
    }

    LifecycleEvent testEvent =
        new LifecycleEvent(
            EventType.CREATE,
            Phase.AFTER,
            "test",
            "test-doc-id",
            "en",
            Map.of("test", true, "message", "This is a test webhook dispatch"),
            null);

    try {
      webhookDispatcher.dispatch(webhook, testEvent, testEvent.getData());
      return Response.ok(
              wrap(
                  "data",
                  Map.of(
                      "id",
                      id,
                      "test_dispatched",
                      true,
                      "message",
                      "Test webhook dispatched to " + webhook.url)))
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(wrapError("Test dispatch failed", e.getMessage()))
          .build();
    }
  }

  // ---- Helpers ----

  private static Map<String, Object> wrap(String key, Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  private static Map<String, Object> wrapError(String message, String details) {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("message", message);
    error.put("details", details);
    return wrap("error", error);
  }
}
