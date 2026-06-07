package com.quarkus.cms.core.schema.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.model.RelationType;
import com.quarkus.cms.core.schema.storage.SchemaCache;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaValidatorTest {

  private SchemaValidator validator;
  private SchemaCache cache;

  @BeforeEach
  void setUp() {
    cache = new SchemaCache();
    validator = new SchemaValidator(cache);
  }

  @Test
  void shouldAcceptValidContentType() {
    // Register the target content type so relation target validation passes
    cache.putContentType(
        ContentTypeDefinition.builder("api::author.author", ContentTypeKind.COLLECTION_TYPE)
            .displayName("Author")
            .build());

    var ct = validArticleCT();
    var errors = validator.validateContentType(ct);
    assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
  }

  @Test
  void shouldRejectBlankUid() {
    // Builder.build() already rejects blank UIDs, so test that directly
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              ContentTypeDefinition.builder("  ", ContentTypeKind.COLLECTION_TYPE).build();
            });
    assertTrue(ex.getMessage().contains("uid"));
  }

  @Test
  void shouldRejectMissingKind() {
    // Test that building without a kind fails
    // Use a non-blank UID but missing kind via the no-arg builder
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              new ContentTypeDefinition.Builder().uid("api::test.test").build();
            });
    assertTrue(ex.getMessage().contains("kind"));
  }

  @Test
  void shouldRejectDuplicateFieldNames() {
    var ct =
        ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(
                List.of(
                    FieldDefinition.builder("title", FieldType.STRING).build(),
                    FieldDefinition.builder("title", FieldType.TEXT).build()))
            .build();
    var errors = validator.validateContentType(ct);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate field name")));
  }

  @Test
  void shouldRejectMinGreaterThanMax() {
    var ct =
        ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(
                List.of(
                    FieldDefinition.builder("rating", FieldType.INTEGER).min(10).max(1).build()))
            .build();
    var errors = validator.validateContentType(ct);
    assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds max")));
  }

  @Test
  void shouldRejectMinLengthGreaterThanMaxLength() {
    var ct =
        ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(
                List.of(
                    FieldDefinition.builder("title", FieldType.STRING)
                        .minLength(100)
                        .maxLength(10)
                        .build()))
            .build();
    var errors = validator.validateContentType(ct);
    assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds maxLength")));
  }

  @Test
  void shouldRejectEnumerationWithoutValues() {
    var ct =
        ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(List.of(FieldDefinition.builder("status", FieldType.ENUMERATION).build()))
            .build();
    var errors = validator.validateContentType(ct);
    assertTrue(
        errors.stream()
            .anyMatch(e -> e.contains("enumeration field must have at least one enum value")));
  }

  @Test
  void shouldRejectComponentFieldWithoutComponentUid() {
    var ct =
        ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(List.of(FieldDefinition.builder("seo", FieldType.COMPONENT).build()))
            .build();
    var errors = validator.validateContentType(ct);
    assertTrue(
        errors.stream().anyMatch(e -> e.contains("component field must specify a component UID")));
  }

  @Test
  void shouldRejectComponentFieldWithUnknownComponent() {
    var ct =
        ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(
                List.of(
                    FieldDefinition.builder("seo", FieldType.COMPONENT)
                        .component("shared.nonexistent")
                        .build()))
            .build();
    var errors = validator.validateContentType(ct);
    assertTrue(errors.stream().anyMatch(e -> e.contains("references unknown component")));
  }

  @Test
  void shouldRejectDuplicateRelationNames() {
    var ct =
        ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .relations(
                List.of(
                    RelationDefinition.builder(
                            "author", RelationType.MANY_TO_ONE, "api::author.author")
                        .build(),
                    RelationDefinition.builder(
                            "author", RelationType.MANY_TO_MANY, "api::author.author")
                        .build()))
            .build();
    var errors = validator.validateContentType(ct);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate relation name")));
  }

  @Test
  void shouldAcceptValidComponent() {
    var comp =
        ComponentDefinition.builder("shared.seo")
            .fields(List.of(FieldDefinition.builder("metaTitle", FieldType.STRING).build()))
            .build();
    var errors = validator.validateComponent(comp);
    assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
  }

  @Test
  void shouldRejectDuplicateComponentFieldNames() {
    var comp =
        ComponentDefinition.builder("shared.seo")
            .fields(
                List.of(
                    FieldDefinition.builder("metaTitle", FieldType.STRING).build(),
                    FieldDefinition.builder("metaTitle", FieldType.TEXT).build()))
            .build();
    var errors = validator.validateComponent(comp);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate field name")));
  }

  private ContentTypeDefinition validArticleCT() {
    return ContentTypeDefinition.builder("api::article.article", ContentTypeKind.COLLECTION_TYPE)
        .displayName("Article")
        .fields(
            List.of(
                FieldDefinition.builder("title", FieldType.STRING)
                    .required(true)
                    .maxLength(255)
                    .build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()))
        .relations(
            List.of(
                RelationDefinition.builder("author", RelationType.MANY_TO_ONE, "api::author.author")
                    .targetAttribute("articles")
                    .build()))
        .build();
  }
}
