package com.quarkus.cms.webhooks.service;

import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Central event bus for the CMS lifecycle hook system.
 *
 * <p>Service methods on {@link com.quarkus.cms.runtime.CmsContentService} (or any other component)
 * call the {@code fire*} methods at the appropriate lifecycle points. This class fires CDI events
 * via {@code jakarta.enterprise.event.Event} so that any {@code @Observes LifecycleEvent} observer
 * picks them up — including the webhook dispatcher.
 *
 * <p>Additionally, each event is published onto the Vert.x event bus under the address {@code
 * cms.lifecycle.<eventType>.<phase>} so that non-CDI Vert.x verticles can also react.
 *
 * <h3>Usage from CmsContentService</h3>
 *
 * <pre>{@code
 * @Inject LifecycleEventBus eventBus;
 *
 * // Before creating
 * eventBus.fireBeforeCreate(contentType, data, locale, userId);
 * // ... perform create ...
 * eventBus.fireAfterCreate(contentType, documentId, locale, data, userId);
 * }</pre>
 */
@ApplicationScoped
public class LifecycleEventBus {

  private final Event<LifecycleEvent> cdiEvent;
  private final io.vertx.mutiny.core.eventbus.EventBus vertxEventBus;

  @Inject
  public LifecycleEventBus(
      Event<LifecycleEvent> cdiEvent, io.vertx.mutiny.core.eventbus.EventBus vertxEventBus) {
    this.cdiEvent = cdiEvent;
    this.vertxEventBus = vertxEventBus;
  }

  // ---- Before hooks ----

  /** Fires a {@link EventType#CREATE CREATE} before-event before a new entry is persisted. */
  public void fireBeforeCreate(
      String contentType, Map<String, Object> data, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.CREATE, Phase.BEFORE, contentType, null, locale, data, userId));
  }

  /** Fires a {@link EventType#UPDATE UPDATE} before-event before an entry is modified. */
  public void fireBeforeUpdate(
      String contentType, String documentId, Map<String, Object> data, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.UPDATE, Phase.BEFORE, contentType, documentId, locale, data, userId));
  }

  /** Fires a {@link EventType#DELETE DELETE} before-event before an entry is deleted. */
  public void fireBeforeDelete(String contentType, String documentId, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.DELETE, Phase.BEFORE, contentType, documentId, locale, null, userId));
  }

  /** Fires a {@link EventType#FIND_ONE FIND_ONE} before-event before a single-entry query. */
  public void fireBeforeFindOne(String contentType, String documentId, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.FIND_ONE, Phase.BEFORE, contentType, documentId, locale, null, userId));
  }

  /** Fires a {@link EventType#FIND_MANY FIND_MANY} before-event before a collection query. */
  public void fireBeforeFindMany(String contentType) {
    fire(
        new LifecycleEvent(EventType.FIND_MANY, Phase.BEFORE, contentType, null, null, null, null));
  }

  /** Fires a {@link EventType#PUBLISH PUBLISH} before-event before an entry is published. */
  public void fireBeforePublish(String contentType, String documentId, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.PUBLISH, Phase.BEFORE, contentType, documentId, locale, null, userId));
  }

  /** Fires an {@link EventType#UNPUBLISH UNPUBLISH} before-event before an entry is unpublished. */
  public void fireBeforeUnpublish(
      String contentType, String documentId, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.UNPUBLISH, Phase.BEFORE, contentType, documentId, locale, null, userId));
  }

  // ---- After hooks ----

  /** Fires an {@link EventType#CREATE CREATE} after-event after a new entry was persisted. */
  public void fireAfterCreate(
      String contentType, String documentId, Map<String, Object> data, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.CREATE, Phase.AFTER, contentType, documentId, locale, data, userId));
  }

  /** Fires an {@link EventType#UPDATE UPDATE} after-event after an entry was modified. */
  public void fireAfterUpdate(
      String contentType, String documentId, Map<String, Object> data, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.UPDATE, Phase.AFTER, contentType, documentId, locale, data, userId));
  }

  /** Fires a {@link EventType#DELETE DELETE} after-event after an entry was deleted. */
  public void fireAfterDelete(String contentType, String documentId, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.DELETE, Phase.AFTER, contentType, documentId, locale, null, userId));
  }

  /**
   * Fires a {@link EventType#FIND_ONE FIND_ONE} after-event after a single-entry query completed.
   */
  public void fireAfterFindOne(
      String contentType, String documentId, Map<String, Object> data, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.FIND_ONE, Phase.AFTER, contentType, documentId, locale, data, userId));
  }

  /**
   * Fires a {@link EventType#FIND_MANY FIND_MANY} after-event after a collection query completed.
   */
  public void fireAfterFindMany(String contentType, int resultCount) {
    fire(
        new LifecycleEvent(
            EventType.FIND_MANY,
            Phase.AFTER,
            contentType,
            null,
            null,
            Map.of("count", resultCount),
            null));
  }

  /** Fires a {@link EventType#PUBLISH PUBLISH} after-event after an entry was published. */
  public void fireAfterPublish(
      String contentType, String documentId, Map<String, Object> data, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.PUBLISH, Phase.AFTER, contentType, documentId, locale, data, userId));
  }

  /** Fires an {@link EventType#UNPUBLISH UNPUBLISH} after-event after an entry was unpublished. */
  public void fireAfterUnpublish(
      String contentType, String documentId, String locale, Long userId) {
    fire(
        new LifecycleEvent(
            EventType.UNPUBLISH, Phase.AFTER, contentType, documentId, locale, null, userId));
  }

  // ---- Internals ----

  private void fire(LifecycleEvent event) {
    // CDI synchronous event — consumers run in the same thread.
    // For async consumers, use @ObservesAsync.
    cdiEvent.fire(event);

    // Also push to the Vert.x event bus for non-CDI consumers.
    String address =
        "cms.lifecycle."
            + event.getEventType().name().toLowerCase()
            + "."
            + event.getPhase().name().toLowerCase();
    vertxEventBus.publish(address, event);

    Log.debugf(
        "Fired lifecycle event: %s (%s/%s) for contentType=%s documentId=%s",
        event.getEventType(),
        event.getPhase(),
        address,
        event.getContentType(),
        event.getDocumentId());
  }
}
