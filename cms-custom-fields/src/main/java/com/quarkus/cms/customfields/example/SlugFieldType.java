package com.quarkus.cms.customfields.example;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Example custom field type: Slug Generator.
 *
 * <p>Stores a URL-safe slug. When no value is explicitly set, it auto-generates one from a source
 * field (e.g., title) via the beforeSave hook.
 */
@ApplicationScoped
public class SlugFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "slug";
  }

  @Override
  public String getDisplayName() {
    return "Slug";
  }

  @Override
  public String getCategory() {
    return "custom";
  }

  @Override
  public String getDescription() {
    return "Auto-generated URL-safe slug from a source field";
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
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException("Field '" + fieldName + "' requires a string value");
    }
    if (!s.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' must be a valid slug (lowercase letters, numbers, hyphens)");
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    return value
        .toString()
        .toLowerCase()
        .trim()
        .replaceAll("[^a-z0-9\\-]", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }

  @Override
  public boolean supportsUnique() {
    return true;
  }

  @Override
  public boolean supportsRegex() {
    return true;
  }

  @Override
  public Map<String, Object> getPluginOptions() {
    return Map.of("sourceField", "title", "autoGenerate", true);
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of(
        "sourceField", "string - the field name to generate slug from",
        "autoGenerate", "boolean - auto-generate slug if empty");
  }
}
