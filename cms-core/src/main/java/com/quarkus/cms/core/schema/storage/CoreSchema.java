package com.quarkus.cms.core.schema.storage;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.SchemaVersion;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Stores a content-type or component schema definition as a JSONB document alongside lookup
 * metadata.
 *
 * <p>Each row represents one schema definition — either a content type, a component, or a dynamic
 * zone configuration. The {@code kind} column discriminates between {@code content_type} and {@code
 * component}. The full schema body is stored in the {@code definition} JSONB column, which is
 * deserialized back into {@link ContentTypeDefinition} or {@link ComponentDefinition} by the
 * service layer.
 *
 * <p>Version history and backup are maintained in the {@code version_history} and {@code
 * previous_definition} columns respectively.
 */
@Entity
@Table(
    name = "core_schema",
    indexes = {
      @Index(name = "idx_core_schema_uid", columnList = "uid"),
      @Index(name = "idx_core_schema_kind", columnList = "kind")
    })
public class CoreSchema extends PanacheEntityBase {

  private static volatile ObjectMapper MAPPER;

  private static ObjectMapper mapper() {
    if (MAPPER == null) {
      synchronized (CoreSchema.class) {
        if (MAPPER == null) {
          MAPPER = JsonMapper.builder().build();
        }
      }
    }
    return MAPPER;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** Unique identifier, e.g. "api::article.article" or "shared.seo" */
  @Column(nullable = false, length = 150)
  public String uid;

  /** "content_type" or "component" */
  @Column(nullable = false, length = 20)
  public String kind;

  /** The full schema definition as JSON */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "definition", nullable = false)
  public Map<String, Object> definition;

  /** Schema version number, monotonically increasing */
  @Column(nullable = false)
  public int version;

  /** Version history: ordered list of {version, changeDescription, createdAt, createdBy} */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "version_history")
  public List<Map<String, Object>> versionHistory = new ArrayList<>();

  /** Snapshot of the previous definition for safe rollback */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "previous_definition")
  public Map<String, Object> previousDefinition;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  // ---- Lifecycle ----

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  // ---- Helper Finders ----

  /** Finds a schema row by UID. */
  public static CoreSchema findByUid(String uid) {
    return find("uid", uid).firstResult();
  }

  /** Lists all schemas of the given kind. */
  public static List<CoreSchema> findByKind(String kind) {
    return list("kind", kind);
  }

  /** Lists all content-type schemas. */
  public static List<CoreSchema> findContentTypes() {
    return findByKind("content_type");
  }

  /** Lists all component schemas. */
  public static List<CoreSchema> findComponents() {
    return findByKind("component");
  }

  /** Deletes a schema row by UID. */
  public static long deleteByUid(String uid) {
    return delete("uid", uid);
  }

  // ---- Serialisation helpers ----

  /** Deserialises the {@code definition} JSONB column back into a {@link ContentTypeDefinition}. */
  public ContentTypeDefinition toContentTypeDefinition() {
    return mapper().convertValue(definition, ContentTypeDefinition.class);
  }

  /** Deserialises the {@code definition} JSONB column back into a {@link ComponentDefinition}. */
  public ComponentDefinition toComponentDefinition() {
    return mapper().convertValue(definition, ComponentDefinition.class);
  }

  /** Serialises a {@link ContentTypeDefinition} into the definition map. */
  public void setContentTypeDefinition(ContentTypeDefinition ct) {
    this.definition = mapper().convertValue(ct, Map.class);
  }

  /** Serialises a {@link ComponentDefinition} into the definition map. */
  public void setComponentDefinition(ComponentDefinition comp) {
    this.definition = mapper().convertValue(comp, Map.class);
  }

  /** Appends a version record to the version history. */
  public void addVersionRecord(int version, String changeDescription, String createdBy) {
    Map<String, Object> record =
        Map.of(
            "version",
            version,
            "changeDescription",
            changeDescription != null ? changeDescription : "",
            "createdAt",
            Instant.now().toString(),
            "createdBy",
            createdBy != null ? createdBy : "system");
    if (versionHistory == null) {
      versionHistory = new ArrayList<>();
    }
    versionHistory.add(record);
  }

  /** Parses the version history into {@link SchemaVersion} objects. */
  public List<SchemaVersion> getVersionHistoryAsObjects() {
    if (versionHistory == null) return List.of();
    return versionHistory.stream()
        .map(
            m ->
                new SchemaVersion(
                    (Integer) m.get("version"),
                    (String) m.getOrDefault("changeDescription", ""),
                    Instant.parse((String) m.getOrDefault("createdAt", Instant.EPOCH.toString())),
                    (String) m.getOrDefault("createdBy", "system")))
        .toList();
  }

  /** Saves the current definition as the previous definition (backup before migration). */
  public void backupCurrentDefinition() {
    this.previousDefinition =
        this.definition != null
            ? mapper().convertValue(definition, Map.class)
            : null;
  }
}
