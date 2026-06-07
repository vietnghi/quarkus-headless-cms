package com.quarkus.cms.core.schema.component;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime validator for component data payloads.
 *
 * <p>Given a {@link ComponentDefinition} and a map of field values, validates:
 *
 * <ul>
 *   <li>Required fields are present and non-null.
 *   <li>Type-appropriate constraints (string length, numeric range, enumeration membership).
 *   <li>Nested component data recursively when a field is of type {@link FieldType#COMPONENT}.
 *   <li>Component arrays (repeatable fields) have each item validated individually.
 * </ul>
 *
 * <p>This is a CDI bean. The {@link #setSchemaStorage(SchemaStorageService)} setter is available
 * for tests that instantiate it manually.
 */
@ApplicationScoped
public class ComponentDataValidator {

  @Inject SchemaStorageService schemaStorage;

  /** Provided for manual wiring in unit tests. */
  public void setSchemaStorage(SchemaStorageService schemaStorage) {
    this.schemaStorage = schemaStorage;
  }

  /**
   * Validates the given data payload against a component definition. Returns a list of error
   * messages; an empty list means the payload is valid.
   */
  public List<String> validate(ComponentDefinition comp, Map<String, Object> data) {
    return validateInternal(comp, data, "", new RecursionGuard());
  }

  /**
   * Validates repeatedly when the field is a repeatable component (list of component data objects).
   */
  public List<String> validateRepeatable(
      ComponentDefinition comp, List<Map<String, Object>> items) {
    List<String> errors = new ArrayList<>();
    RecursionGuard guard = new RecursionGuard();
    for (int i = 0; i < items.size(); i++) {
      String prefix = "[" + i + "]";
      errors.addAll(validateInternal(comp, items.get(i), prefix, guard));
    }
    return errors;
  }

  // ---- Internal ----

  private List<String> validateInternal(
      ComponentDefinition comp, Map<String, Object> data, String pathPrefix, RecursionGuard guard) {

    List<String> errors = new ArrayList<>();
    if (data == null) {
      errors.add(pathPrefix(comp.getUid(), pathPrefix) + "data is null");
      return errors;
    }

    // Track this component instance for cycle detection
    if (!guard.enter(comp.getUid(), pathPrefix)) {
      errors.add(
          pathPrefix(comp.getUid(), pathPrefix)
              + "circular component reference detected: "
              + comp.getUid());
      return errors;
    }

    try {
      for (FieldDefinition field : comp.getFields()) {
        String fieldPath = fieldPath(field.getName(), pathPrefix);
        boolean hasValue = data.containsKey(field.getName());
        Object value = data.get(field.getName());

        // Required check
        if (field.isRequired() && (!hasValue || value == null)) {
          errors.add(fieldPath + "is required");
          continue;
        }

        if (!hasValue || value == null) {
          continue; // optional and absent — skip further checks
        }

        switch (field.getType()) {
          case STRING, TEXT, EMAIL, UID, PASSWORD -> errors.addAll(
              validateStringField(field, (String) value, fieldPath));
          case INTEGER -> errors.addAll(
              validateIntegerField(field, value, fieldPath));
          case FLOAT, DECIMAL -> errors.addAll(
              validateNumericField(field, ((Number) value).doubleValue(), fieldPath));
          case BOOLEAN -> errors.addAll(validateBooleanField(field, value, fieldPath));
          case ENUMERATION -> errors.addAll(
              validateEnumField(field, (String) value, fieldPath));
          case JSON, RICHTEXT -> {
            // Pass-through — any JSON is acceptable
          }
          case DATE, DATETIME, TIME -> errors.addAll(
              validateDateField(field, value, fieldPath));
          case MEDIA -> errors.addAll(validateMediaField(field, value, fieldPath));
          case COMPONENT -> errors.addAll(
              validateComponentField(field, value, fieldPath, guard));
          default -> {
            // Unknown/unhandled type — skip
          }
        }
      }
    } finally {
      guard.exit(comp.getUid(), pathPrefix);
    }

    return errors;
  }

  private List<String> validateStringField(FieldDefinition field, String value, String path) {
    List<String> errors = new ArrayList<>();
    if (value == null) return errors;

    if (field.getMinLength() != null && value.length() < field.getMinLength()) {
      errors.add(
          path
              + "length "
              + value.length()
              + " is less than minimum "
              + field.getMinLength());
    }
    if (field.getMaxLength() != null && value.length() > field.getMaxLength()) {
      errors.add(
          path
              + "length "
              + value.length()
              + " exceeds maximum "
              + field.getMaxLength());
    }
    if (field.getRegex() != null && !value.matches(field.getRegex())) {
      errors.add(path + "does not match pattern: " + field.getRegex());
    }
    return errors;
  }

  private List<String> validateIntegerField(FieldDefinition field, Object value, String path) {
    List<String> errors = new ArrayList<>();
    long longVal;
    if (value instanceof Number n) {
      longVal = n.longValue();
    } else {
      errors.add(path + "must be a number");
      return errors;
    }
    if (field.getMin() != null && longVal < field.getMin()) {
      errors.add(path + longVal + " is less than minimum " + field.getMin());
    }
    if (field.getMax() != null && longVal > field.getMax()) {
      errors.add(path + longVal + " exceeds maximum " + field.getMax());
    }
    return errors;
  }

  private List<String> validateNumericField(FieldDefinition field, double value, String path) {
    List<String> errors = new ArrayList<>();
    if (field.getMin() != null && value < field.getMin()) {
      errors.add(path + value + " is less than minimum " + field.getMin());
    }
    if (field.getMax() != null && value > field.getMax()) {
      errors.add(path + value + " exceeds maximum " + field.getMax());
    }
    return errors;
  }

  private List<String> validateBooleanField(
      FieldDefinition field, Object value, String path) {
    List<String> errors = new ArrayList<>();
    if (!(value instanceof Boolean)) {
      errors.add(path + "must be a boolean");
    }
    return errors;
  }

  private List<String> validateEnumField(
      FieldDefinition field, String value, String path) {
    List<String> errors = new ArrayList<>();
    if (value == null) return errors;
    if (field.getEnumValues() != null && !field.getEnumValues().contains(value)) {
      errors.add(
          path
              + "'"
              + value
              + "' is not a valid enum value; expected one of: "
              + field.getEnumValues());
    }
    return errors;
  }

  private List<String> validateDateField(
      FieldDefinition field, Object value, String path) {
    // Basic sanity check — must be a string
    List<String> errors = new ArrayList<>();
    if (!(value instanceof String)) {
      errors.add(path + "must be a date/time string");
    }
    return errors;
  }

  @SuppressWarnings("unchecked")
  private List<String> validateMediaField(
      FieldDefinition field, Object value, String path) {
    List<String> errors = new ArrayList<>();
    if (value instanceof Map) {
      // Media object — acceptable
    } else if (value instanceof Number || value instanceof String) {
      // ID reference — acceptable
    } else {
      errors.add(path + "must be a media object or ID");
    }
    return errors;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<String> validateComponentField(
      FieldDefinition field, Object value, String path, RecursionGuard guard) {

    List<String> errors = new ArrayList<>();
    String componentUid = field.getComponent();
    if (componentUid == null || componentUid.isBlank()) {
      errors.add(path + "refers to a component but no component UID is configured");
      return errors;
    }

    ComponentDefinition targetComp = schemaStorage != null
        ? schemaStorage.getComponent(componentUid)
        : null;
    if (targetComp == null) {
      // We cannot validate — the referenced component is not registered.
      // The schema-level validator should catch this earlier.
      return errors;
    }

    if (field.isRepeatable()) {
      if (!(value instanceof List list)) {
        errors.add(path + "requires a list of component data objects");
        return errors;
      }
      // Validate min/max components cardinality
      if (field.getMinComponents() > 0 && list.size() < field.getMinComponents()) {
        errors.add(
            path
                + "requires at least "
                + field.getMinComponents()
                + " components, got "
                + list.size());
      }
      if (field.getMaxComponents() > 0 && list.size() > field.getMaxComponents()) {
        errors.add(
            path
                + "allows at most "
                + field.getMaxComponents()
                + " components, got "
                + list.size());
      }
      for (int i = 0; i < list.size(); i++) {
        Object item = list.get(i);
        if (item instanceof Map itemMap) {
          errors.addAll(
              validateInternal(targetComp, (Map<String, Object>) itemMap, path + "[" + i + "]", guard));
        } else {
          errors.add(path + "[" + i + "] must be a component data object");
        }
      }
    } else {
      if (!(value instanceof Map itemMap)) {
        errors.add(path + "requires a component data object");
        return errors;
      }
      errors.addAll(validateInternal(targetComp, itemMap, path, guard));
    }

    return errors;
  }

  // ---- helpers ----

  private static String pathPrefix(String uid, String prefix) {
    return prefix.isEmpty() ? uid + ": " : prefix + " in " + uid + ": ";
  }

  private static String fieldPath(String fieldName, String prefix) {
    return prefix.isEmpty() ? "'" + fieldName + "': " : prefix + ".'" + fieldName + "': ";
  }

  /**
   * Lightweight cycle detector for recursive component validation. Tracks the current call stack
   * as (componentUid, pathPrefix) pairs to detect when the same component instance is re-entered
   * without having been exited.
   */
  static class RecursionGuard {
    private final Set<String> stack = new java.util.HashSet<>();

    /** Returns {@code false} if already visiting this component (cycle detected). */
    boolean enter(String componentUid, String pathPrefix) {
      String key = componentUid + "@" + pathPrefix;
      return stack.add(key);
    }

    void exit(String componentUid, String pathPrefix) {
      String key = componentUid + "@" + pathPrefix;
      stack.remove(key);
    }
  }
}
