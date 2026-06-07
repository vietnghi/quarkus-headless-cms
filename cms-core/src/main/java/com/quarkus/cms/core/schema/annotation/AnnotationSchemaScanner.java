package com.quarkus.cms.core.schema.annotation;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans the CDI bean container (or explicit class list) for classes annotated with {@link
 * ContentType @ContentType} or {@link Component @Component} and registers them with the {@link
 * SchemaStorageService schema storage}.
 *
 * <p>Two discovery strategies:
 *
 * <ol>
 *   <li><b>CDI bean discovery</b> — looks for all beans that carry {@code @ContentType} or {@code
 *       @Component} annotations (useful when content types are themselves CDI beans).
 *   <li><b>Explicit class registration</b> — call {@link #registerContentType(Class)} or {@link
 *       #registerComponent(Class)} directly for POJOs that are not CDI beans.
 * </ol>
 *
 * <p>This service is invoked at application startup by the {@link
 * com.quarkus.cms.core.schema.config.SchemaInitializer} after loading config-file schemas, so
 * annotation-defined schemas override or complement file-based ones.
 */
@ApplicationScoped
public class AnnotationSchemaScanner {

  private final SchemaStorageService storageService;
  private final AnnotationSchemaBuilder builder;

  @Inject
  public AnnotationSchemaScanner(
      SchemaStorageService storageService, AnnotationSchemaBuilder builder) {
    this.storageService = storageService;
    this.builder = builder;
  }

  /**
   * Scans the CDI bean container for all beans annotated with {@link ContentType @ContentType} or
   * {@link Component @Component} and registers them as schema definitions.
   *
   * @return the total number of schemas registered (content types + components)
   */
  public int scanAndRegister() {
    int count = 0;

    // Scan for @ContentType beans
    Instance<Object> ctBeans = CDI.current().select(Object.class, new ContentTypeLiteral());
    for (Object bean : ctBeans) {
      try {
        ContentTypeDefinition def = builder.buildContentType(bean.getClass());
        storageService.registerContentType(def, "Registered from @ContentType annotation", "system");
        count++;
        Log.infof("Registered content type from @ContentType annotation on %s: %s",
            bean.getClass().getName(), def.getUid());
      } catch (Exception e) {
        Log.errorf("Failed to register @ContentType on %s: %s", bean.getClass().getName(), e.getMessage());
      }
    }

    // Scan for @Component beans
    Instance<Object> compBeans = CDI.current().select(Object.class, new ComponentLiteral());
    for (Object bean : compBeans) {
      try {
        ComponentDefinition def = builder.buildComponent(bean.getClass());
        storageService.registerComponent(def, "Registered from @Component annotation", "system");
        count++;
        Log.infof("Registered component from @Component annotation on %s: %s",
            bean.getClass().getName(), def.getUid());
      } catch (Exception e) {
        Log.errorf("Failed to register @Component on %s: %s", bean.getClass().getName(), e.getMessage());
      }
    }

    return count;
  }

  /**
   * Registers a specific class annotated with {@link ContentType @ContentType} as a content-type
   * schema definition.
   *
   * @param annotatedClass the class annotated with {@code @ContentType}
   * @return the registered content-type definition
   */
  public ContentTypeDefinition registerContentType(Class<?> annotatedClass) {
    ContentTypeDefinition def = builder.buildContentType(annotatedClass);
    storageService.registerContentType(def, "Registered from @ContentType class", "system");
    Log.infof("Registered content type from class %s: %s", annotatedClass.getName(), def.getUid());
    return def;
  }

  /**
   * Registers a specific class annotated with {@link Component @Component} as a component schema
   * definition.
   *
   * @param annotatedClass the class annotated with {@code @Component}
   * @return the registered component definition
   */
  public ComponentDefinition registerComponent(Class<?> annotatedClass) {
    ComponentDefinition def = builder.buildComponent(annotatedClass);
    storageService.registerComponent(def, "Registered from @Component class", "system");
    Log.infof("Registered component from class %s: %s", annotatedClass.getName(), def.getUid());
    return def;
  }

  /**
   * Registers multiple annotated content-type classes at once.
   *
   * @param annotatedClasses classes annotated with {@code @ContentType}
   * @return list of registered definitions
   */
  public List<ContentTypeDefinition> registerContentTypes(Class<?>... annotatedClasses) {
    List<ContentTypeDefinition> result = new ArrayList<>();
    for (Class<?> clazz : annotatedClasses) {
      if (clazz.isAnnotationPresent(ContentType.class)) {
        result.add(registerContentType(clazz));
      } else {
        Log.warnf("Skipping %s: missing @ContentType annotation", clazz.getName());
      }
    }
    return result;
  }

  /**
   * Registers multiple annotated component classes at once.
   *
   * @param annotatedClasses classes annotated with {@code @Component}
   * @return list of registered definitions
   */
  public List<ComponentDefinition> registerComponents(Class<?>... annotatedClasses) {
    List<ComponentDefinition> result = new ArrayList<>();
    for (Class<?> clazz : annotatedClasses) {
      if (clazz.isAnnotationPresent(Component.class)) {
        result.add(registerComponent(clazz));
      } else {
        Log.warnf("Skipping %s: missing @Component annotation", clazz.getName());
      }
    }
    return result;
  }
}
