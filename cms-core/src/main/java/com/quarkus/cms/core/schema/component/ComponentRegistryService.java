package com.quarkus.cms.core.schema.component;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaCache;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.schema.storage.SchemaValidationException;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CDI bean that provides higher-level component lifecycle management on top of the raw {@link
 * SchemaStorageService}.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li><b>Usage tracking</b> — records which content types and other components reference each
 *       component, so you can answer "what uses this component?" and guard against unsafe deletion.
 *   <li><b>Delete guards</b> — raises {@link ComponentInUseException} when a component is still
 *       referenced.
 *   <li><b>Lifecycle events</b> — fires {@link ComponentLifecycleEvent} on register, update, and
 *       delete so observers (indexers, webhooks, cache warmers) can react.
 *   <li><b>Bulk introspection</b> — {@link #findComponentsUsedByContentType(String)} and {@link
 *       #findComponentsUsedByComponent(String)} for composition graph traversal.
 * </ul>
 *
 * <p>All mutation methods delegate persistence to {@link SchemaStorageService} and then update the
 * in-memory usage index.
 */
@ApplicationScoped
public class ComponentRegistryService {

  @Inject SchemaStorageService schemaStorage;

  @Inject SchemaCache schemaCache;

  @Inject Event<ComponentLifecycleEvent> lifecycleEvent;

  /** For testing — allows injecting a stub SchemaStorageService. */
  public void setSchemaStorageForTest(SchemaStorageService storage) {
    this.schemaStorage = storage;
  }

  /**
   * In-memory reverse-index: component UID → set of "owners" (both content-type UIDs and component
   * UIDs that reference it via COMPONENT-type fields or dynamic zone listings).
   */
  private final Map<String, Set<String>> usageIndex = new ConcurrentHashMap<>();

  void onStart(@Observes StartupEvent event) {
    rebuildUsageIndex();
    Log.infof(
        "ComponentRegistryService initialized: %d components tracked, %d usage edges",
        schemaCache.componentCount(), usageIndex.size());
  }

  // ---- Registration ----

  /**
   * Registers a component (create or update). Fires a {@link ComponentLifecycleEvent} after
   * persistence.
   */
  public ComponentDefinition register(
      ComponentDefinition comp, String changeDescription, String createdBy) {
    ComponentDefinition saved = schemaStorage.registerComponent(comp, changeDescription, createdBy);
    updateUsageIndex(saved);
    lifecycleEvent.fire(
        ComponentLifecycleEvent.registered(saved.getUid(), changeDescription, createdBy));
    Log.debugf("Component registered: %s (v%d)", saved.getUid(), schemaStorage.getVersionHistory(
        saved.getUid()).size());
    return saved;
  }

  /**
   * Deletes a component only if nothing references it. Throws {@link ComponentInUseException} if
   * the component is still referenced by other schemas.
   */
  public void delete(String uid, String reason, String deletedBy) {
    ComponentDefinition comp = schemaStorage.getComponent(uid);
    if (comp == null) {
      Log.warnf("Attempted to delete non-existent component: %s", uid);
      return;
    }

    Set<String> dependents = getDependents(uid);
    if (!dependents.isEmpty()) {
      throw new ComponentInUseException(
          uid, dependents, "Component '" + uid + "' is still referenced by: " + dependents);
    }

    schemaStorage.deleteComponent(uid);
    usageIndex.remove(uid);
    lifecycleEvent.fire(
        ComponentLifecycleEvent.deleted(uid, reason, deletedBy));
    Log.infof("Component deleted: %s", uid);
  }

  /**
   * Force-deletes a component even if it has dependents. USE WITH CAUTION — will leave dangling
   * references in the schemas that use this component.
   */
  public void forceDelete(String uid, String reason, String deletedBy) {
    schemaStorage.deleteComponent(uid);
    usageIndex.remove(uid);
    lifecycleEvent.fire(
        ComponentLifecycleEvent.deleted(uid, reason + " (force)", deletedBy));
    Log.warnf("Component force-deleted: %s (may leave dangling references)", uid);
  }

  // ---- Queries ----

  /** Returns the set of content-type and component UIDs that reference the given component. */
  public Set<String> getDependents(String componentUid) {
    return usageIndex.getOrDefault(componentUid, Collections.emptySet());
  }

  /**
   * Returns the set of content-type UIDs that directly use the given component (via COMPONENT
   * fields, dynamic zones, or the content-type's component list).
   */
  public Set<String> findContentTypesUsingComponent(String componentUid) {
    return getDependents(componentUid).stream()
        .filter(this::isContentTypeUid)
        .collect(Collectors.toSet());
  }

  /**
   * Returns the set of component UIDs that directly use the given component (via COMPONENT fields
   * or dynamic zones inside another component).
   */
  public Set<String> findComponentsUsingComponent(String componentUid) {
    return getDependents(componentUid).stream()
        .filter(uid -> !isContentTypeUid(uid))
        .collect(Collectors.toSet());
  }

  /**
   * Returns all component UIDs referenced (directly) by the fields and dynamic zones of the given
   * content type.
   */
  public Set<String> findComponentsUsedByContentType(String contentTypeUid) {
    ContentTypeDefinition ct = resolveContentType(contentTypeUid);
    if (ct == null) return Collections.emptySet();
    return collectComponentRefs(ct.getFields(), ct.getDynamicZones());
  }

  /**
   * Returns all component UIDs referenced (directly) by the fields and dynamic zones of the given
   * component.
   */
  public Set<String> findComponentsUsedByComponent(String componentUid) {
    ComponentDefinition comp = resolveComponent(componentUid);
    if (comp == null) return Collections.emptySet();
    return collectFieldComponentRefs(comp.getFields());
  }

  /**
   * Returns {@code true} if the component exists and has no dependents.
   */
  public boolean isDeletable(String componentUid) {
    return resolveComponent(componentUid) != null
        && getDependents(componentUid).isEmpty();
  }

  /**
   * Returns the transitive closure of components used by the given component UID.
   */
  public Set<String> getComponentClosure(String componentUid) {
    Set<String> visited = new HashSet<>();
    walkComponentClosure(componentUid, visited);
    visited.remove(componentUid); // exclude the root
    return visited;
  }

  // ---- Index Maintenance ----

  /** Rebuilds the entire usage index from all registered schemas. Called at startup. */
  public void rebuildUsageIndex() {
    usageIndex.clear();

    for (ContentTypeDefinition ct : schemaCache.getAllContentTypes()) {
      indexReferences(ct.getUid(), collectComponentRefs(ct.getFields(), ct.getDynamicZones()));
    }

    for (ComponentDefinition comp : schemaCache.getAllComponents()) {
      indexReferences(comp.getUid(), collectFieldComponentRefs(comp.getFields()));
    }
  }

  /** Updates the usage index for a single freshly-registered or updated component. */
  private void updateUsageIndex(ComponentDefinition comp) {
    // Remove old index entries for this component
    usageIndex.values().forEach(v -> v.remove(comp.getUid()));

    // Re-index
    Set<String> refs = collectFieldComponentRefs(comp.getFields());
    indexReferences(comp.getUid(), refs);
  }

  private void indexReferences(String ownerUid, Set<String> referencedComponentUids) {
    for (String ref : referencedComponentUids) {
      usageIndex.computeIfAbsent(ref, k -> ConcurrentHashMap.newKeySet()).add(ownerUid);
    }
  }

  // ---- Reference Collection ----

  private Set<String> collectComponentRefs(
      List<FieldDefinition> fields, List<DynamicZoneDefinition> zones) {
    Set<String> refs = new HashSet<>();

    for (FieldDefinition field : fields) {
      if (field.getType() == FieldType.COMPONENT && field.getComponent() != null) {
        refs.add(field.getComponent());
      }
    }

    for (DynamicZoneDefinition zone : zones) {
      if (zone.getComponents() != null) {
        refs.addAll(zone.getComponents());
      }
    }

    return refs;
  }

  private Set<String> collectFieldComponentRefs(List<FieldDefinition> fields) {
    Set<String> refs = new HashSet<>();
    for (FieldDefinition field : fields) {
      if (field.getType() == FieldType.COMPONENT && field.getComponent() != null) {
        refs.add(field.getComponent());
      }
    }
    return refs;
  }

  private boolean isContentTypeUid(String uid) {
    return uid != null && uid.startsWith("api::");
  }

  /**
   * Resolves a component definition from the schema cache (preferred) or the storage service. This
   * allows the registry to operate in unit tests without a full database.
   */
  private ComponentDefinition resolveComponent(String uid) {
    if (schemaCache != null) {
      ComponentDefinition cached = schemaCache.getComponent(uid);
      if (cached != null) return cached;
    }
    return schemaStorage != null ? schemaStorage.getComponent(uid) : null;
  }

  /**
   * Resolves a content type definition from the schema cache (preferred) or the storage service.
   */
  private ContentTypeDefinition resolveContentType(String uid) {
    if (schemaCache != null) {
      ContentTypeDefinition cached = schemaCache.getContentType(uid);
      if (cached != null) return cached;
    }
    return schemaStorage != null ? schemaStorage.getContentType(uid) : null;
  }

  private void walkComponentClosure(String uid, Set<String> visited) {
    if (uid == null || !visited.add(uid)) return;
    ComponentDefinition comp = resolveComponent(uid);
    if (comp == null) return;
    for (FieldDefinition field : comp.getFields()) {
      if (field.getType() == FieldType.COMPONENT && field.getComponent() != null) {
        walkComponentClosure(field.getComponent(), visited);
      }
    }
  }
}
