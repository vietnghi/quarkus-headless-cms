package com.quarkus.cms.core.schema.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level annotation that declares a dynamic zone inside a content type.
 *
 * <p>A dynamic zone is an area where content editors can dynamically add blocks chosen from a set of
 * allowed component UIDs, and arrange them in any order. This maps onto {@link
 * com.quarkus.cms.core.schema.model.DynamicZoneDefinition}.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @ContentType(uid = "api::page.page", ...)
 * public class Page {
 *     @DynamicZone(
 *         allowedComponents = {"shared.quote", "shared.slider", "shared.text-block"},
 *         min = 0, max = 20
 *     )
 *     String contentBlocks;
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DynamicZone {

  /** The name of the dynamic zone field. Defaults to the annotated field name. */
  String name() default "";

  /** List of component UIDs that are allowed in this dynamic zone. */
  String[] allowedComponents() default {};

  /** Minimum number of components (0 = optional). */
  int min() default 0;

  /** Maximum number of components (-1 = unlimited). */
  int max() default -1;

  /** Whether the dynamic zone is required. */
  boolean required() default false;
}
