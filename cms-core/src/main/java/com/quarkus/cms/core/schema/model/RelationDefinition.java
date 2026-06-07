package com.quarkus.cms.core.schema.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Describes a relation field between two content types or components.
 *
 * <p>Relations are stored in the generic {@code cms_relations} adjacency table, avoiding the need
 * for physical foreign-key columns on dynamic schemas.
 */
@JsonDeserialize(builder = RelationDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationDefinition {

  private final String fieldName;
  private final RelationType type;
  private final String target; // UID of the target content type, or '*' for morph
  private final String targetAttribute; // the inverse field name on the target, if bidirectional
  private final String joinTable; // override for join table name (many-to-many)
  private final boolean dominant; // which side owns the FK in one-to-one
  private final String morphColumnType; // discriminator column for morph relations
  private final Map<String, Object> options;

  private RelationDefinition(Builder builder) {
    if (builder.fieldName == null || builder.fieldName.isBlank())
      throw new IllegalArgumentException("fieldName is required");
    if (builder.type == null) throw new IllegalArgumentException("relation type is required");
    if (builder.target == null || builder.target.isBlank())
      throw new IllegalArgumentException("target is required");
    this.fieldName = builder.fieldName;
    this.type = builder.type;
    this.target = builder.target;
    this.targetAttribute = builder.targetAttribute;
    this.joinTable = builder.joinTable;
    this.dominant = builder.dominant;
    this.morphColumnType = builder.morphColumnType;
    this.options = builder.options == null ? Map.of() : Map.copyOf(builder.options);
  }

  public String getFieldName() {
    return fieldName;
  }

  public RelationType getType() {
    return type;
  }

  public String getTarget() {
    return target;
  }

  public String getTargetAttribute() {
    return targetAttribute;
  }

  public String getJoinTable() {
    return joinTable;
  }

  public boolean isDominant() {
    return dominant;
  }

  public String getMorphColumnType() {
    return morphColumnType;
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  @JsonIgnore
  public boolean isBidirectional() {
    return targetAttribute != null && !targetAttribute.isEmpty();
  }

  @JsonIgnore
  public boolean isMorph() {
    return type == RelationType.MORPH_TO_ONE || type == RelationType.MORPH_TO_MANY;
  }

  public static Builder builder(String fieldName, RelationType type, String target) {
    return new Builder(fieldName, type, target);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RelationDefinition that)) return false;
    return fieldName.equals(that.fieldName);
  }

  @Override
  public int hashCode() {
    return fieldName.hashCode();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private String fieldName;
    private RelationType type;
    private String target;
    private String targetAttribute;
    private String joinTable;
    private boolean dominant;
    private String morphColumnType;
    private Map<String, Object> options;

    public Builder() {}

    Builder(String fieldName, RelationType type, String target) {
      this.fieldName = fieldName;
      this.type = type;
      this.target = target;
    }

    public Builder fieldName(String n) {
      this.fieldName = n;
      return this;
    }

    public Builder type(RelationType t) {
      this.type = t;
      return this;
    }

    public Builder target(String t) {
      this.target = t;
      return this;
    }

    public Builder targetAttribute(String targetAttribute) {
      this.targetAttribute = targetAttribute;
      return this;
    }

    public Builder joinTable(String joinTable) {
      this.joinTable = joinTable;
      return this;
    }

    public Builder dominant(boolean dominant) {
      this.dominant = dominant;
      return this;
    }

    public Builder morphColumnType(String morphColumnType) {
      this.morphColumnType = morphColumnType;
      return this;
    }

    public Builder options(Map<String, Object> options) {
      this.options = options;
      return this;
    }

    public RelationDefinition build() {
      return new RelationDefinition(this);
    }
  }
}
