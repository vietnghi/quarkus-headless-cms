package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldType;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;

/**
 * Built-in relation field types: oneToOne, oneToMany, manyToOne, manyToMany.
 *
 * <p>Relations link entries of one content type to another. Values are stored as content type UID +
 * document ID pairs.
 */
@ApplicationScoped
public class RelationFieldType implements CustomFieldType {

  private static final Set<String> SUB_TYPES =
      Set.of("oneToOne", "oneToMany", "manyToOne", "manyToMany");

  @Override
  public String getTypeId() {
    return "relation";
  }

  @Override
  public String getDisplayName() {
    return "Relation";
  }

  @Override
  public String getCategory() {
    return "relation";
  }

  @Override
  public String getDescription() {
    return "Relation to another content type: oneToOne, oneToMany, manyToOne, manyToMany";
  }

  @Override
  public Class<?> getValueType() {
    return Object.class;
  }

  @Override
  public Object getDefaultValue() {
    return null;
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void validate(String fieldName, Object value, Map<String, Object> config) {
    if (value == null) return;

    String subType = (String) config.getOrDefault("subType", "oneToOne");
    String targetCT = (String) config.get("targetContentType");

    if (targetCT == null || targetCT.isBlank()) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' requires a 'targetContentType' configuration");
    }

    switch (subType) {
      case "oneToOne":
      case "manyToOne":
        if (!(value instanceof Map)) {
          throw new IllegalArgumentException(
              "Field '" + fieldName + "' requires a relation object with 'documentId'");
        }
        validateRelationMap(fieldName, (Map) value);
        break;
      case "oneToMany":
      case "manyToMany":
        if (!(value instanceof java.util.List)) {
          throw new IllegalArgumentException(
              "Field '" + fieldName + "' requires a list of relation objects");
        }
        for (Object item : (java.util.List) value) {
          if (!(item instanceof Map)) {
            throw new IllegalArgumentException(
                "Field '"
                    + fieldName
                    + "' requires each relation to be an object with 'documentId'");
          }
          validateRelationMap(fieldName, (Map) item);
        }
        break;
    }
  }

  private void validateRelationMap(String fieldName, Map<String, Object> rel) {
    if (!rel.containsKey("documentId") || rel.get("documentId") == null) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' relation requires 'documentId'");
    }
  }

  @Override
  public Object coerce(Object value) {
    return value; // pass through - relations are stored as maps
  }

  @Override
  public Map<String, String> getConfigSchema() {
    return Map.of(
        "subType", "string (oneToOne, oneToMany, manyToOne, manyToMany)",
        "targetContentType", "string - the target content type UID",
        "inversedBy", "string - inverse relation field name on the target");
  }

  /** Returns true if the given sub-type is handled by this field type. */
  public static boolean handlesSubType(String subType) {
    return SUB_TYPES.contains(subType);
  }
}
