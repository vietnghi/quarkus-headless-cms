package com.quarkus.cms.webhooks.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Strapi v5-compatible webhook payload format.
 *
 * <p>This is the JSON body POSTed to webhook endpoints when a lifecycle event fires. The structure
 * mirrors Strapi v5's webhook payload, which has an {@code event} key and an {@code entry} / {@code
 * media} object.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "event": "entry.create",
 *   "createdAt": "2024-01-01T00:00:00Z",
 *   "model": "article",
 *   "entry": { ... },
 *   "media": {}
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookPayload {

  private String event;

  @JsonProperty("created_at")
  private String createdAt;

  private String model;

  private Map<String, Object> entry;

  private Map<String, Object> media;

  @JsonProperty("is_redelivery")
  private Boolean isRedelivery;

  public WebhookPayload() {}

  public WebhookPayload(
      String event,
      String createdAt,
      String model,
      Map<String, Object> entry,
      Map<String, Object> media,
      Boolean isRedelivery) {
    this.event = event;
    this.createdAt = createdAt;
    this.model = model;
    this.entry = entry;
    this.media = media;
    this.isRedelivery = isRedelivery;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(String event) {
    this.event = event;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Map<String, Object> getEntry() {
    return entry;
  }

  public void setEntry(Map<String, Object> entry) {
    this.entry = entry;
  }

  public Map<String, Object> getMedia() {
    return media;
  }

  public void setMedia(Map<String, Object> media) {
    this.media = media;
  }

  public Boolean getIsRedelivery() {
    return isRedelivery;
  }

  public void setIsRedelivery(Boolean isRedelivery) {
    this.isRedelivery = isRedelivery;
  }
}
