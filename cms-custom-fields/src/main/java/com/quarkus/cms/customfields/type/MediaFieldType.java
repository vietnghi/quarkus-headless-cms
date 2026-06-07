package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/**
 * Built-in media field type. Supports single or multiple media uploads.
 *
 * <p>Single media stores a single media ID string. Multiple media stores a list of media ID
 * strings.
 */
@ApplicationScoped
public class MediaFieldType implements CustomFieldType {

  @Override
  public String getTypeId() {
    return "media";
  }

  @Override
  public String getDisplayName() {
    return "Media";
  }

  @Override
  public String getCategory() {
    return "media";
  }

  @Override
  public String getDescription() {
    return "Media/file upload: single file or multiple files";
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
  @SuppressWarnings("rawtypes")
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;

    String subType = (String) config.getOrDefault("subType", "single");

    if ("single".equals(subType)) {
      if (!(value instanceof String)) {
        throw new IllegalArgumentException("Field '" + fieldName + "' requires a media ID string");
      }
    } else if ("multiple".equals(subType)) {
      if (!(value instanceof List)) {
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' requires a list of media IDs");
      }
      for (Object item : (List) value) {
        if (!(item instanceof String)) {
          throw new IllegalArgumentException(
              "Field '" + fieldName + "' requires a list of media ID strings");
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object coerce(Object value) {
    if (value == null) return null;
    return value; // pass through
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of("subType", "string (single, multiple)");
  }
}
