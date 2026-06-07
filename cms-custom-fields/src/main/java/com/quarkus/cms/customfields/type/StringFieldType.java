package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;

/** Built-in string-based field types: text, email, password, url, textarea, rich text. */
@ApplicationScoped
public class StringFieldType implements CustomFieldType {

  private static final Set<String> SUB_TYPES =
      Set.of("text", "email", "password", "url", "textarea", "richtext");

  @Override
  public String getTypeId() {
    return "string";
  }

  @Override
  public String getDisplayName() {
    return "String";
  }

  @Override
  public String getCategory() {
    return "string";
  }

  @Override
  public String getDescription() {
    return "Text content with various format presets: plain text, email, password, URL, textarea, rich text";
  }

  @Override
  public Class<?> getValueType() {
    return String.class;
  }

  @Override
  public Object getDefaultValue() {
    return "";
  }

  @Override
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException(
          "Field '"
              + fieldName
              + "' requires a string value, got "
              + value.getClass().getSimpleName());
    }

    String subType = (String) config.getOrDefault("subType", "text");

    // Format-specific validation
    switch (subType) {
      case "email":
        if (!s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
          throw new IllegalArgumentException(
              "Field '" + fieldName + "' must be a valid email address");
        }
        break;
      case "url":
        if (!s.matches("^https?://\\S+$") && !s.matches("^/\\S*$")) {
          throw new IllegalArgumentException(
              "Field '" + fieldName + "' must be a valid URL (absolute or relative)");
        }
        break;
      case "password":
        // Password stored as plain text in entry (hashed by auth layer), just check length
        break;
    }

    // Length constraints
    if (config.containsKey("minLength")) {
      int min = ((Number) config.get("minLength")).intValue();
      if (s.length() < min) {
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' must be at least " + min + " characters");
      }
    }
    if (config.containsKey("maxLength")) {
      int max = ((Number) config.get("maxLength")).intValue();
      if (s.length() > max) {
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' must be at most " + max + " characters");
      }
    }

    // Regex pattern
    if (config.containsKey("regex")) {
      String regex = (String) config.get("regex");
      if (!s.matches(regex)) {
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' does not match the required pattern");
      }
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    return value.toString();
  }

  @Override
  public boolean supportsUnique() {
    return true;
  }

  @Override
  public boolean supportsLengthConstraints() {
    return true;
  }

  @Override
  public boolean supportsRegex() {
    return true;
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of(
        "subType", "string (text, email, password, url, textarea, richtext)",
        "minLength", "integer - minimum string length",
        "maxLength", "integer - maximum string length",
        "regex", "string - regex pattern for validation");
  }

  /** Returns true if the given sub-type is handled by this field type. */
  public static boolean handlesSubType(String subType) {
    return SUB_TYPES.contains(subType);
  }
}
