package com.quarkus.cms.rest.dto;

import com.quarkus.cms.core.domain.CmsEntry;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Strapi v5-compatible content entry representation.
 *
 * <p>Flattens the CmsEntry entity into the Strapi Content API response shape, un-nesting the
 * JSONB {@code data} payload into top-level fields alongside standard metadata.
 */
@Schema(
    name = "ContentEntry",
    description = "Strapi-compatible content entry with metadata and dynamic content-type fields")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentEntryDto {

  private Long id;
  private String documentId;
  private String locale;
  private String status;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant publishedAt;
  private Long createdById;
  private Long updatedById;
  private Long publishedById;

  @JsonIgnore private Map<String, Object> dataFields = new HashMap<>();

  /** Converts a CmsEntry entity to a Strapi-compatible DTO. */
  public static ContentEntryDto from(CmsEntry entry) {
    ContentEntryDto dto = new ContentEntryDto();
    dto.id = entry.id;
    dto.documentId = entry.documentId;
    dto.locale = entry.locale;
    dto.status = entry.status;
    dto.createdAt = entry.createdAt;
    dto.updatedAt = entry.updatedAt;
    dto.publishedAt = entry.publishedAt;
    dto.createdById = entry.createdById;
    dto.updatedById = entry.updatedById;
    dto.publishedById = entry.publishedById;

    // Flatten JSONB data fields into top-level
    if (entry.data != null) {
      dto.dataFields.putAll(entry.data);
    }
    return dto;
  }

  // ---- Standard accessors ----

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public Long getCreatedById() {
    return createdById;
  }

  public void setCreatedById(Long createdById) {
    this.createdById = createdById;
  }

  public Long getUpdatedById() {
    return updatedById;
  }

  public void setUpdatedById(Long updatedById) {
    this.updatedById = updatedById;
  }

  public Long getPublishedById() {
    return publishedById;
  }

  public void setPublishedById(Long publishedById) {
    this.publishedById = publishedById;
  }

  // ---- Dynamic field accessors ----

  @JsonAnyGetter
  public Map<String, Object> getDataFields() {
    return dataFields;
  }

  @JsonAnySetter
  public void setDataField(String key, Object value) {
    dataFields.put(key, value);
  }
}
