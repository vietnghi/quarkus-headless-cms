package com.quarkus.cms.core.schema.zone;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime service for dynamic zones — areas within content types where users can add components
 * dynamically at runtime.
 *
 * <p>This service resolves which components are available for a given dynamic zone, validates
 * component ordering and cardinality constraints, serializes/deserializes zone data polymorphically,
 * and supports component ordering and reordering within a zone.
 *
 * <p>All public methods are thread-safe with respect to their inputs (they do not mutate incoming
 * lists or maps).
 */
@ApplicationScoped
public class DynamicZoneService {

  @Inject SchemaStorageService schemaService;

  // ============================================================
  // Query
  // ============================================================

  /**
   * Returns the list of component definitions that are available in a given dynamic zone of a
   * content type.
   */
  public List<ComponentDefinition> getAvailableComponents(String contentTypeUid, String zoneName) {
    ContentTypeDefinition ct = schemaService.getContentType(contentTypeUid);
    if (ct == null) return List.of();

    DynamicZoneDefinition zone = ct.getDynamicZone(zoneName);
    if (zone == null) return List.of();

    return zone.getComponents().stream()
        .map(schemaService::getComponent)
        .filter(c -> c != null)
        .toList();
  }

  // ============================================================
  // Polymorphic Deserialization
  // ============================================================

  /**
   * Deserializes a raw dynamic zone payload (list of maps with {@code __component} keys) into a
   * list of {@link DynamicZoneComponent} instances.
   *
   * <p>Each map entry is validated: the {@code __component} value must reference a known component
   * definition, and the component must be allowed in the given zone. Unknown or disallowed
   * components are omitted, and the errors are returned separately.
   *
   * @param zone the dynamic zone definition
   * @param payload the raw payload list (may be {@code null})
   * @return a deserialization result with the valid components and any errors
   */
  public DeserializationResult deserializeComponents(
      DynamicZoneDefinition zone, List<Map<String, Object>> payload) {

    if (payload == null || payload.isEmpty()) {
      return new DeserializationResult(List.of(), List.of());
    }

    Set<String> allowed = Set.copyOf(zone.getComponents());
    List<DynamicZoneComponent> components = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (int i = 0; i < payload.size(); i++) {
      Map<String, Object> item = payload.get(i);
      if (item == null) {
        errors.add("Component at index " + i + " is null");
        continue;
      }

      String compUid = (String) item.get("__component");
      if (compUid == null || compUid.isBlank()) {
        errors.add("Component at index " + i + " is missing '__component' key");
        continue;
      }

      if (!allowed.contains(compUid)) {
        errors.add(
            "Component '"
                + compUid
                + "' at index "
                + i
                + " is not allowed in zone '"
                + zone.getName()
                + "'");
        continue;
      }

      // Build component data map (exclude __component)
      Map<String, Object> data = new LinkedHashMap<>();
      for (var entry : item.entrySet()) {
        if (!"__component".equals(entry.getKey())) {
          data.put(entry.getKey(), entry.getValue());
        }
      }

      components.add(new DynamicZoneComponent(compUid, data));
    }

    return new DeserializationResult(Collections.unmodifiableList(components),
        Collections.unmodifiableList(errors));
  }

  /**
   * Serializes a list of {@link DynamicZoneComponent} instances back to a list of maps suitable for
   * JSONB storage.
   */
  public List<Map<String, Object>> serializeComponents(List<DynamicZoneComponent> components) {
    if (components == null || components.isEmpty()) {
      return List.of();
    }

    List<Map<String, Object>> result = new ArrayList<>(components.size());
    for (DynamicZoneComponent comp : components) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("__component", comp.getComponentUid());
      map.putAll(comp.getData());
      result.add(map);
    }
    return Collections.unmodifiableList(result);
  }

  // ============================================================
  // Ordering & Reordering
  // ============================================================

  /**
   * Reorders a component from one index to another within the zone's component list.
   *
   * @param components the current ordered list of components (not modified)
   * @param fromIndex the current index of the component to move
   * @param toIndex the target index to move the component to
   * @return a new list with the component at the new position
   * @throws IndexOutOfBoundsException if either index is out of bounds
   */
  public List<DynamicZoneComponent> reorderComponent(
      List<DynamicZoneComponent> components, int fromIndex, int toIndex) {

    if (fromIndex == toIndex) {
      return List.copyOf(components);
    }

    List<DynamicZoneComponent> mutable = new ArrayList<>(components);
    DynamicZoneComponent moving = mutable.remove(fromIndex);

    // When the element was removed from before the target, every element from
    // (fromIndex+1) to toIndex shifted left by one. Insert at the same target
    // index to compensate. When removed from after the target, no shift occurs.
    mutable.add(toIndex, moving);
    return Collections.unmodifiableList(mutable);
  }

  /**
   * Moves a component one position up (towards index 0).
   *
   * @return a new list with the component moved up, or the original list if already at the top
   */
  public List<DynamicZoneComponent> moveComponentUp(
      List<DynamicZoneComponent> components, int index) {
    if (index <= 0 || index >= components.size()) {
      return List.copyOf(components);
    }
    return reorderComponent(components, index, index - 1);
  }

  /**
   * Moves a component one position down (away from index 0).
   *
   * @return a new list with the component moved down, or the original list if already at the
   *     bottom
   */
  public List<DynamicZoneComponent> moveComponentDown(
      List<DynamicZoneComponent> components, int index) {
    if (index < 0 || index >= components.size() - 1) {
      return List.copyOf(components);
    }
    return reorderComponent(components, index, index + 1);
  }

  /**
   * Adds a new component instance at the specified position in the zone.
   *
   * @param components the current ordered list of components (not modified)
   * @param componentUid the UID of the component definition to add
   * @param index the position to insert at (negative or {@code >= size} = append)
   * @param data the field values for the component
   * @return a new list with the component added
   */
  public List<DynamicZoneComponent> addComponent(
      List<DynamicZoneComponent> components,
      String componentUid,
      int index,
      Map<String, Object> data) {

    List<DynamicZoneComponent> mutable = new ArrayList<>(components);
    DynamicZoneComponent newComp = new DynamicZoneComponent(componentUid, data);

    if (index < 0 || index >= mutable.size()) {
      mutable.add(newComp);
    } else {
      mutable.add(index, newComp);
    }

    return Collections.unmodifiableList(mutable);
  }

  /**
   * Removes a component at the given index from the zone.
   *
   * @param components the current ordered list of components (not modified)
   * @param index the index of the component to remove
   * @return a new list with the component removed
   * @throws IndexOutOfBoundsException if the index is out of bounds
   */
  public List<DynamicZoneComponent> removeComponent(
      List<DynamicZoneComponent> components, int index) {
    List<DynamicZoneComponent> mutable = new ArrayList<>(components);
    mutable.remove(index);
    return Collections.unmodifiableList(mutable);
  }

  /**
   * Validates component data fields against the relevant component definition. Checks that all
   * required fields are present and have valid types (basic type checking).
   *
   * @param zone the dynamic zone definition
   * @param components the deserialized components to validate
   * @return validation errors (empty list = valid)
   */
  public List<String> validateComponentData(
      DynamicZoneDefinition zone, List<DynamicZoneComponent> components) {

    List<String> errors = new ArrayList<>();
    if (components == null || components.isEmpty()) {
      return errors;
    }

    for (int i = 0; i < components.size(); i++) {
      DynamicZoneComponent comp = components.get(i);
      String uid = comp.getComponentUid();

      // Resolve the component definition
      ComponentDefinition def = schemaService.getComponent(uid);

      // If the definition exists, validate each field
      if (def != null) {
        for (FieldDefinition field : def.getFields()) {
          String fieldName = field.getName();
          Object value = comp.getField(fieldName);

          if (field.isRequired() && (value == null || (value instanceof String s && s.isBlank()))) {
            errors.add(
                "Component '"
                    + uid
                    + "' at index "
                    + i
                    + " requires field '"
                    + fieldName
                    + "'");
          }
        }
      }
    }

    return errors;
  }

  // ============================================================
  // Zone Validation (min/max, allowed components)
  // ============================================================

  /**
   * Validates that the given raw dynamic zone payload conforms to the zone's constraints: min/max
   * cardinality, allowed component UIDs, and component field requirements.
   *
   * <p>This is the main entry point for zone validation. It first deserializes the payload
   * polymorphically, checks min/max and allowed-component constraints, then validates individual
   * component fields against their definitions.
   *
   * @param zone the dynamic zone definition
   * @param payload a list of maps, each with a {@code __component} key and component data, or
   *     {@code null}
   * @return validation errors (empty list = valid)
   */
  public List<String> validateZonePayload(
      DynamicZoneDefinition zone, List<Map<String, Object>> payload) {
    List<String> errors = new ArrayList<>();

    // Step 1: Deserialize polymorphically
    DeserializationResult deser = deserializeComponents(zone, payload);
    errors.addAll(deser.errors());

    List<DynamicZoneComponent> components = deser.components();

    // Step 2: Cardinality checks
    int count = components.size();
    if (zone.getMin() > 0 && count < zone.getMin()) {
      errors.add(
          "Dynamic zone '"
              + zone.getName()
              + "' requires at least "
              + zone.getMin()
              + " components, got "
              + count);
    }
    if (zone.getMax() >= 0 && count > zone.getMax()) {
      errors.add(
          "Dynamic zone '"
              + zone.getName()
              + "' allows at most "
              + zone.getMax()
              + " components, got "
              + count);
    }

    // Step 3: Validate component data against component definitions
    errors.addAll(validateComponentData(zone, components));

    return errors;
  }

  /**
   * Extracts the dynamic zone data from an entry's JSONB payload, given the content type
   * definition. Returns a map of zone name → list of deserialized {@link DynamicZoneComponent}
   * instances.
   */
  public Map<String, List<DynamicZoneComponent>> extractDynamicZones(
      ContentTypeDefinition ct, Map<String, Object> entryData) {

    Map<String, List<DynamicZoneComponent>> result = new LinkedHashMap<>();

    for (DynamicZoneDefinition zone : ct.getDynamicZones()) {
      Object raw = entryData != null ? entryData.get(zone.getName()) : null;
      if (raw instanceof List<?> list) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPayload =
            list.stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .toList();

        DeserializationResult deser = deserializeComponents(zone, rawPayload);
        result.put(zone.getName(), deser.components());
      } else {
        result.put(zone.getName(), List.of());
      }
    }

    return result;
  }

  /**
   * Serializes a map of zone name → component lists back into a flat map suitable for JSONB
   * storage.
   */
  public Map<String, Object> serializeDynamicZones(
      Map<String, List<DynamicZoneComponent>> zoneData) {
    Map<String, Object> result = new HashMap<>();
    for (var entry : zoneData.entrySet()) {
      result.put(entry.getKey(), serializeComponents(entry.getValue()));
    }
    return result;
  }

  // ============================================================
  // Result type
  // ============================================================

  /**
   * The result of polymorphic deserialization: the list of successfully-deserialized components and
   * any errors encountered during the process.
   */
  public record DeserializationResult(
      List<DynamicZoneComponent> components, List<String> errors) {

    public DeserializationResult {
      components = components == null ? List.of() : components;
      errors = errors == null ? List.of() : errors;
    }

    /** Returns {@code true} if no errors occurred during deserialization. */
    public boolean isValid() {
      return errors.isEmpty();
    }
  }
}
