package com.quarkus.cms.core.schema.annotation;

import com.quarkus.cms.core.schema.model.RelationType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level annotation that declares a relation from the owning content type to a target content
 * type.
 *
 * <p>This is the annotation-based equivalent of {@link
 * com.quarkus.cms.core.schema.model.RelationDefinition}. At annotation-processing time it is
 * converted into a {@link com.quarkus.cms.core.schema.model.RelationDefinition} and added to the
 * parent content type's relation list.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @ContentType(uid = "api::article.article", ...)
 * public class Article {
 *     @ContentTypeRelation(
 *         type = RelationType.MANY_TO_ONE,
 *         target = "api::author.author",
 *         targetAttribute = "articles"
 *     )
 *     String author;
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ContentTypeRelation {

  /** The cardinality of the relation (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY). */
  RelationType type();

  /** Target content-type UID, e.g. {@code "api::author.author"}. */
  String target();

  /**
   * The inverse field name on the target for bidirectional relations. When empty, the relation is
   * unidirectional.
   */
  String targetAttribute() default "";

  /**
   * Explicit join-table name override (only meaningful for many-to-many relations). When empty,
   * the system generates a default name.
   */
  String joinTable() default "";

  /** Whether this side owns the foreign key in a one-to-one relation. */
  boolean dominant() default false;

  /**
   * Discriminator column name for polymorphic (morph) relations. Only meaningful when {@link
   * #type()} is {@link RelationType#MORPH_TO_ONE} or {@link RelationType#MORPH_TO_MANY}.
   */
  String morphColumnType() default "";
}
