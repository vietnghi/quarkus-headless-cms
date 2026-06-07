package com.quarkus.cms.core.schema.component;

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
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ComponentRegistryService}. Uses an in-memory {@link SchemaCache} with no
 * database dependency.
 */
class ComponentRegistryServiceTest {

  private SchemaCache cache;
  private ComponentRegistryService registry;

  @BeforeEach
  void setUp() {
    cache = new SchemaCache();
    registry = new ComponentRegistryService();
    injectField(registry, "schemaCache", cache);
    injectField(registry, "schemaStorage", new CacheBackedStorage(cache));
    // Wire lifecycle events to a test observer via reflection
    TestLifecycleObserver observer = new TestLifecycleObserver();
    injectField(registry, "lifecycleEvent", new ComponentRegistryServiceTest.SimpleEvent(observer::accept));
    registry.rebuildUsageIndex();
  }

  // ---- Registration lifecycle ----

  @Test
  void shouldRebuildIndexOnRegister() {
    putComponent("shared.seo", List.of(field("title", FieldType.STRING).build()));
    // putComponent populates cache but doesn't update registry's usage index
    registry.rebuildUsageIndex();

    assertNotNull(cache.getComponent("shared.seo"));
  }

  // ---- Usage tracking across content types ----

  @Test
  void shouldTrackContentTypeUsage() {
    putComponent("shared.seo", List.of());
    putComponent("shared.author", List.of());

    // Register a content type that uses both components
    ContentTypeDefinition article = ContentTypeDefinition.builder(
        "api::article.article", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Article")
        .fields(List.of(
            FieldDefinition.builder("seo", FieldType.COMPONENT).component("shared.seo").build(),
            FieldDefinition.builder("author", FieldType.COMPONENT).component("shared.author").build()))
        .build();
    cache.putContentType(article);
    registry.rebuildUsageIndex();

    assertTrue(registry.getDependents("shared.seo").contains("api::article.article"));
    assertTrue(registry.getDependents("shared.author").contains("api::article.article"));
    assertEquals(
        Set.of("shared.seo", "shared.author"),
        registry.findComponentsUsedByContentType("api::article.article"));
  }

  @Test
  void shouldTrackComponentToComponentUsage() {
    putComponent("shared.seo", List.of());
    putComponent("shared.author", List.of());
    putComponent("shared.article", List.of(
        FieldDefinition.builder("seo", FieldType.COMPONENT).component("shared.seo").build(),
        FieldDefinition.builder("author", FieldType.COMPONENT).component("shared.author").build()));

    registry.rebuildUsageIndex();

    assertTrue(registry.getDependents("shared.seo").contains("shared.article"));
    assertTrue(registry.getDependents("shared.author").contains("shared.article"));
  }

  @Test
  void shouldTrackDynamicZoneUsage() {
    putComponent("shared.seo", List.of());
    putComponent("shared.hero", List.of());

    ContentTypeDefinition page = ContentTypeDefinition.builder(
        "api::page.page", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Page")
        .dynamicZones(List.of(
            DynamicZoneDefinition.builder("content")
                .components(List.of("shared.seo", "shared.hero"))
                .build()))
        .build();
    cache.putContentType(page);
    registry.rebuildUsageIndex();

    assertTrue(registry.getDependents("shared.seo").contains("api::page.page"));
    assertTrue(registry.getDependents("shared.hero").contains("api::page.page"));
  }

  // ---- Delete guard ----

  @Test
  void shouldAllowDeleteWhenNotInUse() {
    cache.putComponent(ComponentDefinition.builder("shared.unused").build());
    registry.rebuildUsageIndex();

    assertTrue(registry.isDeletable("shared.unused"));
  }

  @Test
  void shouldBlockDeleteWhenInUse() {
    putComponent("shared.seo", List.of(
        FieldDefinition.builder("seo", FieldType.COMPONENT).component("shared.seo").build()));

    ContentTypeDefinition article = ContentTypeDefinition.builder(
        "api::article.article", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Article")
        .fields(List.of(
            FieldDefinition.builder("seo", FieldType.COMPONENT).component("shared.seo").build()))
        .build();
    cache.putContentType(article);
    registry.rebuildUsageIndex();

    assertFalse(registry.isDeletable("shared.seo"));
  }

  @Test
  void shouldDeleteFromIndexAfterCacheInvalidation() {
    putComponent("shared.seo", List.of());

    cache.invalidateComponent("shared.seo");
    registry.rebuildUsageIndex();

    assertTrue(registry.getDependents("shared.seo").isEmpty());
  }

  // ---- Closure ----

  @Test
  void shouldComputeComponentClosure() {
    putComponent("shared.image", List.of());
    putComponent("shared.hero", List.of(
        FieldDefinition.builder("bgImage", FieldType.COMPONENT).component("shared.image").build()));
    putComponent("shared.page", List.of(
        FieldDefinition.builder("hero", FieldType.COMPONENT).component("shared.hero").build()));

    registry.rebuildUsageIndex();

    Set<String> closure = registry.getComponentClosure("shared.page");
    assertTrue(closure.contains("shared.hero"));
    assertTrue(closure.contains("shared.image"));
    assertEquals(2, closure.size());
  }

  @Test
  void shouldReturnClosureOnlyForRootComponent() {
    putComponent("shared.image", List.of());
    putComponent("shared.hero", List.of(
        FieldDefinition.builder("bg", FieldType.COMPONENT).component("shared.image").build()));
    registry.rebuildUsageIndex();

    Set<String> pageClosure = registry.getComponentClosure("shared.page");
    assertTrue(pageClosure.isEmpty(), "Non-existent component should have empty closure");
  }

  @Test
  void shouldReturnEmptyClosureForLeafComponent() {
    putComponent("shared.image", List.of());
    registry.rebuildUsageIndex();

    assertTrue(registry.getComponentClosure("shared.image").isEmpty());
  }

  @Test
  void shouldFindComponentsUsingComponent() {
    putComponent("shared.image", List.of());
    putComponent("shared.hero", List.of(
        FieldDefinition.builder("img", FieldType.COMPONENT).component("shared.image").build()));
    putComponent("shared.footer", List.of(
        FieldDefinition.builder("logo", FieldType.COMPONENT).component("shared.image").build()));
    registry.rebuildUsageIndex();

    Set<String> users = registry.findComponentsUsingComponent("shared.image");
    assertTrue(users.contains("shared.hero"));
    assertTrue(users.contains("shared.footer"));
  }

  @Test
  void shouldFindContentTypesUsingComponent() {
    putComponent("shared.seo", List.of());

    ContentTypeDefinition article = ContentTypeDefinition.builder(
        "api::article.article", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Article")
        .fields(List.of(
            FieldDefinition.builder("seo", FieldType.COMPONENT).component("shared.seo").build()))
        .build();
    cache.putContentType(article);
    registry.rebuildUsageIndex();

    Set<String> ctUsers = registry.findContentTypesUsingComponent("shared.seo");
    assertTrue(ctUsers.contains("api::article.article"));
  }

  // ---- helpers ----

  private void putComponent(String uid, List<FieldDefinition> fields) {
    cache.putComponent(ComponentDefinition.builder(uid).displayName(uid).fields(fields).build());
  }

  private static FieldDefinition.Builder field(String name, FieldType type) {
    return FieldDefinition.builder(name, type);
  }

  /** Injects a value into a private/package-private field. */
  static void injectField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject field '" + fieldName + "'", e);
    }
  }

  /** Minimal CDI Event stub. */
  static class SimpleEvent implements jakarta.enterprise.event.Event<ComponentLifecycleEvent> {
    private final java.util.function.Consumer<ComponentLifecycleEvent> handler;

    SimpleEvent(java.util.function.Consumer<ComponentLifecycleEvent> handler) {
      this.handler = handler;
    }

    @Override
    public void fire(ComponentLifecycleEvent event) { handler.accept(event); }

    @Override @SuppressWarnings("unchecked")
    public <U extends ComponentLifecycleEvent> jakarta.enterprise.event.Event<U> select(
        jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
      return (jakarta.enterprise.event.Event<U>) this;
    }

    @Override @SuppressWarnings("unchecked")
    public <U extends ComponentLifecycleEvent> jakarta.enterprise.event.Event<U> select(
        Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
      return (jakarta.enterprise.event.Event<U>) this;
    }

    @Override
    public jakarta.enterprise.event.Event<ComponentLifecycleEvent> select(
        java.lang.annotation.Annotation... qualifiers) { return this; }

    @Override
    public <U extends ComponentLifecycleEvent> java.util.concurrent.CompletionStage<U> fireAsync(U event) {
      handler.accept(event);
      return java.util.concurrent.CompletableFuture.completedFuture(event);
    }

    @Override
    public <U extends ComponentLifecycleEvent> java.util.concurrent.CompletionStage<U> fireAsync(
        U event, jakarta.enterprise.event.NotificationOptions options) {
      handler.accept(event);
      return java.util.concurrent.CompletableFuture.completedFuture(event);
    }
  }

  /** Records the last lifecycle event. */
  static class TestLifecycleObserver implements java.util.function.Consumer<ComponentLifecycleEvent> {
    int count;
    ComponentLifecycleEvent last;

    @Override
    public void accept(ComponentLifecycleEvent event) {
      count++;
      last = event;
    }

    void reset() { count = 0; last = null; }
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
