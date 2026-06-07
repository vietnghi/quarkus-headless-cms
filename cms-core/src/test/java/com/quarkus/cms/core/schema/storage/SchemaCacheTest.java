package com.quarkus.cms.core.schema.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaCacheTest {

  private SchemaCache cache;

  @BeforeEach
  void setUp() {
    cache = new SchemaCache();
  }

  @Test
  void shouldStoreAndRetrieveContentType() {
    var ct =
        ContentTypeDefinition.builder("api::article.article", ContentTypeKind.COLLECTION_TYPE)
            .displayName("Article")
            .build();

    cache.putContentType(ct);
    assertTrue(cache.hasContentType("api::article.article"));
    assertEquals(ct, cache.getContentType("api::article.article"));
    assertEquals(1, cache.contentTypeCount());
  }

  @Test
  void shouldReturnNullForUnknownContentType() {
    assertNull(cache.getContentType("api::nonexistent.nonexistent"));
    assertFalse(cache.hasContentType("api::nonexistent.nonexistent"));
  }

  @Test
  void shouldInvalidateContentType() {
    var ct =
        ContentTypeDefinition.builder("api::article.article", ContentTypeKind.COLLECTION_TYPE)
            .build();
    cache.putContentType(ct);
    cache.invalidateContentType("api::article.article");
    assertFalse(cache.hasContentType("api::article.article"));
    assertEquals(0, cache.contentTypeCount());
  }

  @Test
  void shouldStoreAndRetrieveComponent() {
    var comp = ComponentDefinition.builder("shared.seo").build();
    cache.putComponent(comp);
    assertTrue(cache.hasComponent("shared.seo"));
    assertEquals(comp, cache.getComponent("shared.seo"));
    assertEquals(1, cache.componentCount());
  }

  @Test
  void shouldInvalidateComponent() {
    var comp = ComponentDefinition.builder("shared.seo").build();
    cache.putComponent(comp);
    cache.invalidateComponent("shared.seo");
    assertFalse(cache.hasComponent("shared.seo"));
    assertEquals(0, cache.componentCount());
  }

  @Test
  void shouldClearAll() {
    cache.putContentType(
        ContentTypeDefinition.builder("api::a.a", ContentTypeKind.COLLECTION_TYPE).build());
    cache.putComponent(ComponentDefinition.builder("shared.x").build());
    cache.clear();
    assertEquals(0, cache.contentTypeCount());
    assertEquals(0, cache.componentCount());
  }

  @Test
  void shouldGetAllContentTypes() {
    cache.putContentType(
        ContentTypeDefinition.builder("api::a.a", ContentTypeKind.COLLECTION_TYPE).build());
    cache.putContentType(
        ContentTypeDefinition.builder("api::b.b", ContentTypeKind.COLLECTION_TYPE).build());
    var all = cache.getAllContentTypes();
    assertEquals(2, all.size());
  }

  @Test
  void shouldGetAllComponents() {
    cache.putComponent(ComponentDefinition.builder("shared.a").build());
    cache.putComponent(ComponentDefinition.builder("shared.b").build());
    cache.putComponent(ComponentDefinition.builder("shared.c").build());
    assertEquals(3, cache.getAllComponents().size());
  }
}
