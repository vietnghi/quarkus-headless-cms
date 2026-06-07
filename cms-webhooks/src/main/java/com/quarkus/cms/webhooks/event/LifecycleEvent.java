package com.quarkus.cms.webhooks.event;

import java.time.Instant;
import java.util.Map;

/**
 * Base lifecycle event fired at content operation boundaries.
 *
 * <p>The {@code phase} distinguishes "before" and "after" variants of the same operation. CDI
 * observers can filter on {@code eventType} alone, or use {@code phase} for fine-grained
 * before/after semantics.
 */
public class LifecycleEvent {

  public enum EventType {
    CREATE,
    UPDATE,
    DELETE,
    FIND_ONE,
    FIND_MANY,
    PUBLISH,
    UNPUBLISH
  }

  public enum Phase {
    BEFORE,
    AFTER
  }

  private final EventType eventType;
  private final Phase phase;
  private final String contentType;
  private final String documentId;
  private final String locale;
  private final Map<String, Object> data;
  private final Long userId;
  private final Instant timestamp;

  public LifecycleEvent(
      EventType eventType,
      Phase phase,
      String contentType,
      String documentId,
      String locale,
      Map<String, Object> data,
      Long userId) {
    this.eventType = eventType;
    this.phase = phase;
    this.contentType = contentType;
    this.documentId = documentId;
    this.locale = locale;
    this.data = data;
    this.userId = userId;
    this.timestamp = Instant.now();
  }

  public EventType getEventType() {
    return eventType;
  }

  public Phase getPhase() {
    return phase;
  }

  public String getContentType() {
    return contentType;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getLocale() {
    return locale;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public Long getUserId() {
    return userId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  /** Returns the event key as used by the webhook subscription system (e.g. "entry.create"). */
  public String toEventKey() {
    return "entry." + eventType.name().toLowerCase();
  }

  @Override
  public String toString() {
    return "LifecycleEvent{"
        + "eventType="
        + eventType
        + ", phase="
        + phase
        + ", contentType='"
        + contentType
        + '\''
        + ", documentId='"
        + documentId
        + '\''
        + '}';
  }
}
