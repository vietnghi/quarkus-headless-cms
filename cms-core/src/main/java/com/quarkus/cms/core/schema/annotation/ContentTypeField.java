package com.quarkus.cms.core.schema.annotation;

import com.quarkus.cms.core.schema.model.FieldType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level annotation that defines a field within a {@link ContentType @ContentType} or {@link
 * Component @Component}.
 *
 * <p>Maps onto {@link com.quarkus.cms.core.schema.model.FieldDefinition}. Required properties are
 * validated at annotation-processing time.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @ContentType(uid = "api::article.article", ...)
 * public class Article {
 *     @ContentTypeField(type = FieldType.STRING, required = true, maxLength = 200)
 *     String title;
 *
 *     @ContentTypeField(type = FieldType.RICHTEXT)
 *     String body;
 *
 *     @ContentTypeField(
 *         type = FieldType.MEDIA,
 *         allowedTypes = {"image/jpeg", "image/png"}
 *     )
 *     String coverImage;
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ContentTypeField {

  /** The field type (STRING, INTEGER, RICHTEXT, MEDIA, etc.). Required. */
  FieldType type();

  /** Whether the field is required. */
  boolean required() default false;

  /** Whether the field value must be unique across entries. */
  boolean unique() default false;

  /** Default value as a string. */
  String defaultValue() default "";

  /** Minimum length for string/text fields. Ignored when &le; 0. */
  int minLength() default -1;

  /** Maximum length for string/text fields. Ignored when &le; 0. */
  int maxLength() default -1;

  /** Minimum value for numeric fields. Ignored when equal to {@link Integer#MIN_VALUE}. */
  int min() default Integer.MIN_VALUE;

  /** Maximum value for numeric fields. Ignored when equal to {@link Integer#MIN_VALUE}. */
  int max() default Integer.MIN_VALUE;

  /** Validation regex for string fields. */
  String regex() default "";

  /** Allowed values for ENUMERATION fields. */
  String[] enumValues() default {};

  /** Whether this field is private (never returned via public APIs). */
  boolean pvt() default false;

  /** Whether this field supports per-locale translations. */
  boolean localized() default false;

  /**
   * For RELATION-type fields: the target content-type UID. For COMPONENT-type fields: the component
   * UID.
   */
  String target() default "";

  /** For COMPONENT fields: the referenced component UID. */
  String component() default "";

  /** For DYNAMIC_ZONE fields: the list of allowed component UIDs. */
  String[] allowedComponents() default {};

  /** Whether component fields can hold multiple values. */
  boolean repeatable() default false;

  /** Minimum number of components (for DYNAMIC_ZONE). */
  int minComponents() default 0;

  /** Maximum number of components (for DYNAMIC_ZONE, -1 = unlimited). */
  int maxComponents() default -1;

  /**
   * Optional list of allowed MIME types for MEDIA fields. When empty, all configured types are
   * allowed.
   */
  String[] allowedTypes() default {};
}
