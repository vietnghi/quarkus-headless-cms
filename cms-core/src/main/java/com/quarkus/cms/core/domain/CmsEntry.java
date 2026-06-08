package com.quarkus.cms.core.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Convert;

/**
 * Core content entry entity implementing the hybrid document-on-RDBMS schema.
 *
 * <p>Standard metadata fields (document ID, content type, locale, status, timestamps) are stored as
 * physical columns for efficient querying. Custom content-type fields are serialized into the
 * {@code data} JSONB column, enabling dynamic schemas without requiring DDL changes or runtime
 * bytecode generation.
 */
@Entity
@Table(
    name = "cms_entries",
    indexes = {
      @Index(name = "idx_entries_doc_id", columnList = "document_id"),
      @Index(name = "idx_entries_content_type_status", columnList = "content_type, status"),
      @Index(name = "idx_entries_locale", columnList = "locale")
    })
public class CmsEntry extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "document_id", nullable = false, length = 36)
  public String documentId;

  @Column(name = "content_type", nullable = false, length = 100)
  public String contentType;

  @Column(name = "locale", nullable = false, length = 10)
  public String locale = "en";

  @Column(name = "status", nullable = false, length = 15)
  public String status = "draft";

  @Column(name = "version_number", nullable = false)
  public Integer versionNumber = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  @Column(name = "published_at")
  public Instant publishedAt;

  @Column(name = "created_by_id")
  public Long createdById;

  @Column(name = "updated_by_id")
  public Long updatedById;

  @Column(name = "published_by_id")
  public Long publishedById;

  @Convert(converter = JsonMapConverter.class)
  @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
  @Column(name = "data")
  public Map<String, Object> data = new HashMap<>();

  // ---- Helper Finders ----

  /** Finds a single entry by document ID, status, and locale. */
  public static CmsEntry findByDocumentId(String documentId, String status, String locale) {
    return find("documentId = ?1 and status = ?2 and locale = ?3", documentId, status, locale)
        .firstResult();
  }

  /** Finds all entries for a given content type, status, and locale. */
  public static java.util.List<CmsEntry> findByContentType(
      String contentType, String status, String locale) {
    return list("contentType = ?1 and status = ?2 and locale = ?3", contentType, status, locale);
  }

  /** Finds all versions of a document across locales and statuses. */
  public static java.util.List<CmsEntry> findDocumentVersions(String documentId) {
    return list("documentId", documentId);
  }

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  /**
   * Returns a map representation of this entry suitable for API response inclusion,
   * including all metadata and content-type data fields.
   *
   * <p>Used by the Relations API and populate machinery to embed resolved relation
   * target entries into the parent entry's response.
   *
   * @return populated data map with documentId, contentType, locale, status, and data fields
   */
  public Map<String, Object> getPopulatedData() {
    Map<String, Object> result = new HashMap<>();
    result.put("documentId", documentId);
    result.put("contentType", contentType);
    result.put("locale", locale);
    result.put("status", status);
    if (versionNumber != null) {
      result.put("versionNumber", versionNumber);
    }
    if (id != null) {
      result.put("id", id);
    }
    if (data != null) {
      result.putAll(data);
    }
    return result;
  }
}
