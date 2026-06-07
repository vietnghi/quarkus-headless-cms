package com.quarkus.cms.core.schema.component;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaCache;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ComponentCompositionService}.
 */
class ComponentCompositionServiceTest {

  private ComponentCompositionService service;
  private SchemaCache cache;

  @BeforeEach
  void setUp() {
    cache = new SchemaCache();
    service = new ComponentCompositionService();
    service.setSchemaStorage(new CacheBackedStorage(cache));
  }

  // ---- Transitive Closure ----

  @Test
  void shouldComputeClosureForLeafComponent() {
    cache.putComponent(comp("shared.image"));
    assertTrue(service.getTransitiveClosure("shared.image").isEmpty());
  }

  @Test
  void shouldComputeClosureForNestedComponent() {
    cache.putComponent(comp("shared.image"));
    cache.putComponent(comp("shared.hero", List.of(compField("bgImage", "shared.image"))));
    cache.putComponent(comp("shared.page", List.of(compField("hero", "shared.hero"))));

    Set<String> closure = service.getTransitiveClosure("shared.page");
    assertTrue(closure.contains("shared.hero"));
    assertTrue(closure.contains("shared.image"));
    assertEquals(2, closure.size());
  }

  @Test
  void shouldHandleMultipleComponentFields() {
    cache.putComponent(comp("shared.image"));
    cache.putComponent(comp("shared.video"));
    cache.putComponent(comp("shared.media", List.of(
        compField("image", "shared.image"),
        compField("video", "shared.video"))));

    assertEquals(2, service.getTransitiveClosure("shared.media").size());
  }

  @Test
  void shouldHandleMissingComponentInClosure() {
    // shared.hero references shared.image which doesn't exist
    cache.putComponent(comp("shared.hero", List.of(compField("bg", "shared.nonexistent"))));

    Set<String> closure = service.getTransitiveClosure("shared.hero");
    // The missing component is silently skipped
    assertTrue(closure.isEmpty());
  }

  // ---- Cycle Detection ----

  @Test
  void shouldDetectSimpleCycle() {
    cache.putComponent(comp("shared.a", List.of(compField("b", "shared.b"))));
    cache.putComponent(comp("shared.b", List.of(compField("a", "shared.a"))));

    List<String> cycle = service.detectCycle("shared.a");
    assertFalse(cycle.isEmpty(), "Expected a cycle to be detected");
    assertTrue(cycle.contains("shared.a"));
    assertTrue(cycle.contains("shared.b"));
  }

  @Test
  void shouldNotDetectCycleInAcyclicGraph() {
    cache.putComponent(comp("shared.image"));
    cache.putComponent(comp("shared.hero", List.of(compField("bg", "shared.image"))));
    cache.putComponent(comp("shared.page", List.of(compField("hero", "shared.hero"))));

    assertTrue(service.detectCycle("shared.page").isEmpty());
  }

  @Test
  void shouldDetectSelfCycle() {
    cache.putComponent(comp("shared.recursive", List.of(compField("child", "shared.recursive"))));

    List<String> cycle = service.detectCycle("shared.recursive");
    assertFalse(cycle.isEmpty(), "Expected self-cycle to be detected");
    assertTrue(cycle.contains("shared.recursive"));
  }

  @Test
  void shouldNotReportCycleForMissingComponent() {
    assertTrue(service.detectCycle("shared.nonexistent").isEmpty());
  }

  // ---- Max Depth ----

  @Test
  void shouldReturnDepthZeroForLeaf() {
    cache.putComponent(comp("shared.image"));
    assertEquals(0, service.getMaxDepth("shared.image"));
  }

  @Test
  void shouldComputeDepth() {
    cache.putComponent(comp("shared.image"));
    cache.putComponent(comp("shared.hero", List.of(compField("bg", "shared.image"))));
    cache.putComponent(comp("shared.page", List.of(compField("hero", "shared.hero"))));

    assertEquals(0, service.getMaxDepth("shared.image"));
    assertEquals(1, service.getMaxDepth("shared.hero"));
    assertEquals(2, service.getMaxDepth("shared.page"));
  }

  @Test
  void shouldThrowOnCycleInDepth() {
    cache.putComponent(comp("shared.a", List.of(compField("b", "shared.b"))));
    cache.putComponent(comp("shared.b", List.of(compField("a", "shared.a"))));

    assertThrows(ComponentCompositionException.class,
        () -> service.getMaxDepth("shared.a"));
  }

  @Test
  void shouldReturnDepthZeroForNonExistent() {
    assertEquals(0, service.getMaxDepth("shared.nonexistent"));
  }

  // ---- Topological Sort ----

  @Test
  void shouldTopologicallySortAcyclicGraph() {
    cache.putComponent(comp("shared.image"));
    cache.putComponent(comp("shared.hero", List.of(compField("bg", "shared.image"))));
    cache.putComponent(comp("shared.page", List.of(compField("hero", "shared.hero"))));

    List<String> sorted = service.topologicalSort("shared.page");
    assertEquals("shared.image", sorted.get(0));
    assertEquals("shared.hero", sorted.get(1));
    assertEquals("shared.page", sorted.get(2));
  }

  @Test
  void shouldThrowOnCycleInTopoSort() {
    cache.putComponent(comp("shared.a", List.of(compField("b", "shared.b"))));
    cache.putComponent(comp("shared.b", List.of(compField("a", "shared.a"))));

    assertThrows(ComponentCompositionException.class,
        () -> service.topologicalSort("shared.a"));
  }

  @Test
  void shouldReturnEmptyForNonExistentTopo() {
    assertTrue(service.topologicalSort("shared.nonexistent").isEmpty());
  }

  // ---- Composition Graph Validation ----

  @Test
  void shouldValidateValidGraph() {
    cache.putComponent(comp("shared.image"));
    cache.putComponent(comp("shared.hero", List.of(compField("bg", "shared.image"))));
    assertTrue(service.validateCompositionGraph("shared.hero").isEmpty());
  }

  @Test
  void shouldReportMissingComponentReference() {
    cache.putComponent(comp("shared.hero", List.of(compField("bg", "shared.nonexistent"))));

    List<String> errors = service.validateCompositionGraph("shared.hero");
    assertTrue(errors.stream().anyMatch(e -> e.contains("nonexistent")),
        "Should report missing component, got: " + errors);
  }

  @Test
  void shouldValidateEmptyGraph() {
    cache.putComponent(comp("shared.empty"));
    assertTrue(service.validateCompositionGraph("shared.empty").isEmpty());
  }

  // ---- Diamond dependency ----

  @Test
  void shouldHandleDiamondDependency() {
    cache.putComponent(comp("shared.image"));
    cache.putComponent(comp("shared.hero", List.of(compField("bg", "shared.image"))));
    cache.putComponent(comp("shared.footer", List.of(compField("logo", "shared.image"))));
    cache.putComponent(comp("shared.page", List.of(
        compField("hero", "shared.hero"),
        compField("footer", "shared.footer"))));

    Set<String> closure = service.getTransitiveClosure("shared.page");
    assertEquals(3, closure.size());
    assertTrue(closure.containsAll(Set.of("shared.hero", "shared.footer", "shared.image")));
  }

  // ---- helpers ----

  private static ComponentDefinition comp(String uid) {
    return ComponentDefinition.builder(uid).displayName(uid).build();
  }

  private static ComponentDefinition comp(String uid, List<FieldDefinition> fields) {
    return ComponentDefinition.builder(uid).displayName(uid).fields(fields).build();
  }

  private static FieldDefinition compField(String name, String componentUid) {
    return FieldDefinition.builder(name, FieldType.COMPONENT).component(componentUid).build();
  }

  /**
   * Minimal SchemaStorageService stub backed by a SchemaCache.
   */
  static class CacheBackedStorage extends com.quarkus.cms.core.schema.storage.SchemaStorageService {
    private final SchemaCache cache;

    CacheBackedStorage(SchemaCache cache) {
      this.cache = cache;
    }

    @Override
    public ComponentDefinition getComponent(String uid) {
      return cache.getComponent(uid);
    }
  }
}
