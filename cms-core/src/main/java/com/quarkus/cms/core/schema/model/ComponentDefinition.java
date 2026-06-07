package com.quarkus.cms.core.schema.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Defines a reusable component — a group of fields that can be embedded inside content types or
 * other components via {@link FieldType#COMPONENT} fields or inside a {@link
 * DynamicZoneDefinition}.
 */
@JsonDeserialize(builder = ComponentDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentDefinition {

  private final String uid; // e.g. "default.seo" or "shared.meta"
  private final String category; // grouping category, e.g. "shared"
  private final String displayName; // human-readable label
  private final String description;
  private final List<FieldDefinition> fields;
  private final Map<String, Object> options;

  private ComponentDefinition(Builder builder) {
    if (builder.uid == null || builder.uid.isBlank())
      throw new IllegalArgumentException("component uid is required");
    this.uid = builder.uid;
    this.category = builder.category;
    this.displayName = builder.displayName;
    this.description = builder.description;
    this.fields = builder.fields == null ? List.of() : List.copyOf(builder.fields);
    this.options = builder.options == null ? Map.of() : Map.copyOf(builder.options);
  }

  public String getUid() {
    return uid;
  }

  public String getCategory() {
    return category;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public List<FieldDefinition> getFields() {
    return fields;
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  public FieldDefinition getField(String name) {
    return fields.stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);
  }

  public static Builder builder(String uid) {
    return new Builder(uid);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ComponentDefinition that)) return false;
    return uid.equals(that.uid);
  }

  @Override
  public int hashCode() {
    return uid.hashCode();
  }

  @Override
  public String toString() {
    return "ComponentDefinition{uid='" + uid + "', fields=" + fields.size() + "}";
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private String uid;
    private String category;
    private String displayName;
    private String description;
    private List<FieldDefinition> fields;
    private Map<String, Object> options;

    public Builder() {}

    Builder(String uid) {
      this.uid = uid;
    }

    public Builder uid(String uid) {
      this.uid = uid;
      return this;
    }

    public Builder category(String category) {
      this.category = category;
      return this;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder fields(List<FieldDefinition> fields) {
      this.fields = fields;
      return this;
    }

    public Builder options(Map<String, Object> options) {
      this.options = options;
      return this;
    }

    public ComponentDefinition build() {
      return new ComponentDefinition(this);
    }
  }
}
