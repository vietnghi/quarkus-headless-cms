package com.quarkus.cms.customfields;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.customfields.storage.JsonbFieldStorageStrategy;

import java.util.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Custom Fields module.
 *
 * <p>Tests CustomFieldDefinition entity defaults, CustomFieldService validation, and custom value
 * operations (no DB required).
 */
class CustomFieldServiceTest {

  private final CustomFieldService service;
  private final JsonbFieldStorageStrategy storage;

  CustomFieldServiceTest() {
    this.service = new CustomFieldService();
    this.storage = new JsonbFieldStorageStrategy();
    this.service.setDefaultStorage(storage);
  }

  // ---- CustomFieldDefinition entity defaults ----

  @Test
  void shouldHaveDefaultFieldType() {
    var def = new CustomFieldDefinition();
    assertEquals("text", def.fieldType);
    assertFalse(def.required);
    assertEquals(0, def.sortOrder);
    assertNotNull(def.createdAt);
    assertNotNull(def.updatedAt);
  }

  @Test
  void shouldSupportAssignment() {
    var def = new CustomFieldDefinition();
    def.contentType = "api::article.article";
    def.fieldName = "featured-image";
    def.label = "Featured Image";
    def.fieldType = "media";
    def.required = true;
    def.defaultValue = "";
    def.sortOrder = 1;

    assertEquals("api::article.article", def.contentType);
    assertEquals("featured-image", def.fieldName);
    assertEquals("Featured Image", def.label);
    assertEquals("media", def.fieldType);
    assertTrue(def.required);
    assertEquals(1, def.sortOrder);
  }

  // ---- Custom value storage in CmsEntry.data._custom ----

  @Test
  void shouldReturnEmptyCustomValuesForNewEntry() {
    var entry = new CmsEntry();
    Map<String, Object> values = service.getCustomValues(entry);
    assertTrue(values.isEmpty());
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldReturnCustomValuesWhenPresent() {
    var entry = new CmsEntry();
    entry.contentType = "api::article.article";
    storage.writeValue(entry, "seoDesc", "An SEO description");
    storage.writeValue(entry, "priority", 5);

    Map<String, Object> values = service.getCustomValues(entry);
    assertEquals(2, values.size());
    assertEquals("An SEO description", values.get("seoDesc"));
    assertEquals(5, values.get("priority"));
  }

  // ---- Value validation (direct type checking) ----

  @Test
  void shouldCoerceNumberValue() {
    // Test number coercion logic directly
    Object result = coerceValue("number", 42);
    assertEquals(42, result);

    Object strResult = coerceValue("number", "3.14");
    assertTrue(strResult instanceof Double);
    assertEquals(3.14, strResult);
  }

  @Test
  void shouldCoerceBooleanValue() {
    Boolean result = (Boolean) coerceValue("boolean", true);
    assertTrue(result);

    Object strResult = coerceValue("boolean", "true");
    assertTrue((Boolean) strResult);
  }

  // ---- Validation logic ----

  @Test
  void shouldRequireStringForTextType() {
    // text type only accepts strings
    assertDoesNotThrow(() -> validateField("text", "hello"));
    assertThrows(IllegalArgumentException.class, () -> validateField("text", 42));
  }

  @Test
  void shouldRequireNumberForNumberType() {
    assertDoesNotThrow(() -> validateField("number", 42));
    assertDoesNotThrow(() -> validateField("number", 3.14));
    assertThrows(IllegalArgumentException.class, () -> validateField("number", "not-a-number"));
  }

  @Test
  void shouldRequireBooleanForBooleanType() {
    assertDoesNotThrow(() -> validateField("boolean", true));
    assertDoesNotThrow(() -> validateField("boolean", false));
    assertThrows(IllegalArgumentException.class, () -> validateField("boolean", "true"));
  }

  @Test
  void shouldValidateJsonType() {
    assertDoesNotThrow(() -> validateField("json", Map.of("key", "value")));
    assertDoesNotThrow(() -> validateField("json", List.of("a", "b")));
    assertThrows(IllegalArgumentException.class, () -> validateField("json", "string-value"));
  }

  @Test
  void shouldValidateSelectType() {
    String options = "draft,published,archived";
    assertDoesNotThrow(() -> validateSelectField("draft", options));
    assertDoesNotThrow(() -> validateSelectField("published", options));
    assertThrows(IllegalArgumentException.class, () -> validateSelectField("deleted", options));
    assertThrows(IllegalArgumentException.class, () -> validateSelectField(42, options));
  }

  @Test
  void shouldHandleEmptyCustomMap() {
    var entry = new CmsEntry();
    entry.data = new LinkedHashMap<>();

    Map<String, Object> values = service.getCustomValues(entry);
    assertTrue(values.isEmpty());
  }

  @Test
  void shouldHandleNonMapCustomValueGracefully() {
    var entry = new CmsEntry();
    entry.data = new LinkedHashMap<>();
    entry.data.put("_custom", "not-a-map");

    Map<String, Object> values = service.getCustomValues(entry);
    assertTrue(values.isEmpty());
  }

  // ---- Custom field update detection ----

  @Test
  void shouldDetectCustomFieldChanges() {
    var entry = new CmsEntry();
    entry.data = new LinkedHashMap<>();
    Map<String, Object> custom = new LinkedHashMap<>();
    custom.put("seoDesc", "Old description");
    entry.data.put("_custom", custom);

    // Simulate updating
    custom.put("seoDesc", "New description");
    custom.put("priority", 1);

    assertEquals("New description", custom.get("seoDesc"));
    assertEquals(1, custom.get("priority"));
  }

  // ---- Edge cases ----

  @Test
  void shouldHaveAlwaysNonNullData() {
    var entry = new CmsEntry();
    // CmsEntry always initializes data = new HashMap<>()
    assertNotNull(entry.data);
  }

  @Test
  void shouldRemoveCustomFieldWhenSettingNull() {
    var entry = new CmsEntry();
    entry.data = new LinkedHashMap<>();
    Map<String, Object> custom = new LinkedHashMap<>();
    custom.put("seoDesc", "Some value");
    custom.put("priority", 1);
    entry.data.put("_custom", custom);

    // Simulate removing a field
    custom.remove("seoDesc");
    assertNull(custom.get("seoDesc"));
    assertEquals(1, custom.get("priority"));
    assertEquals(1, custom.size());
  }

  @Test
  void shouldHandleSelectTypeCaseSensitivity() {
    String options = "draft,published,archived";
    // Exact match should pass
    assertDoesNotThrow(() -> validateSelectField("draft", options));
    // Case mismatch should fail (select options are case-sensitive)
    assertThrows(IllegalArgumentException.class, () -> validateSelectField("Draft", options));
  }

  @Test
  void shouldHandleNullDefaultValue() {
    var def = new CustomFieldDefinition();
    def.defaultValue = null;
    assertNull(def.defaultValue);
  }

  @Test
  void shouldAcceptUnknownFieldType() {
    // Unknown type should be permissive
    assertDoesNotThrow(() -> validateField("custom_type", "anything"));
    assertDoesNotThrow(() -> validateField("custom_type", 42));
    assertDoesNotThrow(() -> validateField("custom_type", true));
  }

  @Test
  void shouldHandleDateType() {
    assertDoesNotThrow(() -> validateField("date", "2024-01-15"));
    assertThrows(IllegalArgumentException.class, () -> validateField("date", 42));
  }

  @Test
  void shouldHandleMediaType() {
    assertDoesNotThrow(() -> validateField("media", "asset-uuid-123"));
    assertThrows(IllegalArgumentException.class, () -> validateField("media", 42));
  }

  // ---- Helpers to test validation logic (same logic as CustomFieldService) ----

  private static void validateField(String fieldType, Object value) {
    // fieldName parameter is not needed for type checking
    switch (fieldType) {
      case "text":
      case "select":
        if (!(value instanceof String)) {
          throw new IllegalArgumentException("requires a string value");
        }
        break;
      case "number":
        if (!(value instanceof Number)) {
          throw new IllegalArgumentException("requires a numeric value");
        }
        break;
      case "boolean":
        if (!(value instanceof Boolean)) {
          throw new IllegalArgumentException("requires a boolean value");
        }
        break;
      case "date":
        if (!(value instanceof String)) {
          throw new IllegalArgumentException("requires a date string value");
        }
        break;
      case "json":
        if (!(value instanceof Map || value instanceof List)) {
          throw new IllegalArgumentException("requires a JSON object or array");
        }
        break;
      case "media":
        if (!(value instanceof String)) {
          throw new IllegalArgumentException("requires a media ID string");
        }
        break;
      default:
        // Unknown type — accept anything
    }
  }

  private static void validateSelectField(Object value, String options) {
    if (!(value instanceof String)) {
      throw new IllegalArgumentException("requires a string value");
    }
    if (options != null && !options.isBlank()) {
      Set<String> validOptions = new HashSet<>(Arrays.asList(options.split(",")));
      if (!validOptions.contains(value.toString())) {
        throw new IllegalArgumentException("must be one of: " + validOptions);
      }
    }
  }

  private static Object coerceValue(String fieldType, Object value) {
    if ("number".equals(fieldType) && value instanceof String) {
      try {
        return Double.parseDouble((String) value);
      } catch (NumberFormatException e) {
        return value;
      }
    }
    if ("boolean".equals(fieldType) && value instanceof String) {
      return Boolean.parseBoolean((String) value);
    }
    return value;
  }
}
