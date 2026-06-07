package com.quarkus.cms.core.schema.storage;

/** Thrown when a schema definition fails validation. */
public class SchemaValidationException extends RuntimeException {

  public SchemaValidationException(String message) {
    super(message);
  }

  public SchemaValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
