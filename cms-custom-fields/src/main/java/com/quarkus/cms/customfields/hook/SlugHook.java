package com.quarkus.cms.customfields.hook;

import com.quarkus.cms.customfields.spi.FieldHook;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Example hook that auto-generates a URL-safe slug from a source field (e.g., title) when the slug
 * field is empty.
 *
 * <p>Configuration options:
 *
 * <ul>
 *   <li>{@code sourceField} - the field to read the base text from (default: "title")
 * </ul>
 */
@ApplicationScoped
public class SlugHook implements FieldHook {

  @Override
  public String getName() {
    return "slug-generator";
  }

  @Override
  public Object beforeSave(
      String fieldName, Object value, Map<String, Object> entryData, Map<String, Object> config) {
    // Only auto-generate if the value is null or empty
    if (value != null && !value.toString().isBlank()) {
      return value;
    }

    String sourceField =
        config != null && config.containsKey("sourceField")
            ? config.get("sourceField").toString()
            : "title";

    Object sourceValue = entryData.get(sourceField);
    if (sourceValue == null) {
      return value;
    }

    String source = sourceValue.toString();
    return source
        .toLowerCase()
        .trim()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("\\s+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }

  @Override
  public void afterSave(
      String fieldName,
      Object savedValue,
      Map<String, Object> entryData,
      Map<String, Object> config) {
    // No side effects after saving a slug
  }
}
