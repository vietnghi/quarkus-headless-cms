package com.quarkus.cms.customfields.storage;

import com.quarkus.cms.core.domain.CmsEntry;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default storage strategy: stores custom field values in the CmsEntry's existing {@code data}
 * JSONB column under the reserved {@code _custom} key.
 *
 * <p>This is a zero-migration approach that leverages the existing JSONB column without needing
 * additional tables or schema changes.
 */
@ApplicationScoped
public class JsonbFieldStorageStrategy implements FieldStorageStrategy {

  private static final String CUSTOM_KEY = "_custom";

  @Override
  public String getName() {
    return "jsonb";
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object readValue(CmsEntry entry, String fieldName) {
    Map<String, Object> custom = getCustomMap(entry);
    return custom.get(fieldName);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void writeValue(CmsEntry entry, String fieldName, Object value) {
    Map<String, Object> custom =
        (Map<String, Object>) entry.data.computeIfAbsent(CUSTOM_KEY, k -> new LinkedHashMap<>());
    custom.put(fieldName, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void removeValue(CmsEntry entry, String fieldName) {
    Object custom = entry.data.get(CUSTOM_KEY);
    if (custom instanceof Map) {
      ((Map<String, Object>) custom).remove(fieldName);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> readAllValues(CmsEntry entry) {
    Object custom = entry.data.get(CUSTOM_KEY);
    if (custom instanceof Map) {
      return (Map<String, Object>) custom;
    }
    return Map.of();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void removeAllValues(CmsEntry entry) {
    entry.data.remove(CUSTOM_KEY);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getCustomMap(CmsEntry entry) {
    Object custom = entry.data.get(CUSTOM_KEY);
    if (custom instanceof Map) {
      return (Map<String, Object>) custom;
    }
    return Map.of();
  }
}
