package com.quarkus.cms.core.schema.storage;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.validation.SchemaValidator;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Central service for persisting, loading, caching, and versioning content-type and component
 * schema definitions.
 *
 * <p>All mutations go through this service to ensure consistent cache invalidation, schema backup,
 * and version tracking.
 */
@ApplicationScoped
public class SchemaStorageService {

  @Inject SchemaCache cache;

  private SchemaValidator validator;

  /**
   * Loads ALL schemas from the database into the in-memory cache. Called at application startup.
   */
  @Transactional
  public void loadAll() {
    cache.clear();

    List<CoreSchema> rows = CoreSchema.listAll();
    Log.infof("Loading %d schema definitions from database...", rows.size());

    int ctCount = 0;
    int compCount = 0;

    for (CoreSchema row : rows) {
      try {
        if ("content_type".equals(row.kind)) {
          ContentTypeDefinition ct = row.toContentTypeDefinition();
          cache.putContentType(ct);
          ctCount++;
        } else if ("component".equals(row.kind)) {
          ComponentDefinition comp = row.toComponentDefinition();
          cache.putComponent(comp);
          compCount++;
        }
      } catch (Exception e) {
        Log.errorf(
            "Failed to deserialize schema %s (kind=%s): %s", row.uid, row.kind, e.getMessage());
      }
    }

    // Now that all schemas are loaded, run cross-reference validation
    validator = new SchemaValidator(cache);
    Log.infof("Schema cache loaded: %d content types, %d components", ctCount, compCount);
  }

  // ---- Content Type Operations ----

  /**
   * Registers (creates or updates) a content-type definition. Backs up the current definition
   * before overwriting it and increments the version.
   */
  @Transactional
  public ContentTypeDefinition registerContentType(
      ContentTypeDefinition ct, String changeDescription, String createdBy) {
    validateContentType(ct);

    CoreSchema row = CoreSchema.findByUid(ct.getUid());
    boolean isNew = (row == null);

    if (isNew) {
      row = new CoreSchema();
      row.uid = ct.getUid();
      row.kind = "content_type";
      row.version = 1;
    } else {
      row.backupCurrentDefinition();
      row.version++;
    }

    row.setContentTypeDefinition(ct);
    row.addVersionRecord(row.version, changeDescription, createdBy);

    if (isNew) {
      row.persist();
    } // else: Panache auto-flushes on managed entity

    cache.putContentType(ct);
    Log.infof("Registered content type: %s (v%d)", ct.getUid(), row.version);
    return ct;
  }

  /** Deletes a content-type schema (and its cached entry). */
  @Transactional
  public void deleteContentType(String uid) {
    CoreSchema.deleteByUid(uid);
    cache.invalidateContentType(uid);
    Log.infof("Deleted content type: %s", uid);
  }

  /** Retrieves a content-type definition from cache (or DB fallback). */
  public ContentTypeDefinition getContentType(String uid) {
    ContentTypeDefinition cached = cache.getContentType(uid);
    if (cached != null) return cached;

    // fallback: load from DB and update cache
    CoreSchema row = CoreSchema.findByUid(uid);
    if (row == null) return null;
    if (!"content_type".equals(row.kind)) return null;
    ContentTypeDefinition ct = row.toContentTypeDefinition();
    cache.putContentType(ct);
    return ct;
  }

  public List<ContentTypeDefinition> getAllContentTypes() {
    return List.copyOf(cache.getAllContentTypes());
  }

  // ---- Component Operations ----

  @Transactional
  public ComponentDefinition registerComponent(
      ComponentDefinition comp, String changeDescription, String createdBy) {
    validateComponent(comp);

    CoreSchema row = CoreSchema.findByUid(comp.getUid());
    boolean isNew = (row == null);

    if (isNew) {
      row = new CoreSchema();
      row.uid = comp.getUid();
      row.kind = "component";
      row.version = 1;
    } else {
      row.backupCurrentDefinition();
      row.version++;
    }

    row.setComponentDefinition(comp);
    row.addVersionRecord(row.version, changeDescription, createdBy);

    if (isNew) {
      row.persist();
    }

    cache.putComponent(comp);
    Log.infof("Registered component: %s (v%d)", comp.getUid(), row.version);
    return comp;
  }

  @Transactional
  public void deleteComponent(String uid) {
    CoreSchema.deleteByUid(uid);
    cache.invalidateComponent(uid);
    Log.infof("Deleted component: %s", uid);
  }

  public ComponentDefinition getComponent(String uid) {
    ComponentDefinition cached = cache.getComponent(uid);
    if (cached != null) return cached;

    CoreSchema row = CoreSchema.findByUid(uid);
    if (row == null) return null;
    if (!"component".equals(row.kind)) return null;
    ComponentDefinition comp = row.toComponentDefinition();
    cache.putComponent(comp);
    return comp;
  }

  public List<ComponentDefinition> getAllComponents() {
    return List.copyOf(cache.getAllComponents());
  }

  // ---- Validation ----

  private void validateContentType(ContentTypeDefinition ct) {
    if (validator == null) validator = new SchemaValidator(cache);
    List<String> errors = validator.validateContentType(ct);
    if (!errors.isEmpty()) {
      throw new SchemaValidationException("Invalid content type '" + ct.getUid() + "': " + errors);
    }
  }

  private void validateComponent(ComponentDefinition comp) {
    if (validator == null) validator = new SchemaValidator(cache);
    List<String> errors = validator.validateComponent(comp);
    if (!errors.isEmpty()) {
      throw new SchemaValidationException("Invalid component '" + comp.getUid() + "': " + errors);
    }
  }

  /** Returns the version history for a schema. */
  public List<com.quarkus.cms.core.schema.model.SchemaVersion> getVersionHistory(String uid) {
    CoreSchema row = CoreSchema.findByUid(uid);
    if (row == null) return List.of();
    return row.getVersionHistoryAsObjects();
  }

  /** Rolls back a schema to its previous definition. */
  @Transactional
  public ContentTypeDefinition rollbackContentType(String uid, String reason, String createdBy) {
    CoreSchema row = CoreSchema.findByUid(uid);
    if (row == null) throw new IllegalArgumentException("Schema not found: " + uid);
    if (row.previousDefinition == null)
      throw new IllegalStateException("No previous definition to roll back to: " + uid);

    // Swap current and previous
    java.util.Map<String, Object> current = row.definition;
    row.definition = row.previousDefinition;
    row.previousDefinition = current;
    row.version++;
    row.addVersionRecord(
        row.version, "Rollback: " + (reason != null ? reason : "manual"), createdBy);

    ContentTypeDefinition ct = row.toContentTypeDefinition();
    cache.putContentType(ct);
    Log.infof("Rolled back content type: %s to v%d", uid, row.version);
    return ct;
  }
}
