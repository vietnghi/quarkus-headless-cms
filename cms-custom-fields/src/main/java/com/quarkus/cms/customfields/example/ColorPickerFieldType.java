package com.quarkus.cms.customfields.example;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Example custom field type: Color Picker.
 *
 * <p>Stores a hex color code (e.g., #ff6633) and validates the format.
 */
@ApplicationScoped
public class ColorPickerFieldType implements CustomFieldType {

  private static final String HEX_COLOR_REGEX = "^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$";

  @Override
  public String getTypeId() {
    return "color-picker";
  }

  @Override
  public String getDisplayName() {
    return "Color Picker";
  }

  @Override
  public String getCategory() {
    return "custom";
  }

  @Override
  public String getDescription() {
    return "Hex color picker with optional alpha channel (e.g., #ff6633, #ff6633cc)";
  }

  @Override
  public Class<?> getValueType() {
    return String.class;
  }

  @Override
  public Object getDefaultValue() {
    return "#000000";
  }

  @Override
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' requires a hex color string (e.g., #ff6633)");
    }
    if (!s.matches(HEX_COLOR_REGEX)) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' must be a valid hex color (#RGB, #RRGGBB, or #RRGGBBAA)");
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    String s = value.toString().trim();
    if (!s.startsWith("#")) {
      s = "#" + s;
    }
    return s.toUpperCase();
  }

  @Override
  public boolean supportsRegex() {
    return true;
  }

  @Override
  public Map<String, Object> getPluginOptions() {
    return Map.of(
        "showAlpha",
        false,
        "presetColors",
        new String[] {"#FF0000", "#00FF00", "#0000FF", "#000000", "#FFFFFF"});
  }
}
