package com.quarkus.cms.customfields.spi;

import java.util.Map;

/**
 * SPI interface for defining a custom field type in the CMS.
 *
 * <p>Implement this interface to register new field types beyond the built-in ones. Each custom
 * field type defines its own category, validation rules, default value, storage preferences, and
 * optional server-side hooks.
 *
 * <p>Types are discovered via the {@link CustomFieldTypeRegistry} CDI bean.
 */
public interface CustomFieldType {

  /**
   * Unique identifier for this field type. Must be kebab-case, e.g. {@code "color-picker"}, {@code
   * "slug"}, {@code "uuid"}.
   */
  String getTypeId();

  /** Human-readable display name, e.g. "Color Picker". */
  String getDisplayName();

  /**
   * Category grouping for the admin UI: "string", "number", "date", "media", "relation",
   * "component", "custom".
   */
  String getCategory();

  /** Description of what this field type does. */
  String getDescription();

  /**
   * The Java type that values of this field will be coerced to at runtime. Common types:
   * String.class, Integer.class, Double.class, Boolean.class, Map.class, List.class.
   */
  Class<?> getValueType();

  /** Returns the default value for this field type when none is specified. May return null. */
  Object getDefaultValue();

  /**
   * Validates a value for this field type. Throws IllegalArgumentException if the value is invalid,
   * or returns normally.
   *
   * @param fieldName the name of the field being validated
   * @param value the value to validate
   * @param config configuration options from the field definition (validation rules)
   * @throws IllegalArgumentException if validation fails
   */
  void validate(String fieldName, Object value, Map<String, Object> config);

  /**
   * Coerces a raw (possibly string) value into the proper Java type for this field.
   *
   * @param value the raw value
   * @return the coerced value
   */
  Object coerce(Object value);

  /** Whether this field type supports the "unique" constraint. */
  default boolean supportsUnique() {
    return false;
  }

  /** Whether this field type supports min/max length constraints. */
  default boolean supportsLengthConstraints() {
    return false;
  }

  /** Whether this field type supports min/max value constraints. */
  default boolean supportsRangeConstraints() {
    return false;
  }

  /** Whether this field type supports regex pattern validation. */
  default boolean supportsRegex() {
    return false;
  }

  /** Whether this field type supports enumeration of allowed values. */
  default boolean supportsEnumValues() {
    return false;
  }

  /** Plugin options that can be passed to the admin UI for rendering. */
  default Map<String, Object> getPluginOptions() {
    return Map.of();
  }

  /**
   * Additional configuration options that this field type accepts. Keys are option names (e.g.
   * "maxLength", "allowedPatterns"), values describe the expected type and purpose.
   */
  default Map<String, String> getConfigSchema() {
    return Map.of();
  }
}
