package com.quarkus.cms.core.schema.annotation;

import com.quarkus.cms.core.schema.model.ContentTypeKind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation that marks a POJO or interface as a content-type schema definition.
 *
 * <p>Fields annotated with {@link ContentTypeField @ContentTypeField} and/or {@link
 * ContentTypeRelation @ContentTypeRelation} on the same class define the content type's schema. At
 * build time or application startup, annotation scanning converts this declaration into a {@link
 * com.quarkus.cms.core.schema.model.ContentTypeDefinition} and registers it with the schema
 * service.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @ContentType(
 *     uid = "api::article.article",
 *     kind = ContentTypeKind.COLLECTION_TYPE,
 *     singularName = "article",
 *     pluralName = "articles",
 *     displayName = "Article"
 * )
 * public class Article {
 *     @ContentTypeField(type = FieldType.STRING, required = true, maxLength = 200)
 *     String title;
 *
 *     @ContentTypeField(type = FieldType.RICHTEXT)
 *     String body;
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ContentType {

  /** Unique identifier for the content type, e.g. {@code "api::article.article"}. */
  String uid();

  /** Whether this is a collection type or a single type. Defaults to COLLECTION_TYPE. */
  ContentTypeKind kind() default ContentTypeKind.COLLECTION_TYPE;

  /** Singular display name, e.g. {@code "article"}. Falls back to last segment of {@link #uid()}. */
  String singularName() default "";

  /** Plural display name, e.g. {@code "articles"}. Falls back to singular + "s". */
  String pluralName() default "";

  /** Human-readable display name, e.g. {@code "Article"}. Falls back to singularName. */
  String displayName() default "";

  /** Optional description of the content type. */
  String description() default "";

  /** Whether draft & publish workflow is enabled. Defaults to true. */
  boolean draftAndPublish() default true;

  /** Whether i18n / localization is enabled. Defaults to false. */
  boolean localized() default false;
}
