package com.quarkus.cms.core.schema.zone;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaCache;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DynamicZoneService}. Uses an in-memory {@link SchemaCache} with no database
 * dependency.
 */
class DynamicZoneServiceTest {

  private SchemaCache cache;
  private DynamicZoneService service;

  @BeforeEach
  void setUp() {
    cache = new SchemaCache();
    service = new DynamicZoneService();
    injectField(service, "schemaService", new CacheBackedStorage(cache));
  }

  // ============================================================
  // DynamicZoneComponent basics
  // ============================================================

  @Test
  void shouldCreateDynamicZoneComponent() {
    DynamicZoneComponent comp = new DynamicZoneComponent("shared.quote",
        Map.of("text", "Hello", "author", "Alice"));

    assertEquals("shared.quote", comp.getComponentUid());
    assertEquals("Hello", comp.getField("text"));
    assertEquals("Alice", comp.getField("author"));
  }

  @Test
  void shouldHandleEmptyData() {
    DynamicZoneComponent comp = new DynamicZoneComponent("shared.quote", Map.of());
    assertEquals("shared.quote", comp.getComponentUid());
    assertTrue(comp.getData().isEmpty());
  }

  @Test
  void shouldSetFieldViaSetter() {
    DynamicZoneComponent comp = new DynamicZoneComponent();
    comp.setComponentUid("shared.hero");
    comp.setField("title", "Welcome");

    assertEquals("shared.hero", comp.getComponentUid());
    assertEquals("Welcome", comp.getField("title"));
  }

  @Test
  void shouldRouteComponentUidViaSetter() {
    DynamicZoneComponent comp = new DynamicZoneComponent();
    comp.setField("__component", "shared.banner");
    comp.setField("heading", "Big Sale");

    assertEquals("shared.banner", comp.getComponentUid());
    assertEquals("Big Sale", comp.getField("heading"));
  }

  // ============================================================
  // Polymorphic Deserialization
  // ============================================================

  @Test
  void shouldDeserializeValidComponents() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote", "shared.hero"))
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.quote", "text", "Hello"),
        Map.of("__component", "shared.hero", "title", "Hero Title"));

    DynamicZoneService.DeserializationResult result =
        service.deserializeComponents(zone, payload);

    assertTrue(result.isValid());
    assertEquals(2, result.components().size());
    assertEquals("shared.quote", result.components().get(0).getComponentUid());
    assertEquals("Hello", result.components().get(0).getField("text"));
    assertEquals("shared.hero", result.components().get(1).getComponentUid());
  }

  @Test
  void shouldReportErrorsOnMissingComponent() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.quote", "text", "Hello"),
        Map.of("text", "no uid"));

    DynamicZoneService.DeserializationResult result =
        service.deserializeComponents(zone, payload);

    assertFalse(result.isValid());
    assertEquals(1, result.errors().size());
    assertTrue(result.errors().get(0).contains("missing '__component' key"));
  }

  @Test
  void shouldReportErrorsOnDisallowedComponent() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.hero", "title", "Bad"));

    DynamicZoneService.DeserializationResult result =
        service.deserializeComponents(zone, payload);

    assertFalse(result.isValid());
    assertTrue(result.errors().get(0).contains("not allowed"));
  }

  @Test
  void shouldHandleNullPayload() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .build();

    DynamicZoneService.DeserializationResult result =
        service.deserializeComponents(zone, null);

    assertTrue(result.isValid());
    assertTrue(result.components().isEmpty());
  }

  // ============================================================
  // Serialization
  // ============================================================

  @Test
  void shouldRoundTripComponents() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.quote", "text", "Hello", "author", "Alice"));

    DynamicZoneService.DeserializationResult deser =
        service.deserializeComponents(zone, payload);
    assertTrue(deser.isValid());

    List<Map<String, Object>> serialized = service.serializeComponents(deser.components());
    assertEquals(1, serialized.size());
    assertEquals("shared.quote", serialized.get(0).get("__component"));
    assertEquals("Hello", serialized.get(0).get("text"));
    assertEquals("Alice", serialized.get(0).get("author"));
  }

  // ============================================================
  // Ordering & Reordering
  // ============================================================

  @Test
  void shouldReorderComponents() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()),
        new DynamicZoneComponent("shared.c", Map.of()));

    List<DynamicZoneComponent> reordered = service.reorderComponent(components, 0, 2);

    assertEquals("shared.b", reordered.get(0).getComponentUid());
    assertEquals("shared.c", reordered.get(1).getComponentUid());
    assertEquals("shared.a", reordered.get(2).getComponentUid());
  }

  @Test
  void shouldNotChangeWhenReorderingToSameIndex() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()));

    List<DynamicZoneComponent> result = service.reorderComponent(components, 1, 1);

    assertEquals(2, result.size());
    assertEquals("shared.a", result.get(0).getComponentUid());
    assertEquals("shared.b", result.get(1).getComponentUid());
  }

  @Test
  void shouldReorderFromEndToStart() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()),
        new DynamicZoneComponent("shared.c", Map.of()));

    List<DynamicZoneComponent> reordered = service.reorderComponent(components, 2, 0);

    assertEquals("shared.c", reordered.get(0).getComponentUid());
    assertEquals("shared.a", reordered.get(1).getComponentUid());
    assertEquals("shared.b", reordered.get(2).getComponentUid());
  }

  @Test
  void shouldNotMutateOriginalList() {
    List<DynamicZoneComponent> original = new java.util.ArrayList<>(List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of())));

    service.reorderComponent(original, 0, 1);

    assertEquals("shared.a", original.get(0).getComponentUid());
    assertEquals("shared.b", original.get(1).getComponentUid());
  }

  @Test
  void shouldMoveComponentUp() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()),
        new DynamicZoneComponent("shared.c", Map.of()));

    List<DynamicZoneComponent> moved = service.moveComponentUp(components, 1);

    assertEquals("shared.b", moved.get(0).getComponentUid());
    assertEquals("shared.a", moved.get(1).getComponentUid());
    assertEquals("shared.c", moved.get(2).getComponentUid());
  }

  @Test
  void shouldNotMoveUpBeyondTop() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()));

    List<DynamicZoneComponent> moved = service.moveComponentUp(components, 0);

    assertEquals("shared.a", moved.get(0).getComponentUid());
    assertEquals("shared.b", moved.get(1).getComponentUid());
  }

  @Test
  void shouldMoveComponentDown() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()),
        new DynamicZoneComponent("shared.c", Map.of()));

    List<DynamicZoneComponent> moved = service.moveComponentDown(components, 0);

    assertEquals("shared.b", moved.get(0).getComponentUid());
    assertEquals("shared.a", moved.get(1).getComponentUid());
    assertEquals("shared.c", moved.get(2).getComponentUid());
  }

  @Test
  void shouldNotMoveDownBeyondBottom() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()));

    List<DynamicZoneComponent> moved = service.moveComponentDown(components, 1);

    assertEquals("shared.a", moved.get(0).getComponentUid());
    assertEquals("shared.b", moved.get(1).getComponentUid());
  }

  @Test
  void shouldAddComponentAtPosition() {
    List<DynamicZoneComponent> components = new java.util.ArrayList<>(List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.c", Map.of())));

    List<DynamicZoneComponent> added = service.addComponent(
        components, "shared.b", 1, Map.of("value", "mid"));

    assertEquals(3, added.size());
    assertEquals("shared.a", added.get(0).getComponentUid());
    assertEquals("shared.b", added.get(1).getComponentUid());
    assertEquals("shared.c", added.get(2).getComponentUid());
    assertEquals("mid", added.get(1).getField("value"));
  }

  @Test
  void shouldAppendComponentWhenIndexOutOfBounds() {
    List<DynamicZoneComponent> components = new java.util.ArrayList<>(List.of(
        new DynamicZoneComponent("shared.a", Map.of())));

    List<DynamicZoneComponent> added = service.addComponent(
        components, "shared.b", -1, Map.of());

    assertEquals(2, added.size());
    assertEquals("shared.b", added.get(1).getComponentUid());
  }

  @Test
  void shouldRemoveComponent() {
    List<DynamicZoneComponent> components = new java.util.ArrayList<>(List.of(
        new DynamicZoneComponent("shared.a", Map.of()),
        new DynamicZoneComponent("shared.b", Map.of()),
        new DynamicZoneComponent("shared.c", Map.of())));

    List<DynamicZoneComponent> removed = service.removeComponent(components, 1);

    assertEquals(2, removed.size());
    assertEquals("shared.a", removed.get(0).getComponentUid());
    assertEquals("shared.c", removed.get(1).getComponentUid());
  }

  @Test
  void shouldThrowOnRemoveOutOfBounds() {
    List<DynamicZoneComponent> components = List.of(
        new DynamicZoneComponent("shared.a", Map.of()));

    assertThrows(IndexOutOfBoundsException.class, () -> service.removeComponent(components, 5));
  }

  // ============================================================
  // Zone Payload Validation
  // ============================================================

  @Test
  void shouldValidateMinComponents() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .min(1)
        .build();

    List<String> errors = service.validateZonePayload(zone, List.of());
    assertFalse(errors.isEmpty());
    assertTrue(errors.get(0).contains("requires at least 1"));
  }

  @Test
  void shouldValidateMaxComponents() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .max(1)
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.quote"),
        Map.of("__component", "shared.quote"));

    List<String> errors = service.validateZonePayload(zone, payload);
    assertFalse(errors.isEmpty());
    assertTrue(errors.get(0).contains("allows at most 1"));
  }

  @Test
  void shouldValidateAllowedComponents() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.hero"));

    List<String> errors = service.validateZonePayload(zone, payload);
    assertFalse(errors.isEmpty());
    assertTrue(errors.get(0).contains("not allowed"));
  }

  @Test
  void shouldValidateRequiredComponentFields() {
    cache.putComponent(ComponentDefinition.builder("shared.quote")
        .displayName("Quote")
        .fields(List.of(
            FieldDefinition.builder("text", FieldType.STRING).required(true).build(),
            FieldDefinition.builder("author", FieldType.STRING).build()))
        .build());

    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .build();

    // Missing required "text" field
    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.quote", "author", "Alice"));

    List<String> errors = service.validateZonePayload(zone, payload);
    assertFalse(errors.isEmpty());
    assertTrue(errors.get(0).contains("requires field 'text'"));
  }

  @Test
  void shouldPassValidationWithCompleteData() {
    cache.putComponent(ComponentDefinition.builder("shared.quote")
        .displayName("Quote")
        .fields(List.of(
            FieldDefinition.builder("text", FieldType.STRING).required(true).build(),
            FieldDefinition.builder("author", FieldType.STRING).build()))
        .build());

    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .min(1).max(3)
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.quote", "text", "Hello", "author", "Alice"));

    List<String> errors = service.validateZonePayload(zone, payload);
    assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
  }

  @Test
  void shouldReturnEmptyErrorsForNotRequiredWhenFieldPresent() {
    cache.putComponent(ComponentDefinition.builder("shared.banner")
        .displayName("Banner")
        .fields(List.of(
            FieldDefinition.builder("title", FieldType.STRING).required(true).build(),
            FieldDefinition.builder("subtitle", FieldType.STRING).required(false).build()))
        .build());

    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("content")
        .components(List.of("shared.banner"))
        .build();

    List<Map<String, Object>> payload = List.of(
        Map.of("__component", "shared.banner", "title", "Main"));

    List<String> errors = service.validateZonePayload(zone, payload);
    assertTrue(errors.isEmpty());
  }

  // ============================================================
  // Extract & Serialize Dynamic Zones
  // ============================================================

  @Test
  void shouldExtractDynamicZonesFromEntry() {
    ContentTypeDefinition ct = ContentTypeDefinition.builder(
        "api::page.page", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Page")
        .dynamicZones(List.of(
            DynamicZoneDefinition.builder("blocks")
                .components(List.of("shared.quote"))
                .build()))
        .build();

    Map<String, Object> entryData = Map.of(
        "title", "My Page",
        "blocks", List.of(
            Map.of("__component", "shared.quote", "text", "Hello")));

    Map<String, List<DynamicZoneComponent>> zones =
        service.extractDynamicZones(ct, entryData);

    assertTrue(zones.containsKey("blocks"));
    assertEquals(1, zones.get("blocks").size());
    assertEquals("shared.quote", zones.get("blocks").get(0).getComponentUid());
  }

  @Test
  void shouldHandleMissingZoneData() {
    ContentTypeDefinition ct = ContentTypeDefinition.builder(
        "api::page.page", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Page")
        .dynamicZones(List.of(
            DynamicZoneDefinition.builder("blocks")
                .components(List.of("shared.quote"))
                .build()))
        .build();

    Map<String, Object> entryData = Map.of("title", "No Zone Data");

    Map<String, List<DynamicZoneComponent>> zones =
        service.extractDynamicZones(ct, entryData);

    assertTrue(zones.containsKey("blocks"));
    assertTrue(zones.get("blocks").isEmpty());
  }

  @Test
  void shouldSerializeDynamicZonesBackToStorage() {
    DynamicZoneDefinition zone = DynamicZoneDefinition.builder("blocks")
        .components(List.of("shared.quote"))
        .build();

    ContentTypeDefinition ct = ContentTypeDefinition.builder(
        "api::page.page", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Page")
        .dynamicZones(List.of(zone))
        .build();

    Map<String, Object> entryData = Map.of(
        "blocks", List.of(
            Map.of("__component", "shared.quote", "text", "Hi")));

    Map<String, List<DynamicZoneComponent>> extracted =
        service.extractDynamicZones(ct, entryData);

    Map<String, Object> serialized = service.serializeDynamicZones(extracted);

    assertTrue(serialized.containsKey("blocks"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> blocks = (List<Map<String, Object>>) serialized.get("blocks");
    assertEquals(1, blocks.size());
    assertEquals("shared.quote", blocks.get(0).get("__component"));
    assertEquals("Hi", blocks.get(0).get("text"));
  }

  // ============================================================
  // Available Components
  // ============================================================

  @Test
  void shouldReturnAvailableComponentsForZone() {
    cache.putComponent(ComponentDefinition.builder("shared.quote").displayName("Quote").build());
    cache.putComponent(ComponentDefinition.builder("shared.hero").displayName("Hero").build());

    ContentTypeDefinition ct = ContentTypeDefinition.builder(
        "api::page.page", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Page")
        .dynamicZones(List.of(
            DynamicZoneDefinition.builder("blocks")
                .components(List.of("shared.quote", "shared.hero"))
                .build()))
        .build();
    cache.putContentType(ct);

    List<ComponentDefinition> available =
        service.getAvailableComponents("api::page.page", "blocks");

    assertEquals(2, available.size());
  }

  @Test
  void shouldReturnEmptyForMissingZone() {
    ContentTypeDefinition ct = ContentTypeDefinition.builder(
        "api::page.page", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Page")
        .build();
    cache.putContentType(ct);

    List<ComponentDefinition> available =
        service.getAvailableComponents("api::page.page", "nonexistent");

    assertTrue(available.isEmpty());
  }

  // ============================================================
  // helpers
  // ============================================================

  private static void injectField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject field '" + fieldName + "'", e);
    }
  }

  /** Minimal SchemaStorageService stub backed by a SchemaCache. */
  static class CacheBackedStorage extends SchemaStorageService {
    private final SchemaCache cache;

    CacheBackedStorage(SchemaCache cache) {
      this.cache = cache;
    }

    @Override
    public ComponentDefinition getComponent(String uid) {
      return cache.getComponent(uid);
    }

    @Override
    public ContentTypeDefinition getContentType(String uid) {
      return cache.getContentType(uid);
    }
  }
}
