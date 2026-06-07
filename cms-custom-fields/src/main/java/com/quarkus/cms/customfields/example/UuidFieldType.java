package com.quarkus.cms.customfields.example;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

/**
 * Example custom field type: UUID Generator.
 *
 * <p>Auto-generates a UUID v4 when no value is set. The generated UUID is read-only in the admin
 * UI.
 */
@ApplicationScoped
public class UuidFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "uuid";
  }

  @Override
  public String getDisplayName() {
    return "UUID";
  }

  @Override
  public String getCategory() {
    return "custom";
  }

  @Override
  public String getDescription() {
    return "Auto-generated UUID v4 identifier";
  }

  @Override
  public Class<?> getValueType() {
    return String.class;
  }

  @Override
  public Object getDefaultValue() {
    return UUID.randomUUID().toString();
  }

  @Override
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException("Field '" + fieldName + "' requires a UUID string value");
    }
    try {
      UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Field '" + fieldName + "' must be a valid UUID");
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    String s = value.toString().trim();
    // Auto-generate if the value looks like it's meant to trigger generation
    if (s.isEmpty() || "__auto__".equals(s)) {
      return UUID.randomUUID().toString();
    }
    return s;
  }

  @Override
  public boolean supportsUnique() {
    return true;
  }

  @Override
  public Map<String, Object> getPluginOptions() {
    return Map.of(
        "readOnly", true,
        "autoGenerate", true);
  }
}
