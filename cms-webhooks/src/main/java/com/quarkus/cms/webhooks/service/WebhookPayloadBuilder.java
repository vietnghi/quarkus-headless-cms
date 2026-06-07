package com.quarkus.cms.webhooks.service;

import com.quarkus.cms.webhooks.dto.WebhookPayload;
import com.quarkus.cms.webhooks.event.LifecycleEvent;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds Strapi v5-compatible webhook payloads from lifecycle events.
 *
 * <p>The resulting JSON is POSTed to subscriber endpoints. The format mirrors the Strapi v5 webhook
 * body with {@code event}, {@code createdAt}, {@code model}, and {@code entry} fields.
 */
@ApplicationScoped
public class WebhookPayloadBuilder {

  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Builds a webhook payload from a lifecycle event and entry data.
   *
   * @param event the lifecycle event that triggered the dispatch
   * @param entryData the data map of the affected entry (nullable for DELETE)
   * @return a populated WebhookPayload
   */
  public WebhookPayload build(LifecycleEvent event, Map<String, Object> entryData) {
    return new WebhookPayload(
        event.toEventKey(),
        event.getTimestamp().toString(),
        event.getContentType(),
        buildEntryObject(event, entryData),
        Map.of(),
        false);
  }

  /** Builds a webhook payload for a redelivery attempt. */
  public WebhookPayload buildRedelivery(LifecycleEvent event, Map<String, Object> entryData) {
    return new WebhookPayload(
        event.toEventKey(),
        event.getTimestamp().toString(),
        event.getContentType(),
        buildEntryObject(event, entryData),
        Map.of(),
        true);
  }

  /** Serializes the payload to a compact JSON string. */
  public String toJson(WebhookPayload payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize webhook payload", e);
    }
  }

  private Map<String, Object> buildEntryObject(
      LifecycleEvent event, Map<String, Object> entryData) {
    Map<String, Object> entry = new LinkedHashMap<>();
    if (event.getDocumentId() != null) {
      entry.put("id", event.getDocumentId());
    }
    if (event.getLocale() != null) {
      entry.put("locale", event.getLocale());
    }
    if (entryData != null) {
      entry.putAll(entryData);
    }
    return entry;
  }
}
