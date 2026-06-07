package com.quarkus.cms.graphql.model;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.i18n.dto.LocaleDto;

import java.util.List;

/**
 * Schema introspection types that expose the CMS content-type and component definitions
 * through GraphQL, allowing clients to discover available fields, relations, and zones.
 */
public final class SchemaTypes {

  private SchemaTypes() {}

  /** GraphQL type exposing a content-type definition. */
  public static class ContentTypeSchema {

    private final String uid;
    private final String kind;
    private final String singularName;
    private final String pluralName;
    private final String displayName;
    private final String description;
    private final List<FieldSchema> fields;
    private final List<RelationSchema> relations;
    private final List<DynamicZoneSchema> dynamicZones;
    private final boolean draftAndPublish;
    private final boolean localized;

    public ContentTypeSchema(ContentTypeDefinition ct) {
      this.uid = ct.getUid();
      this.kind = ct.getKind().name();
      this.singularName = ct.getSingularName();
      this.pluralName = ct.getPluralName();
      this.displayName = ct.getDisplayName();
      this.description = ct.getDescription();
      this.fields = ct.getFields().stream().map(FieldSchema::new).toList();
      this.relations = ct.getRelations().stream().map(RelationSchema::new).toList();
      this.dynamicZones =
          ct.getDynamicZones().stream().map(DynamicZoneSchema::new).toList();
      this.draftAndPublish = ct.isDraftAndPublish();
      this.localized = ct.isLocalized();
    }

    public String getUid() {
      return uid;
    }

    public String getKind() {
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

    public List<FieldSchema> getFields() {
      return fields;
    }

    public List<RelationSchema> getRelations() {
      return relations;
    }

    public List<DynamicZoneSchema> getDynamicZones() {
      return dynamicZones;
    }

    public boolean isDraftAndPublish() {
      return draftAndPublish;
    }

    public boolean isLocalized() {
      return localized;
    }
  }

  /** GraphQL type exposing a field definition. */
  public static class FieldSchema {

    private final String name;
    private final String type;
    private final boolean required;
    private final boolean unique;
    private final String defaultValue;
    private final Integer minLength;
    private final Integer maxLength;
    private final Integer min;
    private final Integer max;
    private final String regex;
    private final boolean repeatable;
    private final String target;
    private final String component;

    public FieldSchema(FieldDefinition f) {
      this.name = f.getName();
      this.type = f.getType().name();
      this.required = f.isRequired();
      this.unique = f.isUnique();
      this.defaultValue = f.getDefaultValue();
      this.minLength = f.getMinLength();
      this.maxLength = f.getMaxLength();
      this.min = f.getMin();
      this.max = f.getMax();
      this.regex = f.getRegex();
      this.repeatable = f.isRepeatable();
      this.target = f.getTarget();
      this.component = f.getComponent();
    }

    public String getName() {
      return name;
    }

    public String getType() {
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

    public boolean isRepeatable() {
      return repeatable;
    }

    public String getTarget() {
      return target;
    }

    public String getComponent() {
      return component;
    }
  }

  /** GraphQL type exposing a relation definition. */
  public static class RelationSchema {

    private final String fieldName;
    private final String type;
    private final String target;
    private final String targetAttribute;
    private final boolean dominant;

    public RelationSchema(RelationDefinition r) {
      this.fieldName = r.getFieldName();
      this.type = r.getType().name();
      this.target = r.getTarget();
      this.targetAttribute = r.getTargetAttribute();
      this.dominant = r.isDominant();
    }

    public String getFieldName() {
      return fieldName;
    }

    public String getType() {
      return type;
    }

    public String getTarget() {
      return target;
    }

    public String getTargetAttribute() {
      return targetAttribute;
    }

    public boolean isDominant() {
      return dominant;
    }
  }

  /** GraphQL type exposing a dynamic zone definition. */
  public static class DynamicZoneSchema {

    private final String name;
    private final List<String> components;
    private final int min;
    private final int max;
    private final boolean required;

    public DynamicZoneSchema(DynamicZoneDefinition dz) {
      this.name = dz.getName();
      this.components = dz.getComponents();
      this.min = dz.getMin();
      this.max = dz.getMax();
      this.required = dz.isRequired();
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
  }

  /** GraphQL type exposing a component definition. */
  public static class ComponentSchema {

    private final String uid;
    private final String category;
    private final String displayName;
    private final String description;
    private final List<FieldSchema> fields;

    public ComponentSchema(ComponentDefinition comp) {
      this.uid = comp.getUid();
      this.category = comp.getCategory();
      this.displayName = comp.getDisplayName();
      this.description = comp.getDescription();
      this.fields = comp.getFields().stream().map(FieldSchema::new).toList();
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

    public List<FieldSchema> getFields() {
      return fields;
    }
  }

  /** GraphQL type exposing a locale definition. */
  public static class LocaleInfo {

    private final String code;
    private final String displayName;
    private final boolean isDefault;
    private final boolean enabled;

    public LocaleInfo(LocaleDto dto) {
      this.code = dto.code;
      this.displayName = dto.displayName;
      this.isDefault = dto.isDefault;
      this.enabled = dto.enabled;
    }

    public String getCode() {
      return code;
    }

    public String getDisplayName() {
      return displayName;
    }

    public boolean isIsDefault() {
      return isDefault;
    }

    public boolean isEnabled() {
      return enabled;
    }
  }
}
