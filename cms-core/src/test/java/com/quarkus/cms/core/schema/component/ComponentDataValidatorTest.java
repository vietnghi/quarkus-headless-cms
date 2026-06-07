package com.quarkus.cms.core.schema.component;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaCache;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ComponentDataValidator}.
 */
class ComponentDataValidatorTest {

  private ComponentDataValidator validator;
  private SchemaCache cache;

  @BeforeEach
  void setUp() {
    cache = new SchemaCache();
    validator = new ComponentDataValidator();
    // Create a stub SchemaStorageService backed by the cache
    validator.setSchemaStorage(new SchemaCacheBackedStorage(cache));
  }

  // ---- Required fields ----

  @Test
  void shouldAcceptValidData() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("title", FieldType.STRING).build()));

    assertTrue(validator.validate(comp, Map.of("title", "Hello")).isEmpty());
  }

  @Test
  void shouldRejectMissingRequiredField() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("title", FieldType.STRING).required(true).build()));

    List<String> errors = validator.validate(comp, Map.of());
    assertTrue(errors.stream().anyMatch(e -> e.contains("'title'") && e.contains("required")));
  }

  @Test
  void shouldAcceptOptionalAbsentField() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("title", FieldType.STRING).required(false).build()));

    assertTrue(validator.validate(comp, Map.of()).isEmpty());
  }

  // ---- String constraints ----

  @Test
  void shouldRejectStringTooShort() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("title", FieldType.STRING).minLength(5).build()));

    List<String> errors = validator.validate(comp, Map.of("title", "ab"));
    assertTrue(errors.stream().anyMatch(e -> e.contains("less than minimum")));
  }

  @Test
  void shouldRejectStringTooLong() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("title", FieldType.STRING).maxLength(5).build()));

    List<String> errors = validator.validate(comp, Map.of("title", "abcdef"));
    assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds maximum")));
  }

  @Test
  void shouldRejectStringNotMatchingRegex() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("slug", FieldType.STRING).regex("^[a-z0-9-]+$").build()));

    List<String> errors = validator.validate(comp, Map.of("slug", "Bad Slug!"));
    assertTrue(errors.stream().anyMatch(e -> e.contains("does not match pattern")));
  }

  @Test
  void shouldAcceptStringMatchingRegex() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("slug", FieldType.STRING).regex("^[a-z0-9-]+$").build()));

    assertTrue(validator.validate(comp, Map.of("slug", "hello-world-42")).isEmpty());
  }

  // ---- Numeric constraints ----

  @Test
  void shouldRejectIntegerBelowMin() {
    ComponentDefinition comp = comp("shared.stats", List.of(
        field("count", FieldType.INTEGER).min(1).build()));

    List<String> errors = validator.validate(comp, Map.of("count", 0));
    assertTrue(errors.stream().anyMatch(e -> e.contains("less than minimum")));
  }

  @Test
  void shouldRejectIntegerAboveMax() {
    ComponentDefinition comp = comp("shared.stats", List.of(
        field("count", FieldType.INTEGER).max(100).build()));

    List<String> errors = validator.validate(comp, Map.of("count", 101));
    assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds maximum")));
  }

  @Test
  void shouldAcceptIntegerInRange() {
    ComponentDefinition comp = comp("shared.stats", List.of(
        field("count", FieldType.INTEGER).min(1).max(100).build()));

    assertTrue(validator.validate(comp, Map.of("count", 50)).isEmpty());
  }

  // ---- Enumeration ----

  @Test
  void shouldRejectInvalidEnumValue() {
    ComponentDefinition comp = comp("shared.status", List.of(
        field("status", FieldType.ENUMERATION)
            .enumValues(List.of("draft", "published", "archived"))
            .build()));

    List<String> errors = validator.validate(comp, Map.of("status", "deleted"));
    assertTrue(errors.stream().anyMatch(e -> e.contains("not a valid enum value")));
  }

  @Test
  void shouldAcceptValidEnumValue() {
    ComponentDefinition comp = comp("shared.status", List.of(
        field("status", FieldType.ENUMERATION)
            .enumValues(List.of("draft", "published"))
            .build()));

    assertTrue(validator.validate(comp, Map.of("status", "draft")).isEmpty());
  }

  // ---- Boolean ----

  @Test
  void shouldRejectNonBoolean() {
    ComponentDefinition comp = comp("shared.features", List.of(
        field("enabled", FieldType.BOOLEAN).build()));

    List<String> errors = validator.validate(comp, Map.of("enabled", "yes"));
    assertTrue(errors.stream().anyMatch(e -> e.contains("must be a boolean")));
  }

  @Test
  void shouldAcceptBoolean() {
    ComponentDefinition comp = comp("shared.features", List.of(
        field("enabled", FieldType.BOOLEAN).build()));

    assertTrue(validator.validate(comp, Map.of("enabled", true)).isEmpty());
    assertTrue(validator.validate(comp, Map.of("enabled", false)).isEmpty());
  }

  // ---- Nested components ----

  @Test
  void shouldValidateNestedComponentData() {
    // Register leaf component in cache
    cache.putComponent(ComponentDefinition.builder("shared.image")
        .fields(List.of(
            field("url", FieldType.STRING).required(true).build(),
            field("alt", FieldType.STRING).maxLength(100).build()))
        .build());

    ComponentDefinition hero = comp("shared.hero", List.of(
        field("title", FieldType.STRING).required(true).build(),
        field("background", FieldType.COMPONENT).component("shared.image").build()));

    // Valid nested data
    assertTrue(validator.validate(hero, Map.of(
        "title", "Welcome",
        "background", Map.of("url", "https://example.com/img.jpg", "alt", "Example"))).isEmpty());

    // Missing required nested field
    List<String> errors = validator.validate(hero, Map.of(
        "title", "Welcome",
        "background", Map.of("alt", "Example")));
    assertTrue(errors.stream().anyMatch(e -> e.contains("'url'") && e.contains("required")));
  }

  @Test
  void shouldValidateRepeatableComponent() {
    cache.putComponent(ComponentDefinition.builder("shared.link")
        .fields(List.of(
            field("label", FieldType.STRING).required(true).build(),
            field("url", FieldType.STRING).required(true).build()))
        .build());

    ComponentDefinition nav = comp("shared.nav", List.of(
        field("links", FieldType.COMPONENT)
            .component("shared.link")
            .repeatable(true)
            .minComponents(1)
            .maxComponents(5)
            .build()));

    // Valid repeatable data
    assertTrue(validator.validate(nav, Map.of(
        "links", List.of(
            Map.of("label", "Home", "url", "/"),
            Map.of("label", "About", "url", "/about")))).isEmpty());

    // Too few components
    List<String> errors = validator.validate(nav, Map.of("links", List.of()));
    assertTrue(errors.stream().anyMatch(e -> e.contains("requires at least")));

    // Missing required in nested item
    errors = validator.validate(nav, Map.of(
        "links", List.of(Map.of("label", "Home"))));
    assertTrue(errors.stream().anyMatch(e -> e.contains("'url'") && e.contains("required")));
  }

  // ---- Empty/null data ----

  @Test
  void shouldHandleNullData() {
    ComponentDefinition comp = comp("shared.seo", List.of(
        field("title", FieldType.STRING).build()));

    List<String> errors = validator.validate(comp, null);
    assertTrue(errors.stream().anyMatch(e -> e.contains("is null")));
  }

  @Test
  void shouldHandleEmptyData() {
    ComponentDefinition comp = comp("shared.seo", List.of());
    assertTrue(validator.validate(comp, Map.of()).isEmpty());
  }

  // ---- Date field types ----

  @Test
  void shouldRejectNonStringDate() {
    ComponentDefinition comp = comp("shared.event", List.of(
        field("eventDate", FieldType.DATE).build()));

    List<String> errors = validator.validate(comp, Map.of("eventDate", 12345));
    assertTrue(errors.stream().anyMatch(e -> e.contains("must be a date/time string")));
  }

  @Test
  void shouldAcceptStringDate() {
    ComponentDefinition comp = comp("shared.event", List.of(
        field("eventDate", FieldType.DATE).build()));

    assertTrue(validator.validate(comp, Map.of("eventDate", "2024-06-07")).isEmpty());
  }

  // ---- Media field ----

  @Test
  void shouldAcceptMediaObject() {
    ComponentDefinition comp = comp("shared.gallery", List.of(
        field("image", FieldType.MEDIA).build()));

    assertTrue(validator.validate(comp, Map.of("image", Map.of("id", 1, "url", "/img.jpg"))).isEmpty());
  }

  @Test
  void shouldAcceptMediaId() {
    ComponentDefinition comp = comp("shared.gallery", List.of(
        field("image", FieldType.MEDIA).build()));

    assertTrue(validator.validate(comp, Map.of("image", 42)).isEmpty());
  }

  // ---- Multiple field types ----

  @Test
  void shouldValidateMultiFieldComponent() {
    ComponentDefinition comp = comp("shared.article", List.of(
        field("title", FieldType.STRING).required(true).maxLength(200).build(),
        field("views", FieldType.INTEGER).min(0).build(),
        field("published", FieldType.BOOLEAN).build(),
        field("status", FieldType.ENUMERATION)
            .enumValues(List.of("draft", "published")).build()));

    assertTrue(validator.validate(comp, Map.of(
        "title", "Hello World",
        "views", 42,
        "published", true,
        "status", "published")).isEmpty());

    // Title too long
    List<String> errors = validator.validate(comp, Map.of("title", "x".repeat(201)));
    assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds maximum")),
        "Should catch title too long, got: " + errors);
  }

  // ---- helpers ----

  private ComponentDefinition comp(String uid, List<FieldDefinition> fields) {
    return ComponentDefinition.builder(uid).displayName(uid).fields(fields).build();
  }

  private static FieldDefinition.Builder field(String name, FieldType type) {
    return FieldDefinition.builder(name, type);
  }

  /**
   * Minimal SchemaStorageService stub that looks up components from a SchemaCache.
   */
  static class SchemaCacheBackedStorage extends SchemaStorageService {
    private final SchemaCache cache;

    SchemaCacheBackedStorage(SchemaCache cache) {
      this.cache = cache;
    }

    @Override
    public ComponentDefinition getComponent(String uid) {
      return cache.getComponent(uid);
    }
  }
}
