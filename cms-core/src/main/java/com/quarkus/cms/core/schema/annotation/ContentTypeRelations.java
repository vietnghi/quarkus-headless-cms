package com.quarkus.cms.core.schema.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeating {@link ContentTypeRelation @ContentTypeRelation} on a single
 * field (for targets where one field may define multiple relations — uncommon but supported).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ContentTypeRelations {
  ContentTypeRelation[] value();
}
