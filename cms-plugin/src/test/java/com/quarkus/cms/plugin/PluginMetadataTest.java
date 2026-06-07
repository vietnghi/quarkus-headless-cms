package com.quarkus.cms.plugin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PluginMetadata}. */
class PluginMetadataTest {

  @Test
  void testRequiredFields() {
    PluginMetadata meta =
        PluginMetadata.builder("test-plugin", "1.0.0")
            .displayName("Test Plugin")
            .description("A test plugin")
            .author("Test Author")
            .license("MIT")
            .build();

    assertEquals("test-plugin", meta.getName());
    assertEquals("1.0.0", meta.getVersion());
    assertEquals("Test Plugin", meta.getDisplayName());
    assertEquals("A test plugin", meta.getDescription());
    assertEquals("Test Author", meta.getAuthor());
    assertEquals("MIT", meta.getLicense());
    assertTrue(meta.getOptions().isEmpty());
    assertTrue(meta.getDependencies().isEmpty());
  }

  @Test
  void testRequiredNameThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> PluginMetadata.builder(null, "1.0.0").build());
  }

  @Test
  void testRequiredVersionThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> PluginMetadata.builder("test", null).build());
  }

  @Test
  void testEqualsAndHashCode() {
    PluginMetadata m1 = PluginMetadata.builder("p1", "1.0.0").build();
    PluginMetadata m2 = PluginMetadata.builder("p1", "2.0.0").build();
    PluginMetadata m3 = PluginMetadata.builder("p2", "1.0.0").build();

    assertEquals(m1, m2);
    assertEquals(m1.hashCode(), m2.hashCode());
    assertNotEquals(m1, m3);
  }

  @Test
  void testFullMetadata() {
    PluginMetadata meta =
        PluginMetadata.builder("seo-plugin", "1.0.0")
            .displayName("SEO Plugin")
            .description("Adds SEO fields")
            .author("CMS Team")
            .license("MIT")
            .homepage("https://example.com/seo")
            .options(java.util.Map.of("maxLength", "160"))
            .dependencies(java.util.Map.of("cms-core", "1.0.0"))
            .build();

    assertEquals("seo-plugin", meta.getName());
    assertEquals(1, meta.getOptions().size());
    assertEquals("160", meta.getOptions().get("maxLength"));
    assertEquals(1, meta.getDependencies().size());
    assertEquals("1.0.0", meta.getDependencies().get("cms-core"));
  }
}
