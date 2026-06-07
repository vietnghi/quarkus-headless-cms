package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;

/** Built-in numeric field types: integer, biginteger, float, decimal. */
@ApplicationScoped
public class NumberFieldType implements CustomFieldType {

  private static final Set<String> SUB_TYPES = Set.of("integer", "biginteger", "float", "decimal");

  @Override
  public String getTypeId() {
    return "number";
  }

  @Override
  public String getDisplayName() {
    return "Number";
  }

  @Override
  public String getCategory() {
    return "number";
  }

  @Override
  public String getDescription() {
    return "Numeric values: integer, biginteger, float, decimal";
  }

  @Override
  public Class<?> getValueType() {
    return Number.class;
  }

  @Override
  public Object getDefaultValue() {
    return 0;
  }

  @Override
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;

    String subType = (String) config.getOrDefault("subType", "float");
    boolean isInteger = "integer".equals(subType) || "biginteger".equals(subType);

    if (!(value instanceof Number n)) {
      throw new IllegalArgumentException("Field '" + fieldName + "' requires a numeric value");
    }

    // Integer sub-types must not have fractional parts
    if (isInteger && n.doubleValue() != Math.floor(n.doubleValue())) {
      throw new IllegalArgumentException("Field '" + fieldName + "' requires an integer value");
    }

    // Range constraints
    double d = n.doubleValue();
    if (config.containsKey("min")) {
      double min = ((Number) config.get("min")).doubleValue();
      if (d < min) {
        throw new IllegalArgumentException("Field '" + fieldName + "' must be at least " + min);
      }
    }
    if (config.containsKey("max")) {
      double max = ((Number) config.get("max")).doubleValue();
      if (d > max) {
        throw new IllegalArgumentException("Field '" + fieldName + "' must be at most " + max);
      }
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    if (value instanceof Number) return value;
    if (value instanceof String s) {
      try {
        if (s.contains(".")) {
          return Double.parseDouble(s);
        } else {
          return Long.parseLong(s);
        }
      } catch (NumberFormatException e) {
        return value; // return as-is, validation will catch it
      }
    }
    return value;
  }

  @Override
  public boolean supportsUnique() {
    return true;
  }

  @Override
  public boolean supportsRangeConstraints() {
    return true;
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of(
        "subType", "string (integer, biginteger, float, decimal)",
        "min", "number - minimum value",
        "max", "number - maximum value");
  }

  /** Returns true if the given sub-type is handled by this field type. */
  public static boolean handlesSubType(String subType) {
    return SUB_TYPES.contains(subType);
  }
}
