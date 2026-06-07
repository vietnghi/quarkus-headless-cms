package com.quarkus.cms.core.schema.validation;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.storage.SchemaCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates content-type and component schema definitions before they are persisted.
 *
 * <p>Checks structural integrity (duplicate field names, missing required properties), constraint
 * validity (e.g., minLength <= maxLength), and referential integrity (components and relations must
 * target existing schemas).
 */
public class SchemaValidator {

  private final SchemaCache schemaCache;

  public SchemaValidator(SchemaCache schemaCache) {
    this.schemaCache = schemaCache;
  }

  /**
   * Validates a content-type definition. Returns a list of validation errors; an empty list means
   * the definition is valid.
   */
  public List<String> validateContentType(ContentTypeDefinition ct) {
    List<String> errors = new ArrayList<>();

    if (ct.getUid() == null || ct.getUid().isBlank()) {
      errors.add("Content type UID is required");
    } else if (!ct.getUid().matches("[a-zA-Z0-9:._-]+")) {
      errors.add("Content type UID contains invalid characters: " + ct.getUid());
    }

    // validate fields
    Set<String> fieldNames = new HashSet<>();
    for (FieldDefinition field : ct.getFields()) {
      if (!fieldNames.add(field.getName())) {
        errors.add("Duplicate field name '" + field.getName() + "' in " + ct.getUid());
      }
      errors.addAll(validateField(field, ct.getUid()));
    }

    // validate relations
    Set<String> relationNames = new HashSet<>();
    for (RelationDefinition rel : ct.getRelations()) {
      if (!relationNames.add(rel.getFieldName())) {
        errors.add("Duplicate relation name '" + rel.getFieldName() + "' in " + ct.getUid());
      }
      errors.addAll(validateRelation(rel, ct.getUid()));
    }

    // validate dynamic zones reference existing components
    for (DynamicZoneDefinition dz : ct.getDynamicZones()) {
      if (fieldNames.contains(dz.getName())) {
        errors.add(
            "Dynamic zone name '"
                + dz.getName()
                + "' conflicts with a field name in "
                + ct.getUid());
      }
      for (String compUid : dz.getComponents()) {
        if (schemaCache != null && !schemaCache.hasComponent(compUid)) {
          errors.add(
              "Dynamic zone '" + dz.getName() + "' references unknown component '" + compUid + "'");
        }
      }
    }

    return errors;
  }

  /** Validates a component definition. */
  public List<String> validateComponent(ComponentDefinition comp) {
    List<String> errors = new ArrayList<>();

    if (comp.getUid() == null || comp.getUid().isBlank()) {
      errors.add("Component UID is required");
    } else if (!comp.getUid().matches("[a-zA-Z0-9._-]+")) {
      errors.add("Component UID contains invalid characters: " + comp.getUid());
    }

    Set<String> fieldNames = new HashSet<>();
    for (FieldDefinition field : comp.getFields()) {
      if (!fieldNames.add(field.getName())) {
        errors.add("Duplicate field name '" + field.getName() + "' in component " + comp.getUid());
      }
      errors.addAll(validateField(field, comp.getUid()));
    }

    return errors;
  }

  private List<String> validateField(FieldDefinition field, String ownerUid) {
    List<String> errors = new ArrayList<>();
    String prefix = "Field '" + field.getName() + "' in " + ownerUid + ": ";

    if (field.getType() == null) {
      errors.add(prefix + "type is required");
      return errors;
    }

    // string/text length constraints
    if (field.getType() == FieldType.STRING || field.getType() == FieldType.TEXT) {
      if (field.getMinLength() != null
          && field.getMaxLength() != null
          && field.getMinLength() > field.getMaxLength()) {
        errors.add(
            prefix
                + "minLength ("
                + field.getMinLength()
                + ") exceeds maxLength ("
                + field.getMaxLength()
                + ")");
      }
    }

    // numeric range
    if (field.getType() == FieldType.INTEGER
        || field.getType() == FieldType.FLOAT
        || field.getType() == FieldType.DECIMAL) {
      if (field.getMin() != null && field.getMax() != null && field.getMin() > field.getMax()) {
        errors.add(prefix + "min (" + field.getMin() + ") exceeds max (" + field.getMax() + ")");
      }
    }

    // enumeration must have values
    if (field.getType() == FieldType.ENUMERATION
        && (field.getEnumValues() == null || field.getEnumValues().isEmpty())) {
      errors.add(prefix + "enumeration field must have at least one enum value");
    }

    // component field must reference a component
    if (field.getType() == FieldType.COMPONENT) {
      if (field.getComponent() == null || field.getComponent().isBlank()) {
        errors.add(prefix + "component field must specify a component UID");
      } else if (schemaCache != null && !schemaCache.hasComponent(field.getComponent())) {
        errors.add(prefix + "references unknown component '" + field.getComponent() + "'");
      }
    }

    // relation field must have a target
    if (field.getType() == FieldType.RELATION
        && (field.getTarget() == null || field.getTarget().isBlank())) {
      errors.add(prefix + "relation field must specify a target content type");
    }

    return errors;
  }

  private List<String> validateRelation(RelationDefinition rel, String ownerUid) {
    List<String> errors = new ArrayList<>();
    String prefix = "Relation '" + rel.getFieldName() + "' in " + ownerUid + ": ";

    if (rel.getType() == null) {
      errors.add(prefix + "type is required");
    }
    if (rel.getTarget() == null || rel.getTarget().isBlank()) {
      errors.add(prefix + "target content type is required");
    }

    // For non-morph relations, target should exist (if cache is available)
    if (!rel.isMorph() && schemaCache != null && !"*".equals(rel.getTarget())) {
      if (!schemaCache.hasContentType(rel.getTarget())) {
        errors.add(prefix + "targets unknown content type '" + rel.getTarget() + "'");
      }
    }

    return errors;
  }
}
