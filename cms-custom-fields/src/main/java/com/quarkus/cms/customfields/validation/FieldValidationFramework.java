package com.quarkus.cms.customfields.validation;

import com.quarkus.cms.customfields.spi.CustomFieldType;
import com.quarkus.cms.customfields.spi.CustomFieldTypeRegistry;
import com.quarkus.cms.customfields.spi.FieldValidationContext;
import com.quarkus.cms.customfields.spi.FieldValidator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.*;

/**
 * Central validation engine for custom field values.
 *
 * <p>Combines type-level validation from {@link CustomFieldType} with constraint-level validation
 * from registered {@link FieldValidator} beans. All field values are validated before they are
 * persisted.
 */
@ApplicationScoped
public class FieldValidationFramework {

  @Inject CustomFieldTypeRegistry typeRegistry;

  @Inject Instance<FieldValidator> validators;

  /**
   * Validates a single field value against its field type and all registered validators.
   *
   * @param fieldName the field name
   * @param fieldType the field type ID
   * @param value the value to validate
   * @param config the field definition's configuration options
   * @param entryData the full entry data for cross-field validation
   * @return a {@link ValidationResult} with any errors
   */
  public ValidationResult validateField(
      String fieldName,
      String fieldType,
      Object value,
      Map<String, Object> config,
      Map<String, Object> entryData) {
    ValidationResult result = new ValidationResult();

    if (config == null) {
      config = new LinkedHashMap<>();
    }

    // Required check
    if (Boolean.TRUE.equals(config.get("required")) && isEmptyValue(value)) {
      result.addError("Field '" + fieldName + "' is required");
      return result;
    }

    // Skip further validation if value is null/empty (not required, so ok)
    if (isEmptyValue(value)) {
      return result;
    }

    // Type-level validation via SPI
    CustomFieldType fieldTypeImpl = typeRegistry.getType(fieldType);
    if (fieldTypeImpl != null) {
      try {
        fieldTypeImpl.validate(fieldName, value, config);
      } catch (IllegalArgumentException e) {
        result.addError(e.getMessage());
      }
    }

    // Constraint-level validation from registered validators
    FieldValidationContext context =
        new FieldValidationContext(fieldName, fieldType, config, entryData);
    for (FieldValidator validator : validators) {
      try {
        validator.validate(context, value);
      } catch (IllegalArgumentException e) {
        result.addError(e.getMessage());
      }
    }

    return result;
  }

  /**
   * Validates multiple field values at once.
   *
   * @param fieldDefinitions map of fieldName -> fieldTypeId
   * @param configs map of fieldName -> configuration options
   * @param values map of fieldName -> value
   * @param entryData the full entry data
   * @return a {@link ValidationResult} with all errors
   */
  public ValidationResult validateFields(
      Map<String, String> fieldDefinitions,
      Map<String, Map<String, Object>> configs,
      Map<String, Object> values,
      Map<String, Object> entryData) {
    ValidationResult result = new ValidationResult();

    for (Map.Entry<String, String> entry : fieldDefinitions.entrySet()) {
      String fieldName = entry.getKey();
      String fieldType = entry.getValue();
      Object value = values.get(fieldName);
      Map<String, Object> config = configs.getOrDefault(fieldName, Map.of());

      // For required checks on missing fields
      boolean isRequired = Boolean.TRUE.equals(config.get("required"));
      if (isRequired && !values.containsKey(fieldName)) {
        result.addError("Field '" + fieldName + "' is required");
        continue;
      }

      ValidationResult fieldResult = validateField(fieldName, fieldType, value, config, entryData);
      result.addAll(fieldResult);
    }

    return result;
  }

  /** Checks if a value is considered "empty" for required-field purposes. */
  private boolean isEmptyValue(Object value) {
    if (value == null) return true;
    if (value instanceof String s && s.isBlank()) return true;
    if (value instanceof Collection<?> c && c.isEmpty()) return true;
    if (value instanceof Map<?, ?> m && m.isEmpty()) return true;
    return false;
  }
}
