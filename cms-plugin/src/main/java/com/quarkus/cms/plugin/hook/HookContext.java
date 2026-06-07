package com.quarkus.cms.plugin.hook;

/**
 * Context object passed to plugin hooks during content lifecycle events.
 *
 * <p>Contains the content type UID, the document ID (if applicable), the event type, the data
 * payload, the locale, and the user ID. Hooks can inspect or modify this context to influence
 * content operations.
 */
public class HookContext {

  private final String contentType;
  private final String documentId;
  private final EventType eventType;
  private final Phase phase;
  private Object data;
  private final String locale;
  private final Long userId;
  private boolean cancelled;
  private String cancelReason;

  public HookContext(
      String contentType,
      String documentId,
      EventType eventType,
      Phase phase,
      Object data,
      String locale,
      Long userId) {
    this.contentType = contentType;
    this.documentId = documentId;
    this.eventType = eventType;
    this.phase = phase;
    this.data = data;
    this.locale = locale;
    this.userId = userId;
    this.cancelled = false;
  }

  public String getContentType() {
    return contentType;
  }

  public String getDocumentId() {
    return documentId;
  }

  public EventType getEventType() {
    return eventType;
  }

  public Phase getPhase() {
    return phase;
  }

  public Object getData() {
    return data;
  }

  public String getLocale() {
    return locale;
  }

  public Long getUserId() {
    return userId;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  /** Allows a before-phase hook to modify the data payload. */
  public void setData(Object data) {
    if (phase == Phase.BEFORE) {
      this.data = data;
    }
  }

  /**
   * Cancels the operation from a before-phase hook. After cancellation, the operation will not
   * proceed and an appropriate error will be returned.
   */
  public void cancel(String reason) {
    if (phase == Phase.BEFORE) {
      this.cancelled = true;
      this.cancelReason = reason;
    }
  }

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
}
