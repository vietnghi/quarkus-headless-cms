package com.quarkus.cms.core.entity;

import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier for observing {@link EntityEvent}s at a specific lifecycle phase.
 *
 * <p>Use this qualifier on observer methods to receive only {@code BEFORE} or {@code AFTER}
 * events. Without the qualifier, an observer would receive all {@code EntityEvent} instances
 * and must filter manually.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @ApplicationScoped
 * public class MyPlugin {
 *     void onBeforeCreate(@Observes @EntityHook(phase = EntityEvent.Phase.BEFORE) EntityEvent event) {
 *         // only BEFORE-phase events
 *     }
 * }
 * }</pre>
 *
 * <p>Combine with {@link io.quarkus.arc.All} to receive all phases in a single method:
 * <pre>{@code
 * void onAny(@Observes @All EntityHook qualifier, EntityEvent event) { ... }
 * }</pre>
 */
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityHook {

    /** The lifecycle phase to observe. */
    EntityEvent.Phase phase();
}
