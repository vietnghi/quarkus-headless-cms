package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/** Built-in boolean field type. */
@ApplicationScoped
public class BooleanFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "boolean";
  }

  @Override
  public String getDisplayName() {
    return "Boolean";
  }

  @Override
  public String getCategory() {
    return "boolean";
  }

  @Override
  public String getDescription() {
    return "True/false toggle";
  }

  @Override
  public Class<?> getValueType() {
    return Boolean.class;
  }

  @Override
  public Object getDefaultValue() {
    return false;
  }

  @Override
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof Boolean)) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' requires a boolean value (true/false)");
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    if (value instanceof Boolean) return value;
    if (value instanceof String s) {
      return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }
    if (value instanceof Number n) {
      return n.doubleValue() != 0;
    }
    return Boolean.TRUE;
  }
}
