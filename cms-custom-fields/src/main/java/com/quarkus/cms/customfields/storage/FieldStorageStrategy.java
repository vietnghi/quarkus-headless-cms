package com.quarkus.cms.customfields.storage;

import com.quarkus.cms.core.domain.CmsEntry;

import java.util.Map;

/**
 * SPI for custom field storage strategies.
 *
 * <p>Different field types may use different storage backends. The default strategy stores values
 * in the entry's JSONB data column. Alternative strategies could store in separate tables, external
 * services, etc.
 */
public interface FieldStorageStrategy {

  /** Unique identifier for this storage strategy. */
  String getName();

  /**
   * Reads a custom field value from storage.
   *
   * @param entry the content entry
   * @param fieldName the field name
   * @return the field value, or null if not set
   */
  Object readValue(CmsEntry entry, String fieldName);

  /**
   * Writes a custom field value to storage.
   *
   * @param entry the content entry
   * @param fieldName the field name
   * @param value the value to store
   */
  void writeValue(CmsEntry entry, String fieldName, Object value);

  /**
   * Removes a custom field value from storage.
   *
   * @param entry the content entry
   * @param fieldName the field name
   */
  void removeValue(CmsEntry entry, String fieldName);

  /**
   * Reads all custom field values for an entry.
   *
   * @param entry the content entry
   * @return map of field names to values
   */
  Map<String, Object> readAllValues(CmsEntry entry);

  /**
   * Removes all custom field values for an entry (e.g., when deleting).
   *
   * @param entry the content entry
   */
  void removeAllValues(CmsEntry entry);
}
