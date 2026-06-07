package com.quarkus.cms.core.schema.annotation;

import jakarta.enterprise.util.AnnotationLiteral;

/**
 * CDI {@link jakarta.inject.Qualifier} literal for the {@link Component @Component} annotation.
 *
 * <p>Enables runtime lookup of beans annotated with {@code @Component}:
 *
 * <pre>{@code
 * Instance<Object> compBeans = CDI.current().select(Object.class, new ComponentLiteral());
 * }</pre>
 */
@SuppressWarnings("all")
public class ComponentLiteral extends AnnotationLiteral<Component> implements Component {

  @Override
  public String uid() {
    return "";
  }

  @Override
  public String category() {
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
  public Class<? extends java.lang.annotation.Annotation> annotationType() {
    return Component.class;
  }
}
