package com.quarkus.cms.core.schema.builder;

import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.model.RelationDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for defining content types programmatically at runtime.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * ContentTypeDefinition article = ContentTypeBuilder.create("api::article.article", ContentTypeKind.COLLECTION_TYPE)
 *     .singularName("article")
 *     .pluralName("articles")
 *     .displayName("Article")
 *     .addStringField("title").required(true).maxLength(255).add()
 *     .addRichTextField("body").add()
 *     .addRelation(RelationDefinition.builder("author", RelationType.MANY_TO_ONE, "api::author.author")
 *         .targetAttribute("articles").build())
 *     .addDynamicZone(DynamicZoneDefinition.builder("contentBlocks")
 *         .components(List.of("shared.quote", "shared.slider")).min(0).max(10).build())
 *     .build();
 * }</pre>
 *
 * <p>Also supports annotation-based definition via {@code @ContentType} — see {@link
 * com.quarkus.cms.core.schema.annotation.ContentType}.
 */
public final class ContentTypeBuilder {

  private final String uid;
  private final ContentTypeKind kind;
  private String singularName;
  private String pluralName;
  private String displayName;
  private String description;
  private final List<FieldDefinition> fields = new ArrayList<>();
  private final List<RelationDefinition> relations = new ArrayList<>();
  private final List<String> components = new ArrayList<>();
  private final List<DynamicZoneDefinition> dynamicZones = new ArrayList<>();
  private boolean draftAndPublish = true;
  private boolean localized;
  private Map<String, Object> pluginOptions;
  private Map<String, Object> options;

  private ContentTypeBuilder(String uid, ContentTypeKind kind) {
    this.uid = uid;
    this.kind = kind;
  }

  public static ContentTypeBuilder create(String uid, ContentTypeKind kind) {
    return new ContentTypeBuilder(uid, kind);
  }

  public ContentTypeBuilder singularName(String name) {
    this.singularName = name;
    return this;
  }

  public ContentTypeBuilder pluralName(String name) {
    this.pluralName = name;
    return this;
  }

  public ContentTypeBuilder displayName(String name) {
    this.displayName = name;
    return this;
  }

  public ContentTypeBuilder description(String desc) {
    this.description = desc;
    return this;
  }

  /** Adds a scalar or component field. */
  public ContentTypeBuilder addField(FieldDefinition field) {
    fields.add(field);
    return this;
  }

  /** Adds a relation definition. */
  public ContentTypeBuilder addRelation(RelationDefinition rel) {
    relations.add(rel);
    return this;
  }

  /** Registers a component UID used by component-type fields within this content type. */
  public ContentTypeBuilder addComponent(String componentUid) {
    components.add(componentUid);
    return this;
  }

  /** Adds a dynamic zone definition. */
  public ContentTypeBuilder addDynamicZone(DynamicZoneDefinition zone) {
    dynamicZones.add(zone);
    return this;
  }

  public ContentTypeBuilder draftAndPublish(boolean v) {
    this.draftAndPublish = v;
    return this;
  }

  public ContentTypeBuilder localized(boolean v) {
    this.localized = v;
    return this;
  }

  public ContentTypeBuilder pluginOptions(Map<String, Object> opts) {
    this.pluginOptions = opts;
    return this;
  }

  public ContentTypeBuilder options(Map<String, Object> opts) {
    this.options = opts;
    return this;
  }

  public ContentTypeDefinition build() {
    return ContentTypeDefinition.builder(uid, kind)
        .singularName(singularName)
        .pluralName(pluralName)
        .displayName(displayName)
        .description(description)
        .fields(fields)
        .relations(relations)
        .components(components)
        .dynamicZones(dynamicZones)
        .draftAndPublish(draftAndPublish)
        .localized(localized)
        .pluginOptions(pluginOptions)
        .options(options)
        .build();
  }

  // ---- Convenience field builders ----

  /** Starts building a STRING field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addStringField(String name) {
    return new FieldInserter(name, FieldType.STRING);
  }

  /** Starts building a TEXT (long string) field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addTextField(String name) {
    return new FieldInserter(name, FieldType.TEXT);
  }

  /** Starts building an INTEGER field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addIntegerField(String name) {
    return new FieldInserter(name, FieldType.INTEGER);
  }

  /** Starts building a FLOAT field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addFloatField(String name) {
    return new FieldInserter(name, FieldType.FLOAT);
  }

  /** Starts building a DECIMAL field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addDecimalField(String name) {
    return new FieldInserter(name, FieldType.DECIMAL);
  }

  /** Starts building a BOOLEAN field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addBooleanField(String name) {
    return new FieldInserter(name, FieldType.BOOLEAN);
  }

  /** Starts building a DATE field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addDateField(String name) {
    return new FieldInserter(name, FieldType.DATE);
  }

  /** Starts building a DATETIME field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addDateTimeField(String name) {
    return new FieldInserter(name, FieldType.DATETIME);
  }

  /** Starts building an EMAIL field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addEmailField(String name) {
    return new FieldInserter(name, FieldType.EMAIL);
  }

  /** Starts building a PASSWORD field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addPasswordField(String name) {
    return new FieldInserter(name, FieldType.PASSWORD);
  }

  /** Starts building a UID field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addUidField(String name) {
    return new FieldInserter(name, FieldType.UID);
  }

  /** Starts building an ENUMERATION field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addEnumerationField(String name, String... values) {
    return new FieldInserter(name, FieldType.ENUMERATION).enumValues(List.of(values));
  }

  /** Starts building a JSON field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addJsonField(String name) {
    return new FieldInserter(name, FieldType.JSON);
  }

  /** Starts building a RICHTEXT field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addRichTextField(String name) {
    return new FieldInserter(name, FieldType.RICHTEXT);
  }

  /** Starts building a MEDIA field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addMediaField(String name) {
    return new FieldInserter(name, FieldType.MEDIA);
  }

  /** Starts building a COMPONENT field referencing a component UID. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addComponentField(String name, String componentUid) {
    return new FieldInserter(name, FieldType.COMPONENT).component(componentUid);
  }

  /**
   * Convenience inner builder for creating a {@link FieldDefinition} and immediately adding it to
   * the parent content type. Call {@link #add()} to finish and chain back to the parent builder.
   */
  public class FieldInserter {
    private final FieldDefinition.Builder fieldBuilder;

    FieldInserter(String name, FieldType type) {
      this.fieldBuilder = FieldDefinition.builder(name, type);
    }

    public FieldInserter required(boolean v) { fieldBuilder.required(v); return this; }
    public FieldInserter unique(boolean v) { fieldBuilder.unique(v); return this; }
    public FieldInserter defaultValue(String v) { fieldBuilder.defaultValue(v); return this; }
    public FieldInserter minLength(int v) { fieldBuilder.minLength(v); return this; }
    public FieldInserter maxLength(int v) { fieldBuilder.maxLength(v); return this; }
    public FieldInserter min(int v) { fieldBuilder.min(v); return this; }
    public FieldInserter max(int v) { fieldBuilder.max(v); return this; }
    public FieldInserter regex(String v) { fieldBuilder.regex(v); return this; }
    public FieldInserter enumValues(List<String> v) { fieldBuilder.enumValues(v); return this; }
    public FieldInserter pvt(boolean v) { fieldBuilder.privateField(v); return this; }
    public FieldInserter localized(boolean v) { fieldBuilder.localized(v); return this; }
    public FieldInserter target(String v) { fieldBuilder.target(v); return this; }
    public FieldInserter component(String v) { fieldBuilder.component(v); return this; }
    public FieldInserter repeatable(boolean v) { fieldBuilder.repeatable(v); return this; }

    /** Builds the field and adds it to the parent content type. Returns the parent builder. */
    public ContentTypeBuilder add() {
      ContentTypeBuilder.this.fields.add(fieldBuilder.build());
      return ContentTypeBuilder.this;
    }
  }
}
