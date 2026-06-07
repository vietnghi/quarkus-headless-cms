package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/**
 * Built-in dynamic zone field type. Allows content editors to dynamically compose content from a
 * set of allowed components.
 */
@ApplicationScoped
public class DynamicZoneFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "dynamiczone";
  }

  @Override
  public String getDisplayName() {
    return "Dynamic Zone";
  }

  @Override
  public String getCategory() {
    return "component";
  }

  @Override
  public String getDescription() {
    return "Dynamic composition zone: choose from allowed components";
  }

  @Override
  public Class<?> getValueType() {
    return List.class;
  }

  @Override
  public Object getDefaultValue() {
    return List.of();
  }

  @Override
  @SuppressWarnings({"rawtypes"})
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(
          "Field '"
              + fieldName
              + "' (dynamic zone) requires a list of component data objects, "
              + "each with a '__component' key");
    }
    List items = (List) value;

    List<String> allowedComponents = (List<String>) config.get("allowedComponents");
    int minComponents =
        config.containsKey("minComponents") ? ((Number) config.get("minComponents")).intValue() : 0;
    int maxComponents =
        config.containsKey("maxComponents") ? ((Number) config.get("maxComponents")).intValue() : 0;

    if (items.size() < minComponents) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' requires at least " + minComponents + " component(s)");
    }
    if (maxComponents > 0 && items.size() > maxComponents) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' allows at most " + maxComponents + " component(s)");
    }

    if (allowedComponents != null && !allowedComponents.isEmpty()) {
      for (Object item : items) {
        if (!(item instanceof Map mapItem)) {
          throw new IllegalArgumentException(
              "Field '" + fieldName + "' each item must be an object with '__component'");
        }
        Object comp = mapItem.get("__component");
        if (comp == null || !allowedComponents.contains(comp.toString())) {
          throw new IllegalArgumentException(
              "Field '"
                  + fieldName
                  + "' component '"
                  + comp
                  + "' is not in the allowed list: "
                  + allowedComponents);
        }
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
        "allowedComponents", "array of strings - component UIDs allowed in this zone",
        "minComponents", "integer - minimum number of components",
        "maxComponents", "integer - maximum number of components");
  }
}
