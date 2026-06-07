package com.quarkus.cms.core.schema.model;

/** Describes the cardinality of a relation between content types. */
public enum RelationType {
  ONE_TO_ONE,
  ONE_TO_MANY,
  MANY_TO_ONE,
  MANY_TO_MANY,
  MORPH_TO_ONE,
  MORPH_TO_MANY
}
