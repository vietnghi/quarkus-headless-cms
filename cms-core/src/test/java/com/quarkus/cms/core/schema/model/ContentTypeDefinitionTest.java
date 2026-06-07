package com.quarkus.cms.core.schema.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ContentTypeDefinitionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldBuildCollectionType() {
    var ct = ContentTypeBuilderHelper.createArticle();

    assertEquals("api::article.article", ct.getUid());
    assertEquals(ContentTypeKind.COLLECTION_TYPE, ct.getKind());
    assertTrue(ct.isCollectionType());
    assertFalse(ct.isSingleType());
    assertEquals("article", ct.getSingularName());
    assertEquals("articles", ct.getPluralName());
    assertEquals("Article", ct.getDisplayName());
  }

  @Test
  void shouldBuildSingleType() {
    var ct =
        ContentTypeDefinition.builder("api::homepage.homepage", ContentTypeKind.SINGLE_TYPE)
            .singularName("homepage")
            .pluralName("homepages")
            .displayName("Homepage")
            .build();

    assertEquals(ContentTypeKind.SINGLE_TYPE, ct.getKind());
    assertTrue(ct.isSingleType());
    assertFalse(ct.isCollectionType());
  }

  @Test
  void shouldLookupFieldByName() {
    var ct = ContentTypeBuilderHelper.createArticle();

    var titleField = ct.getField("title");
    assertNotNull(titleField);
    assertEquals(FieldType.STRING, titleField.getType());

    var bodyField = ct.getField("body");
    assertNotNull(bodyField);
    assertEquals(FieldType.RICHTEXT, bodyField.getType());

    assertNull(ct.getField("nonexistent"));
  }

  @Test
  void shouldFindRelationsToTarget() {
    var ct = ContentTypeBuilderHelper.createArticle();

    var authorRels = ct.getRelationsTo("api::author.author");
    assertEquals(1, authorRels.size());
    assertEquals("author", authorRels.get(0).getFieldName());

    var emptyRels = ct.getRelationsTo("api::nonexistent.nonexistent");
    assertTrue(emptyRels.isEmpty());
  }

  @Test
  void shouldFindDynamicZone() {
    var ct = ContentTypeBuilderHelper.createArticle();

    var zone = ct.getDynamicZone("contentBlocks");
    assertNotNull(zone);
    assertEquals(2, zone.getComponents().size());

    assertNull(ct.getDynamicZone("nonexistent"));
  }

  @Test
  void shouldHaveDraftAndPublishByDefault() {
    var ct = ContentTypeBuilderHelper.createArticle();
    assertTrue(ct.isDraftAndPublish());
  }

  @Test
  void shouldRoundTripThroughJson() throws Exception {
    var ct = ContentTypeBuilderHelper.createArticle();

    String json = MAPPER.writeValueAsString(ct);
    var deserialized = MAPPER.readValue(json, ContentTypeDefinition.class);

    assertEquals(ct.getUid(), deserialized.getUid());
    assertEquals(ct.getKind(), deserialized.getKind());
    assertEquals(ct.getFields().size(), deserialized.getFields().size());
    assertEquals(ct.getRelations().size(), deserialized.getRelations().size());

    // Verify field lookup still works after deserialization
    assertNotNull(deserialized.getField("title"));
    assertEquals(FieldType.STRING, deserialized.getField("title").getType());
  }

  @Test
  void shouldHandleEmptyFields() {
    var ct =
        ContentTypeDefinition.builder("api::empty.empty", ContentTypeKind.COLLECTION_TYPE)
            .displayName("Empty")
            .build();

    assertTrue(ct.getFields().isEmpty());
    assertTrue(ct.getRelations().isEmpty());
    assertTrue(ct.getDynamicZones().isEmpty());
    assertTrue(ct.getFieldsByName().isEmpty());
  }

  // Helper inner class to avoid circular dependency with builder tests
  static class ContentTypeBuilderHelper {
    static ContentTypeDefinition createArticle() {
      return ContentTypeDefinition.builder("api::article.article", ContentTypeKind.COLLECTION_TYPE)
          .singularName("article")
          .pluralName("articles")
          .displayName("Article")
          .fields(
              List.of(
                  FieldDefinition.builder("title", FieldType.STRING)
                      .required(true)
                      .maxLength(255)
                      .build(),
                  FieldDefinition.builder("body", FieldType.RICHTEXT).build(),
                  FieldDefinition.builder("published", FieldType.BOOLEAN)
                      .defaultValue("false")
                      .build()))
          .relations(
              List.of(
                  RelationDefinition.builder(
                          "author", RelationType.MANY_TO_ONE, "api::author.author")
                      .targetAttribute("articles")
                      .build()))
          .dynamicZones(
              List.of(
                  DynamicZoneDefinition.builder("contentBlocks")
                      .components(List.of("shared.quote", "shared.slider"))
                      .min(0)
                      .max(10)
                      .build()))
          .build();
    }
  }
}
