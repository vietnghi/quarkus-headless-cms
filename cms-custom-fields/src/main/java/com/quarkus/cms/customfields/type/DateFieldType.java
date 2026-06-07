package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Built-in date/time field types: date, datetime, timestamp, time. */
@ApplicationScoped
public class DateFieldType implements CustomFieldType {

  private static final Set<String> SUB_TYPES = Set.of("date", "datetime", "timestamp", "time");

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_LOCAL_TIME;

  @Override
  public String getTypeId() {
    return "date";
  }

  @Override
  public String getDisplayName() {
    return "Date";
  }

  @Override
  public String getCategory() {
    return "date";
  }

  @Override
  public String getDescription() {
    return "Date and time values: date, datetime, timestamp, time";
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
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' requires a date/time string value");
    }

    String subType = (String) config.getOrDefault("subType", "datetime");

    try {
      switch (subType) {
        case "date" -> LocalDate.parse(s, DATE_FMT);
        case "datetime" -> LocalDateTime.parse(s, DATETIME_FMT);
        case "time" -> LocalTime.parse(s, TIME_FMT);
        case "timestamp" -> Instant.parse(s);
        default -> LocalDateTime.parse(s, DATETIME_FMT);
      }
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Field '"
              + fieldName
              + "' is not a valid "
              + subType
              + " format (expected ISO 8601): "
              + e.getMessage());
    }
  }

  @Override
  public Object coerce(Object value) {
    if (value == null) return null;
    if (value instanceof String) return value;
    return value.toString();
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of("subType", "string (date, datetime, timestamp, time)");
  }

  /** Returns true if the given sub-type is handled by this field type. */
  public static boolean handlesSubType(String subType) {
    return SUB_TYPES.contains(subType);
  }
}
