package com.quarkus.cms.customfields;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.*;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity for storing custom field values in a separate database table.
 *
 * <p>Used by {@link com.quarkus.cms.customfields.storage.SeparateTableFieldStorageStrategy} when
 * the JSONB storage approach is not suitable. Each row stores one custom field value for one entry.
 */
@Entity
@Table(
    name = "cms_custom_field_values",
    indexes = {
      @Index(name = "idx_cfv_entry_id", columnList = "entry_id"),
      @Index(name = "idx_cfv_content_type", columnList = "content_type"),
      @Index(name = "idx_cfv_entry_field", columnList = "entry_id, field_name", unique = true)
    })
public class CustomFieldValue extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** The entry this value belongs to. */
  @Column(name = "entry_id", nullable = false)
  public Long entryId;

  /** Content type UID for fast querying. */
  @Column(name = "content_type", nullable = false, length = 100)
  public String contentType;

  /** The field name (kebab-case). */
  @Column(name = "field_name", nullable = false, length = 100)
  public String fieldName;

  /** The field value, stored as JSONB for flexibility. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "value_json", columnDefinition = "jsonb")
  public Object valueJson;

  public Object getValue() {
    return valueJson;
  }

  public void setValue(Object value) {
    this.valueJson = value;
  }

  // ---- Finders ----

  public static CustomFieldValue findByEntryAndField(Long entryId, String fieldName) {
    return find("entryId = ?1 and fieldName = ?2", entryId, fieldName).firstResult();
  }

  public static List<CustomFieldValue> findByEntryId(Long entryId) {
    return list("entryId", entryId);
  }

  public static List<CustomFieldValue> findByContentType(String contentType) {
    return list("contentType", contentType);
  }

  public static long deleteByContentType(String contentType) {
    return delete("contentType", contentType);
  }

  public static long deleteByEntryId(Long entryId) {
    return delete("entryId", entryId);
  }
}
