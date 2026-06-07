package com.quarkus.cms.customfields;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.customfields.example.ColorPickerFieldType;
import com.quarkus.cms.customfields.example.SlugFieldType;
import com.quarkus.cms.customfields.example.UuidFieldType;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Tests for example custom field type implementations. */
class ExampleCustomFieldTypesTest {

  // ---- Color Picker ----

  @Test
  void colorPickerAcceptsHexColors() {
    var type = new ColorPickerFieldType();
    assertDoesNotThrow(() -> type.validate("color", "#ff6633", Map.of()));
    assertDoesNotThrow(() -> type.validate("color", "#FFF", Map.of()));
    assertDoesNotThrow(() -> type.validate("color", "#ff6633cc", Map.of()));
    assertThrows(
        IllegalArgumentException.class, () -> type.validate("color", "not-a-color", Map.of()));
    assertThrows(IllegalArgumentException.class, () -> type.validate("color", "123456", Map.of()));
  }

  @Test
  void colorPickerCoercesValues() {
    var type = new ColorPickerFieldType();
    assertEquals("#FF6633", type.coerce("#ff6633"));
    assertEquals("#FF6633", type.coerce("ff6633"));
    assertNull(type.coerce(null));
  }

  @Test
  void colorPickerHasPluginOptions() {
    var type = new ColorPickerFieldType();
    assertTrue(type.getPluginOptions().containsKey("presetColors"));
    assertFalse((Boolean) type.getPluginOptions().get("showAlpha"));
  }

  // ---- Slug ----

  @Test
  void slugAcceptsValidSlugs() {
    var type = new SlugFieldType();
    assertDoesNotThrow(() -> type.validate("slug", "hello-world", Map.of()));
    assertDoesNotThrow(() -> type.validate("slug", "my-post-123", Map.of()));
    assertThrows(
        IllegalArgumentException.class, () -> type.validate("slug", "Hello World", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> type.validate("slug", "_leading_underscore", Map.of()));
  }

  @Test
  void slugCoercesToSlugFormat() {
    var type = new SlugFieldType();
    assertEquals("hello-world", type.coerce("Hello World"));
    assertEquals("my-post", type.coerce("  My Post!!  "));
    assertEquals("a-b-c", type.coerce("A B C"));
    assertNull(type.coerce(null));
  }

  @Test
  void slugSupportsUnique() {
    var type = new SlugFieldType();
    assertTrue(type.supportsUnique());
  }

  // ---- UUID ----

  @Test
  void uuidAcceptsValidUuids() {
    var type = new UuidFieldType();
    assertDoesNotThrow(() -> type.validate("id", "550e8400-e29b-41d4-a716-446655440000", Map.of()));
    assertThrows(IllegalArgumentException.class, () -> type.validate("id", "not-a-uuid", Map.of()));
    assertThrows(IllegalArgumentException.class, () -> type.validate("id", 42, Map.of()));
  }

  @Test
  void uuidCoercesAndAutoGenerates() {
    var type = new UuidFieldType();
    assertNull(type.coerce(null));
    String result = (String) type.coerce("__auto__");
    assertNotNull(result);
    assertDoesNotThrow(() -> UUID.fromString(result));
  }

  @Test
  void uuidSupportsUnique() {
    var type = new UuidFieldType();
    assertTrue(type.supportsUnique());
  }

  @Test
  void uuidHasPluginOptions() {
    var type = new UuidFieldType();
    assertTrue((Boolean) type.getPluginOptions().get("readOnly"));
    assertTrue((Boolean) type.getPluginOptions().get("autoGenerate"));
  }
}
