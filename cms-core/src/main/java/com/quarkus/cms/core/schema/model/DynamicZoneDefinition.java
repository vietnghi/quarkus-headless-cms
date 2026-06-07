package com.quarkus.cms.core.schema.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Defines a dynamic zone — an area inside a content type where the user can add components
 * dynamically at runtime, choosing from a set of allowed component UIDs and arranging them in any
 * order.
 */
@JsonDeserialize(builder = DynamicZoneDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DynamicZoneDefinition {

  private final String name;
  private final List<String> components; // allowed component UIDs
  private final int min; // minimum number of components (0 = optional)
  private final int max; // maximum number of components (-1 = unlimited)
  private final boolean required;

  private DynamicZoneDefinition(Builder builder) {
    if (builder.name == null || builder.name.isBlank())
      throw new IllegalArgumentException("dynamic zone name is required");
    this.name = builder.name;
    this.components = builder.components == null ? List.of() : List.copyOf(builder.components);
    this.min = builder.min;
    this.max = builder.max;
    this.required = builder.required;
  }

  public String getName() {
    return name;
  }

  public List<String> getComponents() {
    return components;
  }

  public int getMin() {
    return min;
  }

  public int getMax() {
    return max;
  }

  public boolean isRequired() {
    return required;
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DynamicZoneDefinition that)) return false;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private String name;
    private List<String> components;
    private int min;
    private int max = -1; // unlimited
    private boolean required;

    public Builder() {}

    Builder(String name) {
      this.name = name;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder components(List<String> components) {
      this.components = components;
      return this;
    }

    public Builder min(int min) {
      this.min = min;
      return this;
    }

    public Builder max(int max) {
      this.max = max;
      return this;
    }

    public Builder required(boolean required) {
      this.required = required;
      return this;
    }

    public DynamicZoneDefinition build() {
      return new DynamicZoneDefinition(this);
    }
  }
}
