package com.quarkus.cms.webhooks.service;

import com.quarkus.cms.webhooks.entity.CmsWebhook;
import com.quarkus.cms.webhooks.event.LifecycleEvent;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Observes CDI lifecycle events and dispatches matching webhooks.
 *
 * <p>This is the bridge between the lifecycle hook system and the webhook dispatch system. When a
 * {@link LifecycleEvent} is fired via CDI (by {@link LifecycleEventBus}), this consumer picks it up
 * and routes it to every webhook that is subscribed to the corresponding event type.
 *
 * <p>The dispatch itself is non-blocking — {@link WebhookDispatcher} sends HTTP requests
 * asynchronously via the Vert.x event loop.
 */
@ApplicationScoped
public class WebhookEventConsumer {

  private final WebhookService webhookService;
  private final WebhookDispatcher dispatcher;

  @Inject
  public WebhookEventConsumer(WebhookService webhookService, WebhookDispatcher dispatcher) {
    this.webhookService = webhookService;
    this.dispatcher = dispatcher;
  }

  /**
   * Observes lifecycle events and dispatches webhooks that are subscribed to the matching event
   * type. Only "after" phase events trigger webhooks — "before" events are meant for
   * validation/transformation hooks.
   *
   * @param event the lifecycle event fired via CDI
   */
  public void onLifecycleEvent(@Observes LifecycleEvent event) {
    // Only dispatch on "after" phase — before hooks are for validation, not notification.
    if (event.getPhase() != LifecycleEvent.Phase.AFTER) {
      return;
    }

    String eventKey = event.toEventKey();
    List<CmsWebhook> subscribers = webhookService.listByEvent(eventKey);

    if (subscribers.isEmpty()) {
      Log.debugf("No webhook subscribers for event: %s", eventKey);
      return;
    }

    Log.debugf("Dispatching %s to %d webhook(s)", eventKey, subscribers.size());

    for (CmsWebhook webhook : subscribers) {
      try {
        dispatcher.dispatch(webhook, event, event.getData());
      } catch (Exception e) {
        Log.errorf(
            e,
            "Failed to dispatch webhook %s for event %s: %s",
            webhook.name,
            eventKey,
            e.getMessage());
        // Don't let one failing webhook block others
      }
    }
  }
}
