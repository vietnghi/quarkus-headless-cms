package com.quarkus.cms.customfields.spi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDI bean that manages registration and discovery of custom field types.
 *
 * <p>Built-in field types are registered at application startup. Custom field types provided by
 * plugins can be injected via CDI and are auto-discovered.
 */
@ApplicationScoped
public class CustomFieldTypeRegistry {

  private final Map<String, CustomFieldType> types = new ConcurrentHashMap<>();

  @Inject Instance<CustomFieldType> discoveredTypes;

  /** Initializes the registry with all discovered CDI beans. */
  public void initialize() {
    for (CustomFieldType type : discoveredTypes) {
      register(type);
    }
  }

  /**
   * Programmatically registers a custom field type.
   *
   * @param type the field type to register
   * @throws IllegalArgumentException if a type with the same ID is already registered
   */
  public void register(CustomFieldType type) {
    String typeId = type.getTypeId();
    if (types.containsKey(typeId)) {
      throw new IllegalArgumentException(
          "Custom field type '" + typeId + "' is already registered");
    }
    types.put(typeId, type);
  }

  /** Registers a type, silently replacing any existing registration with the same ID. */
  public void registerOrReplace(CustomFieldType type) {
    types.put(type.getTypeId(), type);
  }

  /** Unregisters a custom field type. */
  public void unregister(String typeId) {
    types.remove(typeId);
  }

  /** Returns the custom field type for the given ID, or null if not found. */
  public CustomFieldType getType(String typeId) {
    return types.get(typeId);
  }

  /** Returns all registered custom field types. */
  public Collection<CustomFieldType> getAllTypes() {
    return Collections.unmodifiableCollection(types.values());
  }

  /** Returns all registered type IDs. */
  public Set<String> getTypeIds() {
    return Collections.unmodifiableSet(types.keySet());
  }

  /** Returns field types belonging to a specific category. */
  public List<CustomFieldType> getTypesByCategory(String category) {
    return types.values().stream().filter(t -> category.equals(t.getCategory())).toList();
  }

  /** Checks if a type ID is registered. */
  public boolean hasType(String typeId) {
    return types.containsKey(typeId);
  }

  /** Returns the number of registered types. */
  public int size() {
    return types.size();
  }
}
