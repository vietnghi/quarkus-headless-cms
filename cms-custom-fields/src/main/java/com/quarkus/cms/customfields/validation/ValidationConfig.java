package com.quarkus.cms.customfields.validation;

import java.util.Map;

/**
 * Configuration for a field's validation rules.
 *
 * <p>These rules are defined when the field is created and applied during value validation.
 */
public class ValidationConfig {

  private final boolean required;
  private final boolean unique;
  private final Integer minLength;
  private final Integer maxLength;
  private final Double min;
  private final Double max;
  private final String regex;

  public ValidationConfig(
      boolean required,
      boolean unique,
      Integer minLength,
      Integer maxLength,
      Double min,
      Double max,
      String regex) {
    this.required = required;
    this.unique = unique;
    this.minLength = minLength;
    this.maxLength = maxLength;
    this.min = min;
    this.max = max;
    this.regex = regex;
  }

  /** Creates a ValidationConfig from a field definition's options map. */
  @SuppressWarnings("unchecked")
  public static ValidationConfig fromOptions(Map<String, Object> options) {
    if (options == null) return new ValidationConfig(false, false, null, null, null, null, null);

    return new ValidationConfig(
        Boolean.TRUE.equals(options.get("required")),
        Boolean.TRUE.equals(options.get("unique")),
        options.containsKey("minLength") ? ((Number) options.get("minLength")).intValue() : null,
        options.containsKey("maxLength") ? ((Number) options.get("maxLength")).intValue() : null,
        options.containsKey("min") ? ((Number) options.get("min")).doubleValue() : null,
        options.containsKey("max") ? ((Number) options.get("max")).doubleValue() : null,
        (String) options.get("regex"));
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isUnique() {
    return unique;
  }

  public Integer getMinLength() {
    return minLength;
  }

  public Integer getMaxLength() {
    return maxLength;
  }

  public Double getMin() {
    return min;
  }

  public Double getMax() {
    return max;
  }

  public String getRegex() {
    return regex;
  }

  /** Returns all non-null config properties as a map. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    map.put("required", required);
    map.put("unique", unique);
    if (minLength != null) map.put("minLength", minLength);
    if (maxLength != null) map.put("maxLength", maxLength);
    if (min != null) map.put("min", min);
    if (max != null) map.put("max", max);
    if (regex != null) map.put("regex", regex);
    return map;
  }
}
