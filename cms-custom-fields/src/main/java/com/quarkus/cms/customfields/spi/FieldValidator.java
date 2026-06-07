package com.quarkus.cms.customfields.spi;

/**
 * SPI for field validators that enforce constraints on custom field values.
 *
 * <p>Validators are chained by the validation framework and run in sequence. Each validator checks
 * a specific constraint (required, unique, min/max, regex, etc.).
 */
@FunctionalInterface
public interface FieldValidator {

  /**
   * Validates a field value against this validator's constraint.
   *
   * @param context the validation context (field definition, entry data)
   * @param value the value to validate
   * @throws IllegalArgumentException if the value fails validation
   */
  void validate(FieldValidationContext context, Object value);
}
