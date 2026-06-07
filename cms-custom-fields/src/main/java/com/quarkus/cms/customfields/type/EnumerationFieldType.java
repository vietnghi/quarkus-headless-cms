package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/** Built-in enumeration field type. Allows selecting one value from a predefined list. */
@ApplicationScoped
public class EnumerationFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "enumeration";
  }

  @Override
  public String getDisplayName() {
    return "Enumeration";
  }

  @Override
  public String getCategory() {
    return "string";
  }

  @Override
  public String getDescription() {
    return "Select one value from a predefined list of options";
  }

  @Override
  public Class<?> getValueType() {
    return String.class;
  }

  @Override
  public Object getDefaultValue() {
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException("Field '" + fieldName + "' requires a string value");
    }

    List<String> enumValues = (List<String>) config.get("enumValues");
    if (enumValues == null || enumValues.isEmpty()) {
      return; // no values defined - accept anything
    }

    if (!enumValues.contains(s)) {
      throw new IllegalArgumentException("Field '" + fieldName + "' must be one of: " + enumValues);
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    return value.toString();
  }

  @Override
  public boolean supportsEnumValues() {
    return true;
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of("enumValues", "array of strings - allowed values");
  }
}
