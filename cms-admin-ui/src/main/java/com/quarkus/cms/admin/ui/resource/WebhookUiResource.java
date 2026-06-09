package com.quarkus.cms.admin.ui.resource;

import com.quarkus.cms.webhooks.dto.WebhookConfig;
import com.quarkus.cms.webhooks.entity.CmsWebhook;
import com.quarkus.cms.webhooks.entity.CmsWebhookDelivery;
import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;
import com.quarkus.cms.webhooks.service.WebhookDispatcher;
import com.quarkus.cms.webhooks.service.WebhookService;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.*;

/**
 * Server-side rendered webhook management pages (HTMX-powered).
 *
 * <p>Provides CRUD operations on webhook registrations, delivery log viewing, and
 * test dispatch — mirroring the JSON API at {@code /admin/webhooks} but as HTML
 * pages rendered via Qute templates.
 */
@Path("/admin/webhooks-ui")
@Produces(MediaType.TEXT_HTML)
public class WebhookUiResource {

    @Inject
    @Location("admin/webhooks/list.html")
    Template list;

    @Inject
    @Location("admin/webhooks/form.html")
    Template form;

    @Inject
    @Location("admin/webhooks/deliveries.html")
    Template deliveries;

    @Inject
    WebhookService webhookService;

    @Inject
    WebhookDispatcher webhookDispatcher;

    private static final List<String> AVAILABLE_EVENTS = List.of(
            "entry.create", "entry.update", "entry.delete",
            "entry.publish", "entry.unpublish");

    // ---- List ---- //

    @GET
    public TemplateInstance listWebhooks() {
        List<CmsWebhook> webhooks = webhookService.listAll();
        return list
                .data("title", "Webhooks")
                .data("webhooks", webhooks);
    }

    // ---- Create Form ---- //

    @GET
    @Path("/create")
    public TemplateInstance createForm() {
        return form
                .data("title", "Create Webhook")
                .data("webhook", null)
                .data("isNew", true)
                .data("availableEvents", AVAILABLE_EVENTS)
                .data("selectedEvents", List.of())
                .data("formData", new HashMap<String, String>());
    }

    // ---- Edit Form ---- //

    @GET
    @Path("/{id}/edit")
    public TemplateInstance editForm(@PathParam("id") Long id) {
        CmsWebhook webhook = webhookService.findById(id);
        if (webhook == null) {
            throw new NotFoundException("Webhook not found: " + id);
        }

        List<String> selectedEvents = webhook.events != null ? webhook.events : List.of();

        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("name", webhook.name != null ? webhook.name : "");
        formData.put("url", webhook.url != null ? webhook.url : "");
        formData.put("secret", webhook.secret != null ? webhook.secret : "");
        formData.put("isEnabled", String.valueOf(webhook.isEnabled));
        formData.put("maxRetries", String.valueOf(webhook.maxRetries));
        formData.put("timeoutMs", String.valueOf(webhook.timeoutMs));

        // Flatten headers into comma-separated for the text input
        StringBuilder headersStr = new StringBuilder();
        if (webhook.headers != null) {
            for (Map.Entry<String, String> entry : webhook.headers.entrySet()) {
                if (headersStr.length() > 0) {
                    headersStr.append(", ");
                }
                headersStr.append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }
        formData.put("headers", headersStr.toString());

        return form
                .data("title", "Edit Webhook — " + webhook.name)
                .data("webhook", webhook)
                .data("isNew", false)
                .data("availableEvents", AVAILABLE_EVENTS)
                .data("selectedEvents", selectedEvents)
                .data("formData", formData);
    }

    // ---- Create (POST) ---- //

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createWebhook(MultivaluedMap<String, String> formParams) {
        try {
            String name = firstOrNull(formParams, "name");
            String url = firstOrNull(formParams, "url");
            if (name == null || name.isBlank() || url == null || url.isBlank()) {
                return renderFormWithError(
                        null, formParams,
                        "Name and URL are required");
            }

            List<String> events = formParams.get("events");
            if (events == null) {
                events = List.of();
            }

            String headersStr = firstOrNull(formParams, "headers");
            Map<String, String> headers = parseHeaders(headersStr);

            String secret = firstOrNull(formParams, "secret");
            boolean enabled = "on".equals(firstOrNull(formParams, "isEnabled"));

            int maxRetries = parseIntOrDefault(firstOrNull(formParams, "maxRetries"), 3);
            int timeoutMs = parseIntOrDefault(firstOrNull(formParams, "timeoutMs"), 10000);

            WebhookConfig config = new WebhookConfig();
            config.setName(name);
            config.setUrl(url);
            config.setEvents(events);
            config.setHeaders(headers);
            config.setSecret(secret);
            config.setEnabled(enabled);
            config.setMaxRetries(maxRetries);
            config.setTimeoutMs(timeoutMs);

            webhookService.create(config, null);

            return Response.seeOther(URI.create("/admin/webhooks-ui"))
                    .build();
        } catch (Exception e) {
            return renderFormWithError(null, formParams, e.getMessage());
        }
    }

    // ---- Update (POST) ---- //

    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateWebhook(
            @PathParam("id") Long id,
            MultivaluedMap<String, String> formParams) {
        try {
            CmsWebhook webhook = webhookService.findById(id);
            if (webhook == null) {
                throw new NotFoundException("Webhook not found: " + id);
            }

            String name = firstOrNull(formParams, "name");
            String url = firstOrNull(formParams, "url");
            if (name == null || name.isBlank() || url == null || url.isBlank()) {
                return renderFormWithError(
                        id, formParams,
                        "Name and URL are required");
            }

            List<String> events = formParams.get("events");
            if (events == null) {
                events = List.of();
            }

            String headersStr = firstOrNull(formParams, "headers");
            Map<String, String> headers = parseHeaders(headersStr);

            String secret = firstOrNull(formParams, "secret");
            boolean enabled = "on".equals(firstOrNull(formParams, "isEnabled"));

            int maxRetries = parseIntOrDefault(firstOrNull(formParams, "maxRetries"), 3);
            int timeoutMs = parseIntOrDefault(firstOrNull(formParams, "timeoutMs"), 10000);

            WebhookConfig config = new WebhookConfig();
            config.setName(name);
            config.setUrl(url);
            config.setEvents(events);
            config.setHeaders(headers);
            config.setSecret(secret);
            config.setEnabled(enabled);
            config.setMaxRetries(maxRetries);
            config.setTimeoutMs(timeoutMs);

            webhookService.update(id, config);

            return Response.seeOther(URI.create("/admin/webhooks-ui"))
                    .build();
        } catch (Exception e) {
            return renderFormWithError(id, formParams, e.getMessage());
        }
    }

    // ---- Delete ---- //

    @POST
    @Path("/{id}/delete")
    public Response deleteWebhook(@PathParam("id") Long id) {
        try {
            webhookService.delete(id);
            return Response.seeOther(URI.create("/admin/webhooks-ui"))
                    .build();
        } catch (Exception e) {
            return Response.seeOther(URI.create(
                    "/admin/webhooks-ui?error=" + e.getMessage()))
                    .build();
        }
    }

    // ---- Toggle Enable/Disable ---- //

    @POST
    @Path("/{id}/toggle")
    public Response toggleWebhook(@PathParam("id") Long id) {
        CmsWebhook webhook = webhookService.findById(id);
        if (webhook == null) {
            throw new NotFoundException("Webhook not found: " + id);
        }

        if (webhook.isEnabled) {
            webhookService.disable(id);
        } else {
            webhookService.enable(id);
        }

        return Response.seeOther(URI.create("/admin/webhooks-ui"))
                .build();
    }

    // ---- Delivery Log ---- //

    @GET
    @Path("/{id}/deliveries")
    public TemplateInstance viewDeliveries(
            @PathParam("id") Long id,
            @QueryParam("testSent") String testSent,
            @QueryParam("testResult") String testResult) {
        CmsWebhook webhook = webhookService.findById(id);
        if (webhook == null) {
            throw new NotFoundException("Webhook not found: " + id);
        }

        List<CmsWebhookDelivery> deliveryLog = webhookService.getDeliveryLog(id);
        CmsWebhookDelivery latestDelivery = webhookService.getLatestDelivery(id);

        return deliveries
                .data("title", "Deliveries — " + webhook.name)
                .data("webhook", webhook)
                .data("deliveries", deliveryLog)
                .data("latestDelivery", latestDelivery)
                .data("totalDeliveries", deliveryLog.size())
                .data("testSent", testSent != null)
                .data("testResult", testResult);
    }

    // ---- Test Dispatch ---- //

    @POST
    @Path("/{id}/test")
    public Response testDispatch(@PathParam("id") Long id) {
        CmsWebhook webhook = webhookService.findById(id);
        if (webhook == null) {
            throw new NotFoundException("Webhook not found: " + id);
        }

        try {
            LifecycleEvent testEvent = new LifecycleEvent(
                    EventType.CREATE,
                    Phase.AFTER,
                    "test",
                    "test-doc-id",
                    "en",
                    Map.of("test", true, "message", "This is a test webhook dispatch"),
                    null);

            webhookDispatcher.dispatch(webhook, testEvent, testEvent.getData());

            return Response.seeOther(URI.create(
                    "/admin/webhooks-ui/" + id + "/deliveries?testSent=true"))
                    .build();
        } catch (Exception e) {
            return Response.seeOther(URI.create(
                    "/admin/webhooks-ui/" + id + "/deliveries?testResult=" + e.getMessage()))
                    .build();
        }
    }

    // ---- Helpers ---- //

    private Response renderFormWithError(
            Long webhookId,
            MultivaluedMap<String, String> formParams,
            String errorMessage) {

        boolean isNew = webhookId == null;
        CmsWebhook wh = webhookId != null ? webhookService.findById(webhookId) : null;

        List<String> selectedEvents = formParams.get("events");
        if (selectedEvents == null) {
            selectedEvents = List.of();
        }

        Map<String, String> fd = extractFormData(formParams);

        TemplateInstance instance = form
                .data("title", isNew ? "Create Webhook" : "Edit Webhook — " + (wh != null ? wh.name : ""))
                .data("webhook", wh)
                .data("isNew", isNew)
                .data("availableEvents", AVAILABLE_EVENTS)
                .data("selectedEvents", selectedEvents)
                .data("formData", fd)
                .data("errorMessage", errorMessage);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(instance)
                .build();
    }

    private static String firstOrNull(MultivaluedMap<String, String> params, String key) {
        List<String> values = params.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private static int parseIntOrDefault(String val, int defaultVal) {
        if (val == null || val.isBlank()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * Parses "Key: Value, Key2: Value2" format into a map.
     */
    private static Map<String, String> parseHeaders(String headersStr) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headersStr == null || headersStr.isBlank()) {
            return result;
        }
        for (String part : headersStr.split(",")) {
            part = part.trim();
            int colonIdx = part.indexOf(':');
            if (colonIdx > 0) {
                String key = part.substring(0, colonIdx).trim();
                String value = part.substring(colonIdx + 1).trim();
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    private static Map<String, String> extractFormData(MultivaluedMap<String, String> formParams) {
        Map<String, String> data = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : formParams.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            // Events are handled separately
            if (!"events".equals(key)) {
                data.put(key, values.get(0));
            }
        }
        return data;
    }
}
