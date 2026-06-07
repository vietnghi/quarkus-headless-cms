package com.quarkus.cms.customfields.spi;

import java.util.Map;

/**
 * SPI for server-side field hooks that run before and after saving custom field values.
 *
 * <p>Implementations can transform values before persistence (e.g. slug generation, auto-increment)
 * and perform side effects after persistence (e.g. updating denormalized counters, firing events).
 */
public interface FieldHook {

  /** Human-readable name for this hook (e.g. "Slug Generator"). */
  String getName();

  /**
   * Called before a custom field value is saved. The implementation may transform or replace the
   * value.
   *
   * @param fieldName the field name
   * @param value the current value (may be null)
   * @param entryData the full entry data for context
   * @param config hook-specific configuration from the field definition
   * @return the (possibly transformed) value to persist
   */
  default Object beforeSave(
      String fieldName, Object value, Map<String, Object> entryData, Map<String, Object> config) {
    return value;
  }

  /**
   * Called after a custom field value has been saved. Suitable for side effects.
   *
   * @param fieldName the field name
   * @param savedValue the value that was persisted
   * @param entryData the full entry data for context
   * @param config hook-specific configuration from the field definition
   */
  default void afterSave(
      String fieldName,
      Object savedValue,
      Map<String, Object> entryData,
      Map<String, Object> config) {
    // no-op
  }
}
