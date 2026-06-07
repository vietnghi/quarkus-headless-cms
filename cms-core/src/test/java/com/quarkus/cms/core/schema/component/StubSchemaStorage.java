package com.quarkus.cms.core.schema.component;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.SchemaVersion;
import com.quarkus.cms.core.schema.storage.SchemaCache;
import com.quarkus.cms.core.schema.storage.SchemaValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory stub of {@link com.quarkus.cms.core.schema.storage.SchemaStorageService} that works
 * without Hibernate/Panache. Used for unit tests of the component services.
 */
class StubSchemaStorage {

  private final SchemaCache cache;
  private final ConcurrentMap<String, Integer> versions = new ConcurrentHashMap<>();

  StubSchemaStorage(SchemaCache cache) {
    this.cache = cache;
  }

  SchemaCache getCache() {
    return cache;
  }

  void registerComponent(ComponentDefinition comp) {
    // Validate basic requirements (mimicking SchemaValidator without DB)
    if (comp.getUid() == null || comp.getUid().isBlank()) {
      throw new SchemaValidationException("Component UID is required");
    }
    // Only check component field references against what's already cached
    for (var field : comp.getFields()) {
      if (field.getType() == null) continue;
      if (field.getType() == com.quarkus.cms.core.schema.model.FieldType.COMPONENT) {
        String ref = field.getComponent();
        if (ref != null && !ref.isBlank() && !cache.hasComponent(ref)) {
          throw new SchemaValidationException(
              "Invalid component '" + comp.getUid() + "': [Field '" + field.getName()
                  + "' in " + comp.getUid() + ": references unknown component '" + ref + "']");
        }
      }
    }
    cache.putComponent(comp);
    versions.merge(comp.getUid(), 1, (old, v) -> old + 1);
  }

  void registerContentType(ContentTypeDefinition ct) {
    cache.putContentType(ct);
    versions.merge(ct.getUid(), 1, (old, v) -> old + 1);
  }

  ComponentDefinition getComponent(String uid) {
    return cache.getComponent(uid);
  }

  void deleteComponent(String uid) {
    cache.invalidateComponent(uid);
    versions.remove(uid);
  }

  ContentTypeDefinition getContentType(String uid) {
    return cache.getContentType(uid);
  }

  List<SchemaVersion> getVersionHistory(String uid) {
    return List.of();
  }

  List<ComponentDefinition> getAllComponents() {
    return List.copyOf(cache.getAllComponents());
  }

  List<ContentTypeDefinition> getAllContentTypes() {
    return List.copyOf(cache.getAllContentTypes());
  }

  int getComponentCount() {
    return cache.componentCount();
  }
}
