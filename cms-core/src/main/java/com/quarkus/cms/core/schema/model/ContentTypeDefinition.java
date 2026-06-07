package com.quarkus.cms.core.schema.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Defines the full schema of a content type — its kind, fields, relations, component associations,
 * dynamic zones, and display metadata.
 *
 * <p>This is the central schema model object. It is serialized to JSON and stored in the {@code
 * core_schema} table. At runtime, the {@code SchemaService} maintains an in-memory cache of all
 * registered content types.
 */
@JsonDeserialize(builder = ContentTypeDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentTypeDefinition {

  // ---- identity ----
  private final String uid; // e.g. "api::article.article" (Strapi convention)
  private final ContentTypeKind kind;

  // ---- metadata ----
  private final String singularName; // "article"
  private final String pluralName; // "articles"
  private final String displayName; // "Article"
  private final String description;

  // ---- schema body ----
  private final List<FieldDefinition> fields; // ordered list of scalar & component fields
  private final Map<String, FieldDefinition> fieldsByName; // fast lookup

  // ---- relations ----
  private final List<RelationDefinition> relations;

  // ---- components & dynamic zones ----
  private final List<String> components; // simple component UIDs
  private final List<DynamicZoneDefinition> dynamicZones;

  // ---- options ----
  private final boolean draftAndPublish;
  private final boolean localized;
  private final Map<String, Object> pluginOptions;
  private final Map<String, Object> options;

  private ContentTypeDefinition(Builder builder) {
    if (builder.uid == null || builder.uid.isBlank())
      throw new IllegalArgumentException("content-type uid is required");
    if (builder.kind == null) throw new IllegalArgumentException("content-type kind is required");
    this.uid = builder.uid;
    this.kind = builder.kind;
    this.singularName = builder.singularName;
    this.pluralName = builder.pluralName;
    this.displayName = builder.displayName;
    this.description = builder.description;
    this.fields = builder.fields == null ? List.of() : List.copyOf(builder.fields);
    this.relations = builder.relations == null ? List.of() : List.copyOf(builder.relations);
    this.components = builder.components == null ? List.of() : List.copyOf(builder.components);
    this.dynamicZones =
        builder.dynamicZones == null ? List.of() : List.copyOf(builder.dynamicZones);
    this.draftAndPublish = builder.draftAndPublish;
    this.localized = builder.localized;
    this.pluginOptions =
        builder.pluginOptions == null ? Map.of() : Map.copyOf(builder.pluginOptions);
    this.options = builder.options == null ? Map.of() : Map.copyOf(builder.options);

    // build fast lookup map
    Map<String, FieldDefinition> map = new LinkedHashMap<>();
    for (FieldDefinition f : this.fields) {
      map.put(f.getName(), f);
    }
    this.fieldsByName = Collections.unmodifiableMap(map);
  }

  // ---- accessors ----

  public String getUid() {
    return uid;
  }

  public ContentTypeKind getKind() {
    return kind;
  }

  public String getSingularName() {
    return singularName;
  }

  public String getPluralName() {
    return pluralName;
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

  @JsonIgnore
  public Map<String, FieldDefinition> getFieldsByName() {
    return fieldsByName;
  }

  public List<RelationDefinition> getRelations() {
    return relations;
  }

  public List<String> getComponents() {
    return components;
  }

  public List<DynamicZoneDefinition> getDynamicZones() {
    return dynamicZones;
  }

  public boolean isDraftAndPublish() {
    return draftAndPublish;
  }

  public boolean isLocalized() {
    return localized;
  }

  public Map<String, Object> getPluginOptions() {
    return pluginOptions;
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  // ---- derived accessors ----

  @JsonIgnore
  public boolean isCollectionType() {
    return kind == ContentTypeKind.COLLECTION_TYPE;
  }

  @JsonIgnore
  public boolean isSingleType() {
    return kind == ContentTypeKind.SINGLE_TYPE;
  }

  /** Returns the field definition with the given name, or {@code null}. */
  public FieldDefinition getField(String name) {
    return fieldsByName.get(name);
  }

  /** Returns all relation field definitions that point to the given target UID. */
  public List<RelationDefinition> getRelationsTo(String targetUid) {
    return relations.stream()
        .filter(r -> targetUid.equals(r.getTarget()) || "*".equals(r.getTarget()))
        .toList();
  }

  /** Returns the dynamic zone definition with the given name, or {@code null}. */
  public DynamicZoneDefinition getDynamicZone(String name) {
    return dynamicZones.stream().filter(dz -> dz.getName().equals(name)).findFirst().orElse(null);
  }

  public static Builder builder(String uid, ContentTypeKind kind) {
    return new Builder(uid, kind);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ContentTypeDefinition that)) return false;
    return uid.equals(that.uid);
  }

  @Override
  public int hashCode() {
    return uid.hashCode();
  }

  @Override
  public String toString() {
    return "ContentTypeDefinition{uid='"
        + uid
        + "', kind="
        + kind
        + ", fields="
        + fields.size()
        + ", relations="
        + relations.size()
        + "}";
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private String uid;
    private ContentTypeKind kind;
    private String singularName;
    private String pluralName;
    private String displayName;
    private String description;
    private List<FieldDefinition> fields;
    private List<RelationDefinition> relations;
    private List<String> components;
    private List<DynamicZoneDefinition> dynamicZones;
    private boolean draftAndPublish = true;
    private boolean localized;
    private Map<String, Object> pluginOptions;
    private Map<String, Object> options;

    public Builder() {}

    Builder(String uid, ContentTypeKind kind) {
      this.uid = uid;
      this.kind = kind;
    }

    public Builder uid(String uid) {
      this.uid = uid;
      return this;
    }

    public Builder kind(ContentTypeKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder singularName(String name) {
      this.singularName = name;
      return this;
    }

    public Builder pluralName(String name) {
      this.pluralName = name;
      return this;
    }

    public Builder displayName(String name) {
      this.displayName = name;
      return this;
    }

    public Builder description(String desc) {
      this.description = desc;
      return this;
    }

    public Builder fields(List<FieldDefinition> fields) {
      this.fields = fields;
      return this;
    }

    public Builder relations(List<RelationDefinition> relations) {
      this.relations = relations;
      return this;
    }

    public Builder components(List<String> components) {
      this.components = components;
      return this;
    }

    public Builder dynamicZones(List<DynamicZoneDefinition> zones) {
      this.dynamicZones = zones;
      return this;
    }

    public Builder draftAndPublish(boolean v) {
      this.draftAndPublish = v;
      return this;
    }

    public Builder localized(boolean v) {
      this.localized = v;
      return this;
    }

    public Builder pluginOptions(Map<String, Object> opts) {
      this.pluginOptions = opts;
      return this;
    }

    public Builder options(Map<String, Object> opts) {
      this.options = opts;
      return this;
    }

    public ContentTypeDefinition build() {
      return new ContentTypeDefinition(this);
    }
  }
}
