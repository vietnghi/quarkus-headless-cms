package com.quarkus.cms.graphql.model;

import com.quarkus.cms.core.domain.CmsEntry;

import org.eclipse.microprofile.graphql.Ignore;

import java.time.Instant;
import java.util.Map;

/**
 * GraphQL type representing a content entry with flattened metadata and dynamic JSONB data.
 *
 * <p>The {@code data} field contains the content-type-specific fields as a dynamic JSON object.
 * This avoids needing to generate per-content-type GraphQL types at schema build time.
 */
public class Entry {

  private final Long id;
  private final String documentId;
  private final String contentType;
  private final String locale;
  private final String status;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant publishedAt;
  private final Map<String, Object> data;

  public Entry(CmsEntry entity) {
    this.id = entity.id;
    this.documentId = entity.documentId;
    this.contentType = entity.contentType;
    this.locale = entity.locale;
    this.status = entity.status;
    this.createdAt = entity.createdAt;
    this.updatedAt = entity.updatedAt;
    this.publishedAt = entity.publishedAt;
    this.data = entity.data != null ? entity.data : Map.of();
  }

  public Long getId() {
    return id;
  }

  public String getDocumentId() {
    return documentId;
  }

  public String getContentType() {
    return contentType;
  }

  public String getLocale() {
    return locale;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  @Ignore
  public Map<String, Object> getData() {
    return data;
  }
}
