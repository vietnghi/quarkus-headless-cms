package com.quarkus.cms.customfields;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.customfields.hook.SlugHook;
import com.quarkus.cms.customfields.storage.JsonbFieldStorageStrategy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Tests for the JSONB storage strategy and hooks. */
class StorageAndHooksTest {

  // ---- JSONB Storage ----

  @Test
  void shouldReadWriteValues() {
    var entry = new CmsEntry();
    entry.contentType = "api::article.article";
    var storage = new JsonbFieldStorageStrategy();

    storage.writeValue(entry, "seoDesc", "SEO Description");
    assertEquals("SEO Description", storage.readValue(entry, "seoDesc"));
  }

  @Test
  void shouldReadAllValues() {
    var entry = new CmsEntry();
    var storage = new JsonbFieldStorageStrategy();
    storage.writeValue(entry, "a", 1);
    storage.writeValue(entry, "b", "two");

    Map<String, Object> all = storage.readAllValues(entry);
    assertEquals(2, all.size());
    assertEquals(1, all.get("a"));
    assertEquals("two", all.get("b"));
  }

  @Test
  void shouldRemoveValues() {
    var entry = new CmsEntry();
    var storage = new JsonbFieldStorageStrategy();
    storage.writeValue(entry, "key", "value");
    assertEquals("value", storage.readValue(entry, "key"));

    storage.removeValue(entry, "key");
    assertNull(storage.readValue(entry, "key"));
  }

  @Test
  void shouldRemoveAllValues() {
    var entry = new CmsEntry();
    var storage = new JsonbFieldStorageStrategy();
    storage.writeValue(entry, "a", 1);
    storage.writeValue(entry, "b", 2);
    storage.removeAllValues(entry);
    assertTrue(storage.readAllValues(entry).isEmpty());
  }

  @Test
  void shouldHandleNonMapCustomGracefully() {
    var entry = new CmsEntry();
    entry.data.put("_custom", "not-a-map");
    var storage = new JsonbFieldStorageStrategy();
    assertTrue(storage.readAllValues(entry).isEmpty());
  }

  @Test
  void shouldReturnEmptyMapForNewEntry() {
    var entry = new CmsEntry();
    var storage = new JsonbFieldStorageStrategy();
    assertTrue(storage.readAllValues(entry).isEmpty());
  }

  // ---- Slug Hook ----

  @Test
  void slugHookGeneratesFromSourceField() {
    var hook = new SlugHook();
    Map<String, Object> entryData = Map.of("title", "Hello World Post");
    Object result = hook.beforeSave("slug", "", entryData, Map.of("sourceField", "title"));
    assertEquals("hello-world-post", result);
  }

  @Test
  void slugHookPreservesExistingValue() {
    var hook = new SlugHook();
    Map<String, Object> entryData = Map.of("title", "Hello World");
    Object result = hook.beforeSave("slug", "custom-slug", entryData, Map.of());
    assertEquals("custom-slug", result);
  }

  @Test
  void slugHookUsesDefaultSourceField() {
    var hook = new SlugHook();
    Map<String, Object> entryData = Map.of("title", "Default Source");
    Object result = hook.beforeSave("slug", "", entryData, Map.of());
    assertEquals("default-source", result);
  }

  @Test
  void slugHookReturnsNullWhenNoSource() {
    var hook = new SlugHook();
    Map<String, Object> entryData = Map.of();
    Object result = hook.beforeSave("slug", null, entryData, Map.of());
    assertNull(result);
  }
}
