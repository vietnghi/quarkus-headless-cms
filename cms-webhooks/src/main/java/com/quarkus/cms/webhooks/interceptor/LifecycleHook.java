package com.quarkus.cms.webhooks.interceptor;

import com.quarkus.cms.webhooks.event.LifecycleEvent;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interceptor binding annotation for lifecycle hooks.
 *
 * <p>Apply to a CDI bean method to have it automatically invoked before or after content
 * operations. Use with {@link LifecycleEvent.Phase} to control timing.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @LifecycleHook(eventType = LifecycleEvent.EventType.CREATE, phase = LifecycleEvent.Phase.BEFORE)
 * public void beforeCreate(@Observes LifecycleEvent event) {
 *     // validate or transform content before creation
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LifecycleHook {

  @Nonbinding
  LifecycleEvent.EventType eventType();

  @Nonbinding
  LifecycleEvent.Phase phase() default LifecycleEvent.Phase.AFTER;
}
