package com.quarkus.cms.core.schema.builder;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for defining reusable components programmatically.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * ComponentDefinition seo = ComponentBuilder.create("shared.seo")
 *     .category("shared")
 *     .displayName("SEO")
 *     .addStringField("metaTitle").maxLength(60).add()
 *     .addTextField("metaDescription").maxLength(160).add()
 *     .build();
 * }</pre>
 *
 * <p>Also supports annotation-based definition via {@code @Component} — see {@link
 * com.quarkus.cms.core.schema.annotation.Component}.
 */
public final class ComponentBuilder {

  private final String uid;
  private String category;
  private String displayName;
  private String description;
  private final List<FieldDefinition> fields = new ArrayList<>();
  private Map<String, Object> options;

  private ComponentBuilder(String uid) {
    this.uid = uid;
  }

  public static ComponentBuilder create(String uid) {
    return new ComponentBuilder(uid);
  }

  public ComponentBuilder category(String category) {
    this.category = category;
    return this;
  }

  public ComponentBuilder displayName(String name) {
    this.displayName = name;
    return this;
  }

  public ComponentBuilder description(String desc) {
    this.description = desc;
    return this;
  }

  public ComponentBuilder addField(FieldDefinition field) {
    fields.add(field);
    return this;
  }

  public ComponentBuilder options(Map<String, Object> opts) {
    this.options = opts;
    return this;
  }

  public ComponentDefinition build() {
    return ComponentDefinition.builder(uid)
        .category(category)
        .displayName(displayName)
        .description(description)
        .fields(fields)
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

  /** Starts building a BOOLEAN field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addBooleanField(String name) {
    return new FieldInserter(name, FieldType.BOOLEAN);
  }

  /** Starts building a DATE field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addDateField(String name) {
    return new FieldInserter(name, FieldType.DATE);
  }

  /** Starts building an EMAIL field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addEmailField(String name) {
    return new FieldInserter(name, FieldType.EMAIL);
  }

  /** Starts building a JSON field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addJsonField(String name) {
    return new FieldInserter(name, FieldType.JSON);
  }

  /** Starts building a MEDIA field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addMediaField(String name) {
    return new FieldInserter(name, FieldType.MEDIA);
  }

  /** Starts building a RICHTEXT field. Call {@link FieldInserter#add()} to finish. */
  public FieldInserter addRichTextField(String name) {
    return new FieldInserter(name, FieldType.RICHTEXT);
  }

  /**
   * Convenience inner builder for creating a {@link FieldDefinition} and immediately adding it to
   * the parent component. Call {@link #add()} to finish and chain back to the parent builder.
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
    public FieldInserter pvt(boolean v) { fieldBuilder.privateField(v); return this; }
    public FieldInserter localized(boolean v) { fieldBuilder.localized(v); return this; }
    public FieldInserter repeatable(boolean v) { fieldBuilder.repeatable(v); return this; }

    /** Builds the field and adds it to the parent component. Returns the parent builder. */
    public ComponentBuilder add() {
      ComponentBuilder.this.fields.add(fieldBuilder.build());
      return ComponentBuilder.this;
    }
  }
}
