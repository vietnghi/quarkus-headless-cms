package com.quarkus.cms.core.schema.model;

import java.time.Instant;

/**
 * A lightweight record of a schema version, used for migration tracking. Each time a content-type
 * schema is modified, a new version is recorded so that the previous state can be preserved and
 * (potentially) rolled back.
 */
public class SchemaVersion {
  private final int version;
  private final String changeDescription;
  private final Instant createdAt;
  private final String createdBy;

  public SchemaVersion(int version, String changeDescription, Instant createdAt, String createdBy) {
    this.version = version;
    this.changeDescription = changeDescription;
    this.createdAt = createdAt;
    this.createdBy = createdBy;
  }

  public int getVersion() {
    return version;
  }

  public String getChangeDescription() {
    return changeDescription;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }
}
