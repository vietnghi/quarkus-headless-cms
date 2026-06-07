package com.quarkus.cms.customfields.spi;

import java.util.Map;

/**
 * Context object for field validation, providing the field definition configuration and the full
 * entry data for cross-field validation.
 */
public class FieldValidationContext {

  private final String fieldName;
  private final String fieldType;
  private final Map<String, Object> config;
  private final Map<String, Object> entryData;

  public FieldValidationContext(
      String fieldName,
      String fieldType,
      Map<String, Object> config,
      Map<String, Object> entryData) {
    this.fieldName = fieldName;
    this.fieldType = fieldType;
    this.config = config != null ? Map.copyOf(config) : Map.of();
    this.entryData = entryData != null ? Map.copyOf(entryData) : Map.of();
  }

  /** The field name being validated. */
  public String getFieldName() {
    return fieldName;
  }

  /** The field type identifier. */
  public String getFieldType() {
    return fieldType;
  }

  /** Configuration/options from the field definition. */
  public Map<String, Object> getConfig() {
    return config;
  }

  /** The full entry data for cross-field validation. */
  public Map<String, Object> getEntryData() {
    return entryData;
  }

  /** Convenience: get a typed config value. */
  @SuppressWarnings("unchecked")
  public <T> T getConfigValue(String key, T defaultValue) {
    return (T) config.getOrDefault(key, defaultValue);
  }
}
