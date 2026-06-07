package com.quarkus.cms.core.schema.model;

/**
 * Enumeration of all supported field types in the dynamic content schema system. Mirrors Strapi
 * v5's field types for compatibility.
 */
public enum FieldType {
  STRING,
  TEXT,
  INTEGER,
  FLOAT,
  DECIMAL,
  BOOLEAN,
  DATE,
  DATETIME,
  TIME,
  EMAIL,
  PASSWORD,
  UID,
  ENUMERATION,
  JSON,
  RICHTEXT,
  MEDIA,
  RELATION,
  COMPONENT,
  DYNAMIC_ZONE;

  /** Returns true if this field type stores a scalar/primitive value. */
  public boolean isScalar() {
    return switch (this) {
      case STRING,
          TEXT,
          INTEGER,
          FLOAT,
          DECIMAL,
          BOOLEAN,
          DATE,
          DATETIME,
          TIME,
          EMAIL,
          PASSWORD,
          UID,
          JSON,
          RICHTEXT ->
          true;
      default -> false;
    };
  }

  /** Returns true if this field type represents a relational link. */
  public boolean isRelation() {
    return this == RELATION;
  }

  /** Returns true if this field type represents a component embedding. */
  public boolean isComponent() {
    return this == COMPONENT || this == DYNAMIC_ZONE;
  }
}
