package com.quarkus.cms.core.schema.component;

/**
 * Thrown by {@link ComponentCompositionService} when the component composition graph contains a
 * cycle or other structural error.
 */
public class ComponentCompositionException extends RuntimeException {

  public ComponentCompositionException(String message) {
    super(message);
  }

  public ComponentCompositionException(String message, Throwable cause) {
    super(message, cause);
  }
}
