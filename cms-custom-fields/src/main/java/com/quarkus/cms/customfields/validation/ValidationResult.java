package com.quarkus.cms.customfields.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a field validation operation. Contains all validation errors encountered (rather than
 * failing fast on the first one).
 */
public class ValidationResult {

  private final List<String> errors = new ArrayList<>();

  /** Returns true if no validation errors were found. */
  public boolean isValid() {
    return errors.isEmpty();
  }

  /** Returns the list of validation error messages. */
  public List<String> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  /** Adds a validation error message. */
  public void addError(String error) {
    errors.add(error);
  }

  /** Adds all errors from another result. */
  public void addAll(ValidationResult other) {
    errors.addAll(other.errors);
  }

  /** Returns a single combined error message, or null if valid. */
  public String toErrorMessage() {
    if (errors.isEmpty()) return null;
    if (errors.size() == 1) return errors.get(0);
    return "Validation failed: " + String.join("; ", errors);
  }

  /** Throws IllegalArgumentException if the result contains errors. */
  public void throwIfInvalid() {
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(toErrorMessage());
    }
  }

  /** Creates an empty (valid) result. */
  public static ValidationResult valid() {
    return new ValidationResult();
  }

  /** Creates a result with a single error. */
  public static ValidationResult error(String message) {
    ValidationResult r = new ValidationResult();
    r.addError(message);
    return r;
  }
}
