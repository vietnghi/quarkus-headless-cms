package com.quarkus.cms.customfields;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.customfields.hook.HookExecutor;
import com.quarkus.cms.customfields.spi.CustomFieldType;
import com.quarkus.cms.customfields.spi.CustomFieldTypeRegistry;
import com.quarkus.cms.customfields.storage.FieldStorageStrategy;
import com.quarkus.cms.customfields.storage.JsonbFieldStorageStrategy;
import com.quarkus.cms.customfields.validation.FieldValidationFramework;
import com.quarkus.cms.customfields.validation.ValidationResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing custom field definitions and their values.
 *
 * <p>Integrates with the SPI-based validation framework, server-side hooks, and pluggable storage
 * strategies.
 */
@ApplicationScoped
public class CustomFieldService {

  @Inject CustomFieldTypeRegistry typeRegistry;

  @Inject FieldValidationFramework validationFramework;

  @Inject HookExecutor hookExecutor;

  @Inject JsonbFieldStorageStrategy defaultStorage;

  /** For unit testing: set the storage strategy manually. */
  void setDefaultStorage(JsonbFieldStorageStrategy storage) {
    this.defaultStorage = storage;
  }

  // ---- Custom Field Definition Management ----

  /** Defines a new custom field for a content type. */
  @Transactional
  public CustomFieldDefinition defineField(
      String contentType,
      String fieldName,
      String label,
      String fieldType,
      String defaultValue,
      boolean required,
      String placeholder,
      String options,
      int sortOrder) {
    // Validate field type exists
    if (fieldType != null && !fieldType.isBlank() && !typeRegistry.hasType(fieldType)) {
      throw new IllegalArgumentException(
          "Unknown field type: '" + fieldType + "'. Available types: " + typeRegistry.getTypeIds());
    }

    // Check for duplicate
    CustomFieldDefinition existing =
        CustomFieldDefinition.findByContentTypeAndName(contentType, fieldName);
    if (existing != null) {
      throw new IllegalArgumentException(
          "Custom field '" + fieldName + "' already exists for content type '" + contentType + "'");
    }

    var def = new CustomFieldDefinition();
    def.contentType = contentType;
    def.fieldName = fieldName;
    def.label = label;
    def.fieldType = fieldType != null && !fieldType.isBlank() ? fieldType : "string";
    def.defaultValue = defaultValue;
    def.required = required;
    def.placeholder = placeholder;
    def.options = options;
    def.sortOrder = sortOrder;
    def.persist();
    return def;
  }

  /** Updates an existing custom field definition. */
  @Transactional
  public CustomFieldDefinition updateField(
      Long fieldId,
      String label,
      String fieldType,
      String defaultValue,
      boolean required,
      String placeholder,
      String options,
      int sortOrder) {
    CustomFieldDefinition def = CustomFieldDefinition.findById(fieldId);
    if (def == null) {
      throw new IllegalArgumentException("Custom field definition not found: " + fieldId);
    }
    if (label != null) def.label = label;
    if (fieldType != null) {
      if (!typeRegistry.hasType(fieldType)) {
        throw new IllegalArgumentException(
            "Unknown field type: '"
                + fieldType
                + "'. Available types: "
                + typeRegistry.getTypeIds());
      }
      def.fieldType = fieldType;
    }
    if (defaultValue != null) def.defaultValue = defaultValue;
    def.required = required;
    if (placeholder != null) def.placeholder = placeholder;
    if (options != null) def.options = options;
    def.sortOrder = sortOrder;
    def.persist();
    return def;
  }

  /** Removes a custom field definition and all its values from entries. */
  @Transactional
  public void removeField(Long fieldId) {
    CustomFieldDefinition def = CustomFieldDefinition.findById(fieldId);
    if (def == null) {
      throw new IllegalArgumentException("Custom field definition not found: " + fieldId);
    }

    // Remove values for this field from all entries of this content type
    String contentType = def.contentType;
    String fieldName = def.fieldName;
    List<CmsEntry> entries = CmsEntry.list("contentType", contentType);
    for (CmsEntry entry : entries) {
      defaultStorage.removeValue(entry, fieldName);
      entry.persist();
    }

    def.delete();
  }

  /** Returns all custom field definitions for a content type. */
  public List<CustomFieldDefinition> getFields(String contentType) {
    return CustomFieldDefinition.findByContentType(contentType);
  }

  /** Returns a single custom field definition. */
  public CustomFieldDefinition getField(String contentType, String fieldName) {
    return CustomFieldDefinition.findByContentTypeAndName(contentType, fieldName);
  }

  // ---- Custom Field Values with full lifecycle ----

  /** Retrieves the custom fields data for a CmsEntry. */
  public Map<String, Object> getCustomValues(CmsEntry entry) {
    return defaultStorage.readAllValues(entry);
  }

  /** Gets a single custom field value from an entry. */
  public Object getCustomValue(CmsEntry entry, String fieldName) {
    Object value = defaultStorage.readValue(entry, fieldName);
    if (value != null) return value;

    // Fall back to default value from definition
    CustomFieldDefinition def =
        CustomFieldDefinition.findByContentTypeAndName(entry.contentType, fieldName);
    if (def != null) {
      if (def.config != null && def.config.containsKey("defaultValue")) {
        return def.config.get("defaultValue");
      }
      if (def.defaultValue != null) {
        return coerceDefault(def.fieldType, def.defaultValue);
      }
    }
    return null;
  }

  /** Sets a single custom field value on an entry, with full validation and hooks. */
  @Transactional
  public CmsEntry setCustomValue(CmsEntry entry, String fieldName, Object value) {
    CustomFieldDefinition def =
        CustomFieldDefinition.findByContentTypeAndName(entry.contentType, fieldName);
    if (def == null) {
      throw new IllegalArgumentException(
          "Unknown custom field '" + fieldName + "' for content type '" + entry.contentType + "'");
    }

    // Build config map from definition + validation rules
    Map<String, Object> effectiveConfig = buildEffectiveConfig(def);

    // Validate value
    if (value != null && !(value instanceof String && ((String) value).isBlank())) {
      ValidationResult validationResult =
          validationFramework.validateField(
              fieldName, def.fieldType, value, effectiveConfig, entry.data);
      validationResult.throwIfInvalid();

      // Coerce value via the field type SPI
      CustomFieldType fieldType = typeRegistry.getType(def.fieldType);
      if (fieldType != null) {
        value = fieldType.coerce(value);
      }
    } else {
      if (def.required) {
        throw new IllegalArgumentException(
            "Required custom field '" + fieldName + "' cannot be empty");
      }
      value = null;
    }

    // Execute beforeSave hooks
    value = hookExecutor.executeBeforeSave(fieldName, value, entry.data, effectiveConfig);

    // Store
    if (value == null) {
      defaultStorage.removeValue(entry, fieldName);
    } else {
      defaultStorage.writeValue(entry, fieldName, value);
    }

    entry.persist();

    // Execute afterSave hooks
    hookExecutor.executeAfterSave(fieldName, value, entry.data, effectiveConfig);

    return entry;
  }

  /** Sets multiple custom field values at once, with full validation. */
  @Transactional
  public CmsEntry setCustomValues(CmsEntry entry, Map<String, Object> values) {
    for (Map.Entry<String, Object> kv : values.entrySet()) {
      setCustomValue(entry, kv.getKey(), kv.getValue());
    }
    return entry;
  }

  /** Validates all custom field values in an entry against their definitions. */
  @SuppressWarnings("unchecked")
  public void validateEntryCustomFields(CmsEntry entry) {
    List<CustomFieldDefinition> defs = CustomFieldDefinition.findByContentType(entry.contentType);
    Map<String, Object> custom = defaultStorage.readAllValues(entry);

    ValidationResult combinedResult = new ValidationResult();

    for (CustomFieldDefinition def : defs) {
      Object value = custom.get(def.fieldName);
      Map<String, Object> effectiveConfig = buildEffectiveConfig(def);

      // Required check
      if (value == null && def.required) {
        combinedResult.addError("Required custom field '" + def.fieldName + "' is missing");
        continue;
      }

      if (value != null) {
        ValidationResult result =
            validationFramework.validateField(
                def.fieldName, def.fieldType, value, effectiveConfig, entry.data);
        combinedResult.addAll(result);
      }
    }

    combinedResult.throwIfInvalid();
  }

  // ---- Storage strategy management ----

  /**
   * Returns the storage strategy to use for a given field definition. Currently always returns the
   * JSONB strategy.
   */
  public FieldStorageStrategy getStorageStrategy(CustomFieldDefinition def) {
    if (def.config != null && def.config.containsKey("storageStrategy")) {
      String strategyName = (String) def.config.get("storageStrategy");
      if ("separate_table".equals(strategyName)) {
        // Would return SeparateTableFieldStorageStrategy if injected
      }
    }
    return defaultStorage;
  }

  // ---- Internal helpers ----

  /**
   * Builds an effective configuration map from a field definition, combining explicit config,
   * validation rules, and defaults.
   */
  private Map<String, Object> buildEffectiveConfig(CustomFieldDefinition def) {
    Map<String, Object> config = new LinkedHashMap<>();

    // Start with any stored config
    if (def.config != null) {
      config.putAll(def.config);
    }

    // Add definition-level properties as config
    config.putIfAbsent("required", def.required);
    if (def.defaultValue != null) {
      config.putIfAbsent("defaultValue", def.defaultValue);
    }
    if (def.options != null && !def.options.isBlank()) {
      config.putIfAbsent(
          "enumValues",
          Arrays.stream(def.options.split(",")).map(String::trim).collect(Collectors.toList()));
    }

    return config;
  }

  private Object coerceDefault(String fieldType, String defaultValue) {
    CustomFieldType type = typeRegistry.getType(fieldType);
    if (type != null) {
      return type.coerce(defaultValue);
    }
    return switch (fieldType) {
      case "number" -> {
        try {
          yield Double.parseDouble(defaultValue);
        } catch (NumberFormatException e) {
          yield defaultValue;
        }
      }
      case "boolean" -> Boolean.parseBoolean(defaultValue);
      default -> defaultValue;
    };
  }
}
