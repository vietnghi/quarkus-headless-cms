package com.quarkus.cms.core.schema.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Defines a single field within a content type or component.
 *
 * <p>Fields carry type information, validation constraints, and metadata. Relation and component
 * fields reference other schema definitions.
 */
@JsonDeserialize(builder = FieldDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldDefinition {

  private final String name;
  private final FieldType type;
  private final boolean required;
  private final boolean unique;
  private final String defaultValue;
  private final Integer minLength;
  private final Integer maxLength;
  private final Integer min;
  private final Integer max;
  private final String regex;
  private final List<String> enumValues;

  @JsonProperty("private")
  private final boolean privateField;

  /** Whether this field supports per-locale translations. */
  private final boolean localized;

  private final String target;
  private final String component;
  private final List<String> allowedComponents;
  private final int minComponents;
  private final int maxComponents;
  private final boolean repeatable;
  private final Map<String, Object> pluginOptions;

  private FieldDefinition(Builder builder) {
    if (builder.name == null || builder.name.isBlank())
      throw new IllegalArgumentException("field name is required");
    if (builder.type == null) throw new IllegalArgumentException("field type is required");
    this.name = builder.name;
    this.type = builder.type;
    this.required = builder.required;
    this.unique = builder.unique;
    this.defaultValue = builder.defaultValue;
    this.minLength = builder.minLength;
    this.maxLength = builder.maxLength;
    this.min = builder.min;
    this.max = builder.max;
    this.regex = builder.regex;
    this.enumValues = builder.enumValues == null ? List.of() : List.copyOf(builder.enumValues);
    this.privateField = builder.privateField;
    this.localized = builder.localized;
    this.target = builder.target;
    this.component = builder.component;
    this.allowedComponents =
        builder.allowedComponents == null ? List.of() : List.copyOf(builder.allowedComponents);
    this.minComponents = builder.minComponents;
    this.maxComponents = builder.maxComponents;
    this.repeatable = builder.repeatable;
    this.pluginOptions =
        builder.pluginOptions == null ? Map.of() : Map.copyOf(builder.pluginOptions);
  }

  // ---- accessors ----

  public String getName() {
    return name;
  }

  public FieldType getType() {
    return type;
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isUnique() {
    return unique;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public Integer getMinLength() {
    return minLength;
  }

  public Integer getMaxLength() {
    return maxLength;
  }

  public Integer getMin() {
    return min;
  }

  public Integer getMax() {
    return max;
  }

  public String getRegex() {
    return regex;
  }

  public List<String> getEnumValues() {
    return enumValues;
  }

  @JsonProperty("private")
  public boolean isPrivate() {
    return privateField;
  }

  /** Returns {@code true} if this field supports per-locale translations. */
  @JsonProperty("localized")
  public boolean isLocalized() {
    return localized;
  }

  public String getTarget() {
    return target;
  }

  public String getComponent() {
    return component;
  }

  public List<String> getAllowedComponents() {
    return allowedComponents;
  }

  public int getMinComponents() {
    return minComponents;
  }

  public int getMaxComponents() {
    return maxComponents;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public Map<String, Object> getPluginOptions() {
    return pluginOptions;
  }

  public static Builder builder(String name, FieldType type) {
    return new Builder(name, type);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FieldDefinition that)) return false;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "FieldDefinition{name='" + name + "', type=" + type + "}";
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private String name;
    private FieldType type;
    private boolean required;
    private boolean unique;
    private String defaultValue;
    private Integer minLength;
    private Integer maxLength;
    private Integer min;
    private Integer max;
    private String regex;
    private List<String> enumValues;

    @JsonProperty("private")
    private boolean privateField;

    @JsonProperty("localized")
    private boolean localized;

    private String target;
    private String component;
    private List<String> allowedComponents;
    private int minComponents;
    private int maxComponents;
    private boolean repeatable;
    private Map<String, Object> pluginOptions;

    public Builder() {}

    Builder(String name, FieldType type) {
      this.name = name;
      this.type = type;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder type(FieldType type) {
      this.type = type;
      return this;
    }

    public Builder required(boolean v) {
      this.required = v;
      return this;
    }

    public Builder unique(boolean v) {
      this.unique = v;
      return this;
    }

    public Builder defaultValue(String v) {
      this.defaultValue = v;
      return this;
    }

    public Builder minLength(Integer v) {
      this.minLength = v;
      return this;
    }

    public Builder maxLength(Integer v) {
      this.maxLength = v;
      return this;
    }

    public Builder min(Integer v) {
      this.min = v;
      return this;
    }

    public Builder max(Integer v) {
      this.max = v;
      return this;
    }

    public Builder regex(String v) {
      this.regex = v;
      return this;
    }

    public Builder enumValues(List<String> v) {
      this.enumValues = v;
      return this;
    }

    @JsonProperty("private")
    public Builder privateField(boolean v) {
      this.privateField = v;
      return this;
    }

    @JsonProperty("localized")
    public Builder localized(boolean v) {
      this.localized = v;
      return this;
    }

    public Builder target(String v) {
      this.target = v;
      return this;
    }

    public Builder component(String v) {
      this.component = v;
      return this;
    }

    public Builder allowedComponents(List<String> v) {
      this.allowedComponents = v;
      return this;
    }

    public Builder minComponents(int v) {
      this.minComponents = v;
      return this;
    }

    public Builder maxComponents(int v) {
      this.maxComponents = v;
      return this;
    }

    public Builder repeatable(boolean v) {
      this.repeatable = v;
      return this;
    }

    public Builder pluginOptions(Map<String, Object> v) {
      this.pluginOptions = v;
      return this;
    }

    public FieldDefinition build() {
      return new FieldDefinition(this);
    }
  }
}
