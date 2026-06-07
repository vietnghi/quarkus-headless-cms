package com.quarkus.cms.core.schema.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation that marks a POJO as a reusable component schema definition.
 *
 * <p>Components are groups of fields that can be embedded inside content types or other components
 * via {@link ContentTypeField#component()}. They map onto {@link
 * com.quarkus.cms.core.schema.model.ComponentDefinition}.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @Component(
 *     uid = "shared.seo",
 *     category = "shared",
 *     displayName = "SEO"
 * )
 * public class Seo {
 *     @ContentTypeField(type = FieldType.STRING, maxLength = 60)
 *     String metaTitle;
 *
 *     @ContentTypeField(type = FieldType.TEXT, maxLength = 160)
 *     String metaDescription;
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Component {

  /** Unique identifier for the component, e.g. {@code "shared.seo"} or {@code "default.meta"}. */
  String uid();

  /** Optional grouping category, e.g. {@code "shared"} or {@code "default"}. */
  String category() default "";

  /** Human-readable display name. Falls back to the last segment of {@link #uid()}. */
  String displayName() default "";

  /** Optional description. */
  String description() default "";
}
