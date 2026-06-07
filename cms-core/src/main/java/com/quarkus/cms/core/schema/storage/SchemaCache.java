package com.quarkus.cms.core.schema.storage;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory cache of all registered content-type and component schemas.
 *
 * <p>Registrants call {@link #putContentType(ContentTypeDefinition)} and {@link
 * #putComponent(ComponentDefinition)} after loading schemas at startup or after runtime mutations.
 * Callers use the read methods for constant-time lookups without hitting the database.
 *
 * <p>The cache is invalidated incrementally — individual entries can be removed via {@link
 * #invalidateContentType(String)} and {@link #invalidateComponent(String)}, or the entire cache can
 * be cleared with {@link #clear()}.
 */
@ApplicationScoped
public class SchemaCache {

  private final Map<String, ContentTypeDefinition> contentTypes = new ConcurrentHashMap<>();
  private final Map<String, ComponentDefinition> components = new ConcurrentHashMap<>();

  // ---- Content Types ----

  public void putContentType(ContentTypeDefinition ct) {
    contentTypes.put(ct.getUid(), ct);
  }

  public ContentTypeDefinition getContentType(String uid) {
    return contentTypes.get(uid);
  }

  public Collection<ContentTypeDefinition> getAllContentTypes() {
    return Collections.unmodifiableCollection(contentTypes.values());
  }

  public boolean hasContentType(String uid) {
    return contentTypes.containsKey(uid);
  }

  public void invalidateContentType(String uid) {
    contentTypes.remove(uid);
  }

  // ---- Components ----

  public void putComponent(ComponentDefinition component) {
    components.put(component.getUid(), component);
  }

  public ComponentDefinition getComponent(String uid) {
    return components.get(uid);
  }

  public Collection<ComponentDefinition> getAllComponents() {
    return Collections.unmodifiableCollection(components.values());
  }

  public boolean hasComponent(String uid) {
    return components.containsKey(uid);
  }

  public void invalidateComponent(String uid) {
    components.remove(uid);
  }

  // ---- Bulk ----

  public void clear() {
    contentTypes.clear();
    components.clear();
  }

  public int contentTypeCount() {
    return contentTypes.size();
  }

  public int componentCount() {
    return components.size();
  }
}
