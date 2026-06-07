package com.quarkus.cms.core.schema.builder;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;

import org.junit.jupiter.api.Test;

class ComponentBuilderTest {

  @Test
  void shouldBuildComponent() {
    var comp =
        ComponentBuilder.create("shared.seo")
            .category("shared")
            .displayName("SEO")
            .description("SEO metadata")
            .addField(FieldDefinition.builder("metaTitle", FieldType.STRING).maxLength(60).build())
            .addField(
                FieldDefinition.builder("metaDescription", FieldType.TEXT).maxLength(160).build())
            .build();

    assertEquals("shared.seo", comp.getUid());
    assertEquals("shared", comp.getCategory());
    assertEquals("SEO", comp.getDisplayName());
    assertEquals(2, comp.getFields().size());
  }

  @Test
  void shouldBuildMinimalComponent() {
    var comp = ComponentBuilder.create("shared.quote").build();
    assertEquals("shared.quote", comp.getUid());
    assertTrue(comp.getFields().isEmpty());
  }

  @Test
  void shouldAddMultipleFields() {
    var comp =
        ComponentBuilder.create("shared.hero")
            .addField(FieldDefinition.builder("title", FieldType.STRING).required(true).build())
            .addField(FieldDefinition.builder("subtitle", FieldType.STRING).build())
            .addField(FieldDefinition.builder("image", FieldType.MEDIA).build())
            .build();

    assertEquals(3, comp.getFields().size());
  }
}
