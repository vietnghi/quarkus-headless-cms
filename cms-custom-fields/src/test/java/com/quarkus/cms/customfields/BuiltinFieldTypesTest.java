package com.quarkus.cms.customfields;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.customfields.type.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Tests for all built-in field type implementations. */
class BuiltinFieldTypesTest {

  // ---- String Field Type ----

  @Test
  void stringFieldTypeAcceptsText() {
    var type = new StringFieldType();
    assertDoesNotThrow(() -> type.validate("title", "Hello World", Map.of()));
  }

  @Test
  void stringFieldTypeRejectsNonString() {
    var type = new StringFieldType();
    assertThrows(IllegalArgumentException.class, () -> type.validate("num", 42, Map.of()));
  }

  @Test
  void stringFieldTypeValidatesEmail() {
    var type = new StringFieldType();
    assertDoesNotThrow(
        () -> type.validate("email", "user@example.com", Map.of("subType", "email")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("email", "not-an-email", Map.of("subType", "email")));
  }

  @Test
  void stringFieldTypeValidatesUrl() {
    var type = new StringFieldType();
    assertDoesNotThrow(() -> type.validate("url", "https://example.com", Map.of("subType", "url")));
    assertDoesNotThrow(() -> type.validate("url", "/relative/path", Map.of("subType", "url")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("url", "not a url", Map.of("subType", "url")));
  }

  @Test
  void stringFieldTypeEnforcesLengthConstraints() {
    var type = new StringFieldType();
    assertDoesNotThrow(
        () -> type.validate("name", "Hello", Map.of("minLength", 1, "maxLength", 10)));
    assertThrows(
        IllegalArgumentException.class, () -> type.validate("name", "Hi", Map.of("minLength", 5)));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("name", "Too long string here", Map.of("maxLength", 10)));
  }

  @Test
  void stringFieldTypeValidatesRegex() {
    var type = new StringFieldType();
    assertDoesNotThrow(() -> type.validate("code", "ABC123", Map.of("regex", "^[A-Z]{3}\\d{3}$")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("code", "invalid", Map.of("regex", "^[A-Z]{3}\\d{3}$")));
  }

  @Test
  void stringFieldTypeCoercesToString() {
    var type = new StringFieldType();
    assertEquals("42", type.coerce(42));
    assertEquals("hello", type.coerce("hello"));
    assertNull(type.coerce(null));
  }

  @Test
  void stringFieldTypeHandlesSubTypes() {
    assertTrue(StringFieldType.handlesSubType("text"));
    assertTrue(StringFieldType.handlesSubType("email"));
    assertTrue(StringFieldType.handlesSubType("richtext"));
    assertFalse(StringFieldType.handlesSubType("unknown"));
  }

  // ---- Number Field Type ----

  @Test
  void numberFieldTypeAcceptsIntegers() {
    var type = new NumberFieldType();
    assertDoesNotThrow(() -> type.validate("age", 42, Map.of("subType", "integer")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("age", 3.14, Map.of("subType", "integer")));
  }

  @Test
  void numberFieldTypeAcceptsFloats() {
    var type = new NumberFieldType();
    assertDoesNotThrow(() -> type.validate("price", 3.14, Map.of("subType", "float")));
    assertDoesNotThrow(() -> type.validate("price", 42, Map.of("subType", "float")));
  }

  @Test
  void numberFieldTypeRejectsNonNumeric() {
    var type = new NumberFieldType();
    assertThrows(IllegalArgumentException.class, () -> type.validate("count", "hello", Map.of()));
  }

  @Test
  void numberFieldTypeEnforcesRange() {
    var type = new NumberFieldType();
    assertDoesNotThrow(() -> type.validate("age", 25, Map.of("min", 0, "max", 150)));
    assertThrows(IllegalArgumentException.class, () -> type.validate("age", -1, Map.of("min", 0)));
    assertThrows(
        IllegalArgumentException.class, () -> type.validate("age", 200, Map.of("max", 150)));
  }

  @Test
  void numberFieldTypeCoercesFromString() {
    var type = new NumberFieldType();
    assertEquals(42L, type.coerce("42"));
    assertEquals(3.14, type.coerce("3.14"));
    assertEquals("not-a-number", type.coerce("not-a-number"));
  }

  @Test
  void numberFieldTypeSupportsFeatures() {
    var type = new NumberFieldType();
    assertTrue(type.supportsUnique());
    assertTrue(type.supportsRangeConstraints());
    assertFalse(type.supportsLengthConstraints());
  }

  // ---- Boolean Field Type ----

  @Test
  void booleanFieldTypeAcceptsBooleans() {
    var type = new BooleanFieldType();
    assertDoesNotThrow(() -> type.validate("flag", true, Map.of()));
    assertDoesNotThrow(() -> type.validate("flag", false, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> type.validate("flag", "true", Map.of()));
  }

  @Test
  void booleanFieldTypeCoerces() {
    var type = new BooleanFieldType();
    assertEquals(true, type.coerce(true));
    assertEquals(true, type.coerce("true"));
    assertEquals(true, type.coerce(1));
    assertEquals(false, type.coerce("false"));
    assertEquals(false, type.coerce(0));
    assertNull(type.coerce(null));
  }

  // ---- Date Field Type ----

  @Test
  void dateFieldTypeValidatesDate() {
    var type = new DateFieldType();
    assertDoesNotThrow(() -> type.validate("date", "2024-01-15", Map.of("subType", "date")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("date", "invalid-date", Map.of("subType", "date")));
  }

  @Test
  void dateFieldTypeValidatesDateTime() {
    var type = new DateFieldType();
    assertDoesNotThrow(
        () -> type.validate("dt", "2024-01-15T14:30:00", Map.of("subType", "datetime")));
  }

  @Test
  void dateFieldTypeValidatesTime() {
    var type = new DateFieldType();
    assertDoesNotThrow(() -> type.validate("time", "14:30:00", Map.of("subType", "time")));
  }

  @Test
  void dateFieldTypeRejectsNonString() {
    var type = new DateFieldType();
    assertThrows(IllegalArgumentException.class, () -> type.validate("dt", 42, Map.of()));
  }

  // ---- Enumeration Field Type ----

  @Test
  void enumerationFieldTypeValidatesAllowedValues() {
    var type = new EnumerationFieldType();
    assertDoesNotThrow(
        () ->
            type.validate(
                "status",
                "draft",
                Map.of("enumValues", List.of("draft", "published", "archived"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            type.validate(
                "status", "deleted", Map.of("enumValues", List.of("draft", "published"))));
  }

  @Test
  void enumerationFieldTypeRejectsNonString() {
    var type = new EnumerationFieldType();
    assertThrows(IllegalArgumentException.class, () -> type.validate("status", 42, Map.of()));
  }

  // ---- JSON Field Type ----

  @Test
  void jsonFieldTypeAcceptsMapAndList() {
    var type = new JsonFieldType();
    assertDoesNotThrow(() -> type.validate("data", Map.of("key", "value"), Map.of()));
    assertDoesNotThrow(() -> type.validate("data", List.of(1, 2, 3), Map.of()));
    assertThrows(
        IllegalArgumentException.class, () -> type.validate("data", "string-value", Map.of()));
  }

  // ---- Media Field Type ----

  @Test
  void mediaFieldTypeSingle() {
    var type = new MediaFieldType();
    assertDoesNotThrow(() -> type.validate("img", "asset-123", Map.of("subType", "single")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("img", 42, Map.of("subType", "single")));
  }

  @Test
  void mediaFieldTypeMultiple() {
    var type = new MediaFieldType();
    assertDoesNotThrow(
        () ->
            type.validate("gallery", List.of("asset-1", "asset-2"), Map.of("subType", "multiple")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("gallery", "single-string", Map.of("subType", "multiple")));
  }

  // ---- Relation Field Type ----

  @Test
  void relationFieldTypeOneToOne() {
    var type = new RelationFieldType();
    assertDoesNotThrow(
        () ->
            type.validate(
                "author",
                Map.of("documentId", "abc-123"),
                Map.of("subType", "oneToOne", "targetContentType", "api::user.user")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            type.validate(
                "author",
                "not-a-map",
                Map.of("subType", "oneToOne", "targetContentType", "api::user.user")));
  }

  @Test
  void relationFieldTypeManyToMany() {
    var type = new RelationFieldType();
    assertDoesNotThrow(
        () ->
            type.validate(
                "tags",
                List.of(Map.of("documentId", "tag-1"), Map.of("documentId", "tag-2")),
                Map.of("subType", "manyToMany", "targetContentType", "api::tag.tag")));
  }

  @Test
  void relationFieldTypeRequiresTargetContentType() {
    var type = new RelationFieldType();
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("author", Map.of("documentId", "abc"), Map.of("subType", "oneToOne")));
  }

  // ---- Component Field Type ----

  @Test
  void componentFieldTypeNonRepeatable() {
    var type = new ComponentFieldType();
    assertDoesNotThrow(
        () -> type.validate("seo", Map.of("title", "Hello"), Map.of("component", "default.seo")));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("seo", "not-a-map", Map.of("component", "default.seo")));
  }

  @Test
  void componentFieldTypeRepeatable() {
    var type = new ComponentFieldType();
    assertDoesNotThrow(
        () ->
            type.validate(
                "faq",
                List.of(Map.of("q", "Q1"), Map.of("q", "Q2")),
                Map.of("component", "default.faq", "repeatable", true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            type.validate(
                "faq", Map.of("q", "Q1"), Map.of("component", "default.faq", "repeatable", true)));
  }

  @Test
  void componentFieldTypeRequiresComponent() {
    var type = new ComponentFieldType();
    assertThrows(
        IllegalArgumentException.class, () -> type.validate("seo", Map.of("title", "H"), Map.of()));
  }

  // ---- Dynamic Zone Field Type ----

  @Test
  void dynamicZoneFieldTypeAcceptsValidComponents() {
    var type = new DynamicZoneFieldType();
    assertDoesNotThrow(
        () ->
            type.validate(
                "content",
                List.of(Map.of("__component", "default.hero")),
                Map.of("allowedComponents", List.of("default.hero", "default.text"))));
  }

  @Test
  void dynamicZoneFieldTypeRejectsInvalidComponents() {
    var type = new DynamicZoneFieldType();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            type.validate(
                "content",
                List.of(Map.of("__component", "default.unknown")),
                Map.of("allowedComponents", List.of("default.hero", "default.text"))));
  }

  @Test
  void dynamicZoneFieldTypeEnforcesMinMax() {
    var type = new DynamicZoneFieldType();
    assertDoesNotThrow(
        () ->
            type.validate(
                "content",
                List.of(Map.of("__component", "default.hero")),
                Map.of("minComponents", 1, "maxComponents", 3)));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("content", List.of(), Map.of("minComponents", 1)));
  }

  @Test
  void dynamicZoneFieldTypeRejectsNonList() {
    var type = new DynamicZoneFieldType();
    assertThrows(
        IllegalArgumentException.class, () -> type.validate("zone", "not-a-list", Map.of()));
  }
}
