package com.quarkus.cms.core.schema.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ComponentDefinitionTest {

  @Test
  void shouldBuildComponent() {
    var comp =
        ComponentDefinition.builder("shared.seo")
            .category("shared")
            .displayName("SEO")
            .description("SEO metadata fields")
            .fields(
                List.of(
                    FieldDefinition.builder("metaTitle", FieldType.STRING).maxLength(60).build(),
                    FieldDefinition.builder("metaDescription", FieldType.TEXT)
                        .maxLength(160)
                        .build()))
            .build();

    assertEquals("shared.seo", comp.getUid());
    assertEquals("shared", comp.getCategory());
    assertEquals("SEO", comp.getDisplayName());
    assertEquals(2, comp.getFields().size());
    assertNotNull(comp.getField("metaTitle"));
    assertNotNull(comp.getField("metaDescription"));
    assertNull(comp.getField("nonexistent"));
  }

  @Test
  void shouldBuildMinimalComponent() {
    var comp = ComponentDefinition.builder("shared.quote").build();

    assertEquals("shared.quote", comp.getUid());
    assertTrue(comp.getFields().isEmpty());
  }
}
