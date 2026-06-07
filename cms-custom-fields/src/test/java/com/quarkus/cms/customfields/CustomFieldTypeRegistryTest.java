package com.quarkus.cms.customfields;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.customfields.spi.CustomFieldType;
import com.quarkus.cms.customfields.spi.CustomFieldTypeRegistry;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the CustomFieldType SPI and Registry. */
class CustomFieldTypeRegistryTest {

  private CustomFieldTypeRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new CustomFieldTypeRegistry();
  }

  @Test
  void shouldRegisterAndRetrieveTypes() {
    var type = new TestFieldType("text", "Text", "string");
    registry.register(type);
    assertSame(type, registry.getType("text"));
    assertEquals(1, registry.size());
  }

  @Test
  void shouldRejectDuplicateRegistration() {
    registry.register(new TestFieldType("my-type", "My Type", "custom"));
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.register(new TestFieldType("my-type", "Duplicate", "custom")));
  }

  @Test
  void shouldReplaceOnRegisterOrReplace() {
    registry.register(new TestFieldType("slug", "Slug V1", "custom"));
    registry.registerOrReplace(new TestFieldType("slug", "Slug V2", "custom"));
    assertEquals("Slug V2", registry.getType("slug").getDisplayName());
  }

  @Test
  void shouldUnregisterTypes() {
    registry.register(new TestFieldType("color", "Color", "custom"));
    assertTrue(registry.hasType("color"));
    registry.unregister("color");
    assertFalse(registry.hasType("color"));
  }

  @Test
  void shouldReturnNullForUnknownType() {
    assertNull(registry.getType("nonexistent"));
  }

  @Test
  void shouldReturnAllTypeIds() {
    registry.register(new TestFieldType("a", "A", "cat1"));
    registry.register(new TestFieldType("b", "B", "cat2"));
    registry.register(new TestFieldType("c", "C", "cat1"));
    assertEquals(Set.of("a", "b", "c"), registry.getTypeIds());
  }

  @Test
  void shouldFilterByCategory() {
    registry.register(new TestFieldType("a", "A", "string"));
    registry.register(new TestFieldType("b", "B", "number"));
    registry.register(new TestFieldType("c", "C", "string"));
    assertEquals(2, registry.getTypesByCategory("string").size());
    assertEquals(1, registry.getTypesByCategory("number").size());
  }

  @Test
  void shouldStartEmpty() {
    assertEquals(0, registry.size());
    assertTrue(registry.getTypeIds().isEmpty());
    assertTrue(registry.getAllTypes().isEmpty());
  }

  @Test
  void shouldProvideImmutableCollections() {
    registry.register(new TestFieldType("x", "X", "cat"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> registry.getAllTypes().add(new TestFieldType("y", "Y", "cat")));
    assertThrows(UnsupportedOperationException.class, () -> registry.getTypeIds().add("y"));
  }

  // ---- Test helper ----

  private static class TestFieldType implements CustomFieldType {
    private final String typeId;
    private final String displayName;
    private final String category;

    TestFieldType(String typeId, String displayName, String category) {
      this.typeId = typeId;
      this.displayName = displayName;
      this.category = category;
    }

    @Override
    public String getTypeId() {
      return typeId;
    }

    @Override
    public String getDisplayName() {
      return displayName;
    }

    @Override
    public String getCategory() {
      return category;
    }

    @Override
    public String getDescription() {
      return "Test field type";
    }

    @Override
    public Class<?> getValueType() {
      return String.class;
    }

    @Override
    public Object getDefaultValue() {
      return null;
    }

    @Override
    public void validate(String fieldName, Object value, Map<String, Object> config) {}

    @Override
    public Object coerce(Object value) {
      return value;
    }
  }
}
