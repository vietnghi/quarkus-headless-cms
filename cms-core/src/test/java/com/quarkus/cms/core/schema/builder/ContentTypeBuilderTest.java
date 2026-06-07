package com.quarkus.cms.core.schema.builder;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ContentTypeBuilderTest {

  @Test
  void shouldBuildCollectionType() {
    var ct =
        ContentTypeBuilder.create("api::article.article", ContentTypeKind.COLLECTION_TYPE)
            .singularName("article")
            .pluralName("articles")
            .displayName("Article")
            .description("A blog article")
            .addField(
                FieldDefinition.builder("title", FieldType.STRING)
                    .required(true)
                    .maxLength(255)
                    .build())
            .addField(FieldDefinition.builder("body", FieldType.RICHTEXT).build())
            .addRelation(
                RelationDefinition.builder("author", RelationType.MANY_TO_ONE, "api::author.author")
                    .targetAttribute("articles")
                    .build())
            .addDynamicZone(
                DynamicZoneDefinition.builder("contentBlocks")
                    .components(List.of("shared.quote", "shared.slider"))
                    .min(0)
                    .max(10)
                    .build())
            .build();

    assertEquals("api::article.article", ct.getUid());
    assertEquals(ContentTypeKind.COLLECTION_TYPE, ct.getKind());
    assertEquals("article", ct.getSingularName());
    assertEquals("articles", ct.getPluralName());
    assertEquals("Article", ct.getDisplayName());
    assertEquals("A blog article", ct.getDescription());
    assertEquals(2, ct.getFields().size());
    assertEquals(1, ct.getRelations().size());
    assertEquals(1, ct.getDynamicZones().size());
    assertTrue(ct.isDraftAndPublish());
  }

  @Test
  void shouldBuildSingleType() {
    var ct =
        ContentTypeBuilder.create("api::homepage.homepage", ContentTypeKind.SINGLE_TYPE)
            .displayName("Homepage")
            .draftAndPublish(false)
            .localized(true)
            .build();

    assertEquals(ContentTypeKind.SINGLE_TYPE, ct.getKind());
    assertFalse(ct.isDraftAndPublish());
    assertTrue(ct.isLocalized());
  }

  @Test
  void shouldAddMultipleFields() {
    var ct =
        ContentTypeBuilder.create("api::product.product", ContentTypeKind.COLLECTION_TYPE)
            .addField(FieldDefinition.builder("name", FieldType.STRING).build())
            .addField(FieldDefinition.builder("price", FieldType.FLOAT).min(0).build())
            .addField(FieldDefinition.builder("description", FieldType.TEXT).build())
            .addField(
                FieldDefinition.builder("inStock", FieldType.BOOLEAN).defaultValue("true").build())
            .build();

    assertEquals(4, ct.getFields().size());
  }

  @Test
  void shouldAddComponents() {
    var ct =
        ContentTypeBuilder.create("api::page.page", ContentTypeKind.COLLECTION_TYPE)
            .addComponent("shared.seo")
            .addComponent("shared.hero")
            .build();

    assertEquals(2, ct.getComponents().size());
    assertTrue(ct.getComponents().contains("shared.seo"));
  }

  @Test
  void shouldAddRelationsWithMorphType() {
    var ct =
        ContentTypeBuilder.create("api::media.media", ContentTypeKind.COLLECTION_TYPE)
            .addRelation(
                RelationDefinition.builder("related", RelationType.MORPH_TO_MANY, "*")
                    .morphColumnType("related_type")
                    .build())
            .build();

    assertEquals(1, ct.getRelations().size());
    var rel = ct.getRelations().get(0);
    assertEquals(RelationType.MORPH_TO_MANY, rel.getType());
    assertTrue(rel.isMorph());
    assertEquals("related_type", rel.getMorphColumnType());
  }
}
