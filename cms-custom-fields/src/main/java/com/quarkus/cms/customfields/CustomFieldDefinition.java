package com.quarkus.cms.customfields;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Defines a custom metadata field for a given content type.
 *
 * <p>Custom fields are additional fields that content managers can add to content types beyond the
 * schema-defined fields. They are stored in the entry's JSONB data under a dedicated {@code
 * _custom} key.
 *
 * <p>Example: an article content type could have a custom "featuredImage" field that is not part of
 * the base schema.
 */
@Entity
@Table(
    name = "cms_custom_field_defs",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_custom_field_content_type_name",
          columnNames = {"content_type", "field_name"})
    })
public class CustomFieldDefinition extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** Content type UID this field belongs to (e.g. "api::article.article"). */
  @Column(name = "content_type", nullable = false, length = 100)
  public String contentType;

  /** Field name (kebab-case, used as JSON key under _custom). */
  @Column(name = "field_name", nullable = false, length = 100)
  public String fieldName;

  /** Human-readable label for display in the admin UI. */
  @Column(name = "label", nullable = false, length = 200)
  public String label;

  /**
   * Field type: text, number, boolean, date, json, select, media. Determines value validation
   * rules.
   */
  @Column(name = "field_type", nullable = false, length = 20)
  public String fieldType = "text";

  /** Default value for the field when no value is set. */
  @Column(name = "default_value", length = 1000)
  public String defaultValue;

  /** Whether this field must have a value. */
  @Column(name = "is_required", nullable = false)
  public boolean required = false;

  /** Placeholder hint for the admin UI input. */
  @Column(name = "placeholder", length = 500)
  public String placeholder;

  /** For select fields: comma-separated list of valid options. */
  @Column(name = "options", length = 2000)
  public String options;

  /** Display order in the admin UI (lower = first). */
  @Column(name = "sort_order")
  public Integer sortOrder = 0;

  /** Description of this field (for admin UI help text). */
  @Column(name = "description", length = 2000)
  public String description;

  /**
   * Configuration options for this field: verification rules, type-specific settings, etc. Stored
   * as JSONB for flexibility.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config", columnDefinition = "jsonb")
  public Map<String, Object> config;

  /** Timestamps. */
  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public void setDescription(String description) {
    this.description = description;
  }

  // ---- Helper Finders ----

  /** Returns all custom field definitions for a content type, ordered by sort_order. */
  public static java.util.List<CustomFieldDefinition> findByContentType(String contentType) {
    return list("contentType = ?1 order by sortOrder asc, id asc", contentType);
  }

  /** Finds a specific custom field by content type and field name. */
  public static CustomFieldDefinition findByContentTypeAndName(
      String contentType, String fieldName) {
    return find("contentType = ?1 and fieldName = ?2", contentType, fieldName).firstResult();
  }

  /** Deletes all custom field definitions for a content type. */
  public static long deleteByContentType(String contentType) {
    return delete("contentType", contentType);
  }
}
