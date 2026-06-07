package com.quarkus.cms.core.schema.annotation;

import jakarta.enterprise.util.AnnotationLiteral;

/**
 * CDI {@link jakarta.inject.Qualifier} literal for the {@link ContentType @ContentType} annotation.
 *
 * <p>Enables runtime lookup of beans annotated with {@code @ContentType}:
 *
 * <pre>{@code
 * Instance<Object> ctBeans = CDI.current().select(Object.class, new ContentTypeLiteral());
 * }</pre>
 */
@SuppressWarnings("all")
public class ContentTypeLiteral extends AnnotationLiteral<ContentType> implements ContentType {

  @Override
  public String uid() {
    return "";
  }

  @Override
  public com.quarkus.cms.core.schema.model.ContentTypeKind kind() {
    return com.quarkus.cms.core.schema.model.ContentTypeKind.COLLECTION_TYPE;
  }

  @Override
  public String singularName() {
    return "";
  }

  @Override
  public String pluralName() {
    return "";
  }

  @Override
  public String displayName() {
    return "";
  }

  @Override
  public String description() {
    return "";
  }

  @Override
  public boolean draftAndPublish() {
    return true;
  }

  @Override
  public boolean localized() {
    return false;
  }

  @Override
  public Class<? extends java.lang.annotation.Annotation> annotationType() {
    return ContentType.class;
  }
}
