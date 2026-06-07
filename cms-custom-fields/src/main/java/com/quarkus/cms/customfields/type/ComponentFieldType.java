package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/**
 * Built-in component field type. Embeds a repeatable or non-repeatable component within a content
 * type entry.
 */
@ApplicationScoped
public class ComponentFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "component";
  }

  @Override
  public String getDisplayName() {
    return "Component";
  }

  @Override
  public String getCategory() {
    return "component";
  }

  @Override
  public String getDescription() {
    return "Embed a component: repeatable (array) or non-repeatable (single object)";
  }

  @Override
  public Class<?> getValueType() {
    return Object.class;
  }

  @Override
  public Object getDefaultValue() {
    return null;
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;

    String componentUid = (String) config.get("component");
    if (componentUid == null || componentUid.isBlank()) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' requires a 'component' configuration");
    }

    boolean repeatable = Boolean.TRUE.equals(config.get("repeatable"));

    if (repeatable) {
      if (!(value instanceof List)) {
        throw new IllegalArgumentException(
            "Field '"
                + fieldName
                + "' (repeatable component) requires a list of component data objects");
      }
    } else {
      if (!(value instanceof Map)) {
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' (component) requires a component data object");
      }
    }
  }

  @Override
  public Object coerce(Object value) {
    return value; // pass through
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of(
        "component", "string - component UID",
        "repeatable", "boolean - whether the component is repeatable");
  }
}
