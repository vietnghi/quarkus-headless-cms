package com.quarkus.cms.core.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonMapConverter}.
 *
 * <p>Covers serialization (Map → JSON string) and deserialization (JSON string → Map)
 * including null/empty inputs, nested maps, and error handling.
 */
class JsonMapConverterTest {

  private final JsonMapConverter converter = new JsonMapConverter();

  // ---- convertToDatabaseColumn (Map → JSON string) ----

  @Test
  void shouldConvertNullToEmptyJson() {
    assertEquals("{}", converter.convertToDatabaseColumn(null));
  }

  @Test
  void shouldConvertEmptyMapToEmptyJson() {
    assertEquals("{}", converter.convertToDatabaseColumn(Map.of()));
  }

  @Test
  void shouldConvertSimpleMap() {
    Map<String, Object> input = new HashMap<>();
    input.put("title", "Hello");
    input.put("count", 42);
    String json = converter.convertToDatabaseColumn(input);
    assertTrue(json.contains("\"title\":\"Hello\"") || json.contains("\"title\": \"Hello\""));
    assertTrue(json.contains("\"count\":42") || json.contains("\"count\": 42"));
  }

  @Test
  void shouldConvertNestedMap() {
    Map<String, Object> nested = Map.of("inner", "value");
    Map<String, Object> input = Map.of("nested", nested);
    String json = converter.convertToDatabaseColumn(input);
    assertTrue(json.contains("\"nested\""));
    assertTrue(json.contains("\"inner\":\"value\"") || json.contains("\"inner\": \"value\""));
  }

  @Test
  void shouldConvertMixedTypes() {
    Map<String, Object> input = new HashMap<>();
    input.put("string", "text");
    input.put("integer", 123);
    input.put("bool", true);
    input.put("null_val", null);
    String json = converter.convertToDatabaseColumn(input);
    assertNotNull(json);
    assertTrue(json.contains("\"string\":\"text\"") || json.contains("\"string\": \"text\""));
    assertTrue(json.contains("\"integer\":123") || json.contains("\"integer\": 123"));
    assertTrue(json.contains("\"bool\":true") || json.contains("\"bool\": true"));
  }

  // ---- convertToEntityAttribute (JSON string → Map) ----

  @Test
  void shouldConvertNullToEmptyMap() {
    Map<String, Object> result = converter.convertToEntityAttribute(null);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void shouldConvertBlankToEmptyMap() {
    Map<String, Object> result = converter.convertToEntityAttribute("  ");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void shouldConvertEmptyJsonToEmptyMap() {
    Map<String, Object> result = converter.convertToEntityAttribute("{}");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void shouldDeserializeSimpleJson() {
    String json = "{\"title\":\"Hello\",\"count\":42}";
    Map<String, Object> result = converter.convertToEntityAttribute(json);
    assertEquals("Hello", result.get("title"));
    assertEquals(42, result.get("count"));
  }

  @Test
  void shouldDeserializeNestedJson() {
    String json = "{\"nested\":{\"inner\":\"value\"}}";
    Map<String, Object> result = converter.convertToEntityAttribute(json);
    assertNotNull(result.get("nested"));
    assertInstanceOf(Map.class, result.get("nested"));
    @SuppressWarnings("unchecked")
    Map<String, Object> inner = (Map<String, Object>) result.get("nested");
    assertEquals("value", inner.get("inner"));
  }

  @Test
  void shouldDeserializeMixedTypes() {
    String json = "{\"string\":\"text\",\"integer\":123,\"bool\":true,\"null_val\":null}";
    Map<String, Object> result = converter.convertToEntityAttribute(json);
    assertEquals("text", result.get("string"));
    assertEquals(123, result.get("integer"));
    assertEquals(true, result.get("bool"));
  }

  @Test
  void shouldThrowOnMalformedJson() {
    assertThrows(RuntimeException.class,
        () -> converter.convertToEntityAttribute("{broken json"));
  }

  // ---- Round-trip ----

  @Test
  void shouldRoundTrip() {
    Map<String, Object> original = new HashMap<>();
    original.put("name", "test");
    original.put("version", 1);
    original.put("active", true);
    original.put("data", Map.of("key", "val"));

    String json = converter.convertToDatabaseColumn(original);
    Map<String, Object> restored = converter.convertToEntityAttribute(json);

    assertEquals("test", restored.get("name"));
    assertEquals(1, restored.get("version"));
    assertEquals(true, restored.get("active"));
    assertNotNull(restored.get("data"));
  }

  @Test
  void shouldHandleSpecialCharacters() {
    Map<String, Object> input = Map.of("text", "line1\nline2\ttab\"quote\\backslash");
    String json = converter.convertToDatabaseColumn(input);
    Map<String, Object> result = converter.convertToEntityAttribute(json);
    assertEquals("line1\nline2\ttab\"quote\\backslash", result.get("text"));
  }
}
