package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/** Built-in JSON field type. Stores arbitrary JSON objects or arrays. */
@ApplicationScoped
public class JsonFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "json";
  }

  @Override
  public String getDisplayName() {
    return "JSON";
  }

  @Override
  public String getCategory() {
    return "json";
  }

  @Override
  public String getDescription() {
    return "Arbitrary JSON data (object or array)";
  }

  @Override
  public Class<?> getValueType() {
    return Object.class;
  }

  @Override
  public Object getDefaultValue() {
    return Map.of();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof Map) && !(value instanceof List)) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' requires a JSON object or array");
    }
  }

  @Override
  public Object coerce(Object value) {
    return value; // pass through
  }
}
