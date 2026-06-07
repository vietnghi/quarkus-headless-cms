package com.quarkus.cms.core.schema.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class FieldDefinitionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldBuildStringField() {
    var field =
        FieldDefinition.builder("title", FieldType.STRING).required(true).maxLength(255).build();

    assertEquals("title", field.getName());
    assertEquals(FieldType.STRING, field.getType());
    assertTrue(field.isRequired());
    assertEquals(255, field.getMaxLength());
    assertFalse(field.isUnique());
    assertTrue(field.getType().isScalar());
  }

  @Test
  void shouldBuildTextField() {
    var field = FieldDefinition.builder("body", FieldType.TEXT).build();
    assertEquals(FieldType.TEXT, field.getType());
    assertTrue(field.getType().isScalar());
  }

  @Test
  void shouldBuildIntegerField() {
    var field =
        FieldDefinition.builder("views", FieldType.INTEGER).min(0).defaultValue("0").build();
    assertEquals(FieldType.INTEGER, field.getType());
    assertEquals(0, field.getMin());
    assertEquals("0", field.getDefaultValue());
  }

  @Test
  void shouldBuildFloatField() {
    var field = FieldDefinition.builder("price", FieldType.FLOAT).min(0).build();
    assertEquals(FieldType.FLOAT, field.getType());
    assertEquals(0, field.getMin());
  }

  @Test
  void shouldBuildDecimalField() {
    var field = FieldDefinition.builder("rating", FieldType.DECIMAL).min(0).max(5).build();
    assertEquals(FieldType.DECIMAL, field.getType());
    assertEquals(0, field.getMin());
    assertEquals(5, field.getMax());
  }

  @Test
  void shouldBuildBooleanField() {
    var field =
        FieldDefinition.builder("published", FieldType.BOOLEAN).defaultValue("false").build();
    assertEquals(FieldType.BOOLEAN, field.getType());
    assertEquals("false", field.getDefaultValue());
  }

  @Test
  void shouldBuildDateField() {
    var field = FieldDefinition.builder("eventDate", FieldType.DATE).build();
    assertEquals(FieldType.DATE, field.getType());
  }

  @Test
  void shouldBuildDatetimeField() {
    var field = FieldDefinition.builder("publishedAt", FieldType.DATETIME).build();
    assertEquals(FieldType.DATETIME, field.getType());
  }

  @Test
  void shouldBuildTimeField() {
    var field = FieldDefinition.builder("startTime", FieldType.TIME).build();
    assertEquals(FieldType.TIME, field.getType());
  }

  @Test
  void shouldBuildEmailField() {
    var field =
        FieldDefinition.builder("email", FieldType.EMAIL)
            .required(true)
            .unique(true)
            .regex("^[\\\\w.-]+@[\\\\w.-]+\\\\.[a-zA-Z]{2,}$")
            .build();
    assertEquals(FieldType.EMAIL, field.getType());
    assertTrue(field.isRequired());
    assertTrue(field.isUnique());
    assertNotNull(field.getRegex());
  }

  @Test
  void shouldBuildPasswordField() {
    var field =
        FieldDefinition.builder("password", FieldType.PASSWORD)
            .minLength(8)
            .privateField(true)
            .build();
    assertEquals(FieldType.PASSWORD, field.getType());
    assertEquals(8, field.getMinLength());
    assertTrue(field.isPrivate());
  }

  @Test
  void shouldBuildUidField() {
    var field = FieldDefinition.builder("slug", FieldType.UID).build();
    assertEquals(FieldType.UID, field.getType());
  }

  @Test
  void shouldBuildEnumerationField() {
    var field =
        FieldDefinition.builder("status", FieldType.ENUMERATION)
            .enumValues(List.of("draft", "published", "archived"))
            .defaultValue("draft")
            .build();
    assertEquals(FieldType.ENUMERATION, field.getType());
    assertEquals(3, field.getEnumValues().size());
    assertTrue(field.getEnumValues().contains("draft"));
  }

  @Test
  void shouldBuildJsonField() {
    var field = FieldDefinition.builder("metadata", FieldType.JSON).build();
    assertEquals(FieldType.JSON, field.getType());
    assertTrue(field.getType().isScalar());
  }

  @Test
  void shouldBuildRichtextField() {
    var field = FieldDefinition.builder("description", FieldType.RICHTEXT).build();
    assertEquals(FieldType.RICHTEXT, field.getType());
    assertTrue(field.getType().isScalar());
  }

  @Test
  void shouldBuildMediaField() {
    var field = FieldDefinition.builder("cover", FieldType.MEDIA).repeatable(true).build();
    assertEquals(FieldType.MEDIA, field.getType());
    assertTrue(field.isRepeatable());
    assertFalse(field.getType().isScalar());
  }

  @Test
  void shouldBuildRelationField() {
    var field =
        FieldDefinition.builder("author", FieldType.RELATION).target("api::author.author").build();
    assertEquals(FieldType.RELATION, field.getType());
    assertEquals("api::author.author", field.getTarget());
    assertTrue(field.getType().isRelation());
  }

  @Test
  void shouldBuildComponentField() {
    var field = FieldDefinition.builder("seo", FieldType.COMPONENT).component("shared.seo").build();
    assertEquals(FieldType.COMPONENT, field.getType());
    assertEquals("shared.seo", field.getComponent());
    assertTrue(field.getType().isComponent());
  }

  @Test
  void shouldBuildDynamicZoneField() {
    var field =
        FieldDefinition.builder("contentBlocks", FieldType.DYNAMIC_ZONE)
            .allowedComponents(List.of("shared.quote", "shared.slider"))
            .minComponents(0)
            .maxComponents(10)
            .build();
    assertEquals(FieldType.DYNAMIC_ZONE, field.getType());
    assertEquals(2, field.getAllowedComponents().size());
    assertEquals(0, field.getMinComponents());
    assertEquals(10, field.getMaxComponents());
    assertTrue(field.getType().isComponent());
  }

  @Test
  void shouldRoundTripThroughJson() throws Exception {
    var field =
        FieldDefinition.builder("title", FieldType.STRING).required(true).maxLength(100).build();

    String json = MAPPER.writeValueAsString(field);
    var deserialized = MAPPER.readValue(json, FieldDefinition.class);

    assertEquals(field.getName(), deserialized.getName());
    assertEquals(field.getType(), deserialized.getType());
    assertEquals(field.isRequired(), deserialized.isRequired());
    assertEquals(field.getMaxLength(), deserialized.getMaxLength());
  }
}
