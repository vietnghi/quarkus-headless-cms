package com.quarkus.cms.core.schema.component;

import java.time.Instant;

/**
 * Event payload fired by {@link ComponentRegistryService} on component lifecycle transitions.
 *
 * <p>Observers (indexers, webhook dispatchers, cache warmers) can observe this event using CDI
 * event observers:
 *
 * <pre>{@code
 * void onComponentRegistered(@Observes ComponentLifecycleEvent event) { ... }
 * }</pre>
 *
 * @param uid        the component UID that was mutated
 * @param kind       the lifecycle kind: {@code REGISTERED}, {@code UPDATED}, or {@code DELETED}
 * @param changeDescription optional human-readable description of the change
 * @param createdBy  optional principal that performed the mutation
 * @param timestamp  when the event was created
 */
public record ComponentLifecycleEvent(
    String uid,
    String kind,
    String changeDescription,
    String createdBy,
    Instant timestamp) {

  /** Convenience factory for a registration event. */
  public static ComponentLifecycleEvent registered(String uid, String desc, String by) {
    return new ComponentLifecycleEvent(uid, "REGISTERED", desc, by, Instant.now());
  }

  /** Convenience factory for an update event. */
  public static ComponentLifecycleEvent updated(String uid, String desc, String by) {
    return new ComponentLifecycleEvent(uid, "UPDATED", desc, by, Instant.now());
  }

  /** Convenience factory for a deletion event. */
  public static ComponentLifecycleEvent deleted(String uid, String desc, String by) {
    return new ComponentLifecycleEvent(uid, "DELETED", desc, by, Instant.now());
  }
}
