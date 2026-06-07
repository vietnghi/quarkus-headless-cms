package com.quarkus.cms.core.schema.annotation;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.model.RelationType;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Converts annotated classes into {@link ContentTypeDefinition} and {@link ComponentDefinition}
 * objects.
 *
 * <p>This is the bridge between the annotation-based schema system and the existing schema model.
 * It inspects each annotated class's fields for {@link ContentTypeField @ContentTypeField}, {@link
 * ContentTypeRelation @ContentTypeRelation}, and {@link DynamicZone @DynamicZone} annotations and
 * builds the corresponding definition objects.
 */
@ApplicationScoped
public class AnnotationSchemaBuilder {

  /**
   * Builds a {@link ContentTypeDefinition} from a class annotated with {@link ContentType
   * @ContentType}.
   *
   * @param annotatedClass the annotated class
   * @return the built content-type definition
   * @throws IllegalArgumentException if the class lacks {@code @ContentType}
   */
  public ContentTypeDefinition buildContentType(Class<?> annotatedClass) {
    ContentType ctAnn = annotatedClass.getAnnotation(ContentType.class);
    if (ctAnn == null) {
      throw new IllegalArgumentException(
          "Class " + annotatedClass.getName() + " is not annotated with @ContentType");
    }

    String uid = ctAnn.uid();
    ContentTypeKind kind = ctAnn.kind();
    String singularName = resolveString(ctAnn.singularName(), lastSegment(uid));
    String pluralName = resolveString(ctAnn.pluralName(), singularName + "s");
    String displayName = resolveString(ctAnn.displayName(), capitalize(singularName));

    ContentTypeDefinition.Builder builder =
        ContentTypeDefinition.builder(uid, kind)
            .singularName(singularName)
            .pluralName(pluralName)
            .displayName(displayName)
            .description(nullIfBlank(ctAnn.description()))
            .draftAndPublish(ctAnn.draftAndPublish())
            .localized(ctAnn.localized());

    List<FieldDefinition> fields = new ArrayList<>();
    List<RelationDefinition> relations = new ArrayList<>();
    List<DynamicZoneDefinition> dynamicZones = new ArrayList<>();

    processFields(annotatedClass, fields, relations, dynamicZones);

    builder.fields(fields);
    builder.relations(relations);
    builder.dynamicZones(dynamicZones);

    ContentTypeDefinition def = builder.build();
    Log.debugf("Built content-type definition from @ContentType %s: %s", annotatedClass.getName(), def.getUid());
    return def;
  }

  /**
   * Builds a {@link ComponentDefinition} from a class annotated with {@link Component @Component}.
   *
   * @param annotatedClass the annotated class
   * @return the built component definition
   * @throws IllegalArgumentException if the class lacks {@code @Component}
   */
  public ComponentDefinition buildComponent(Class<?> annotatedClass) {
    Component compAnn = annotatedClass.getAnnotation(Component.class);
    if (compAnn == null) {
      throw new IllegalArgumentException(
          "Class " + annotatedClass.getName() + " is not annotated with @Component");
    }

    String uid = compAnn.uid();
    String displayName = resolveString(compAnn.displayName(), capitalize(lastSegment(uid)));

    ComponentDefinition.Builder builder =
        ComponentDefinition.builder(uid)
            .category(nullIfBlank(compAnn.category()))
            .displayName(displayName)
            .description(nullIfBlank(compAnn.description()));

    List<FieldDefinition> fields = new ArrayList<>();
    List<RelationDefinition> relations = new ArrayList<>();
    List<DynamicZoneDefinition> dynamicZones = new ArrayList<>();

    processFields(annotatedClass, fields, relations, dynamicZones);

    builder.fields(fields);

    ComponentDefinition def = builder.build();
    Log.debugf("Built component definition from @Component %s: %s", annotatedClass.getName(), def.getUid());
    return def;
  }

  // ---- Internal helpers ----

  private void processFields(
      Class<?> clazz,
      List<FieldDefinition> fields,
      List<RelationDefinition> relations,
      List<DynamicZoneDefinition> dynamicZones) {

    // Walk the class hierarchy
    for (Field javaField : getAllFields(clazz)) {
      ContentTypeField fieldAnn = javaField.getAnnotation(ContentTypeField.class);
      ContentTypeRelation relAnn = javaField.getAnnotation(ContentTypeRelation.class);
      DynamicZone dzAnn = javaField.getAnnotation(DynamicZone.class);

      if (fieldAnn != null) {
        // Check if it's actually a relation field in disguise
        if (fieldAnn.type() == FieldType.RELATION) {
          // Only add as a relation if no explicit @ContentTypeRelation exists
          if (relAnn == null && fieldAnn.target() != null && !fieldAnn.target().isEmpty()) {
            relations.add(buildRelationFromField(javaField, fieldAnn));
          } else if (relAnn != null) {
            // Let relAnn handle it
            relations.add(buildRelation(javaField.getName(), relAnn));
          }
        } else {
          fields.add(buildField(fieldAnn, javaField));
        }
      }

      if (relAnn != null) {
        // Only add if not already handled as a RELATION type field
        if (fieldAnn == null || fieldAnn.type() != FieldType.RELATION) {
          relations.add(buildRelation(javaField.getName(), relAnn));
        }
      }

      if (dzAnn != null) {
        dynamicZones.add(buildDynamicZone(dzAnn, javaField));
      }
    }
  }

  private FieldDefinition buildField(ContentTypeField ann, Field javaField) {
    String fieldName = javaField.getName();
    FieldDefinition.Builder builder =
        FieldDefinition.builder(fieldName, ann.type())
            .required(ann.required())
            .unique(ann.unique())
            .localized(ann.localized());

    if (!ann.defaultValue().isEmpty()) {
      builder.defaultValue(ann.defaultValue());
    }
    if (ann.minLength() >= 0) {
      builder.minLength(ann.minLength());
    }
    if (ann.maxLength() >= 0) {
      builder.maxLength(ann.maxLength());
    }
    if (ann.min() != Integer.MIN_VALUE) {
      builder.min(ann.min());
    }
    if (ann.max() != Integer.MIN_VALUE) {
      builder.max(ann.max());
    }
    if (!ann.regex().isEmpty()) {
      builder.regex(ann.regex());
    }
    if (ann.enumValues().length > 0) {
      builder.enumValues(Arrays.asList(ann.enumValues()));
    }
    if (ann.pvt()) {
      builder.privateField(true);
    }
    if (ann.type() == FieldType.COMPONENT && !ann.component().isEmpty()) {
      builder.component(ann.component());
    }
    if (ann.type() == FieldType.DYNAMIC_ZONE && ann.allowedComponents().length > 0) {
      builder.allowedComponents(Arrays.asList(ann.allowedComponents()));
    }
    builder.repeatable(ann.repeatable());
    builder.minComponents(ann.minComponents());
    if (ann.maxComponents() >= 0) {
      builder.maxComponents(ann.maxComponents());
    }

    return builder.build();
  }

  private RelationDefinition buildRelationFromField(Field javaField, ContentTypeField fieldAnn) {
    String fieldName = javaField.getName();
    // For RELATION type fields without @ContentTypeRelation, we create a basic relation
    // The user should use @ContentTypeRelation for full control
    RelationType relType =
        fieldAnn.repeatable() ? RelationType.ONE_TO_MANY : RelationType.MANY_TO_ONE;

    return RelationDefinition.builder(fieldName, relType, fieldAnn.target()).build();
  }

  private RelationDefinition buildRelation(String fieldName, ContentTypeRelation ann) {
    RelationDefinition.Builder builder =
        RelationDefinition.builder(fieldName, ann.type(), ann.target());

    if (!ann.targetAttribute().isEmpty()) {
      builder.targetAttribute(ann.targetAttribute());
    }
    if (!ann.joinTable().isEmpty()) {
      builder.joinTable(ann.joinTable());
    }
    if (ann.dominant()) {
      builder.dominant(true);
    }
    if (!ann.morphColumnType().isEmpty()) {
      builder.morphColumnType(ann.morphColumnType());
    }

    return builder.build();
  }

  private DynamicZoneDefinition buildDynamicZone(DynamicZone ann, Field javaField) {
    String name = ann.name().isEmpty() ? javaField.getName() : ann.name();
    return DynamicZoneDefinition.builder(name)
        .components(
            ann.allowedComponents().length > 0
                ? Arrays.asList(ann.allowedComponents())
                : List.of())
        .min(ann.min())
        .max(ann.max())
        .required(ann.required())
        .build();
  }

  private static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      fields.addAll(Arrays.asList(current.getDeclaredFields()));
      current = current.getSuperclass();
    }
    return fields;
  }

  private static String resolveString(String explicit, String fallback) {
    return (explicit == null || explicit.isEmpty()) ? fallback : explicit;
  }

  private static String lastSegment(String uid) {
    if (uid == null || uid.isEmpty()) return "untitled";
    int dot = uid.lastIndexOf('.');
    int colon = uid.lastIndexOf(':');
    int last = Math.max(dot, colon);
    return last >= 0 && last < uid.length() - 1 ? uid.substring(last + 1) : uid;
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String nullIfBlank(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
