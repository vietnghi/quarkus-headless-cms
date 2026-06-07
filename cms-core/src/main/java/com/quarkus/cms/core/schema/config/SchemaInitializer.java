package com.quarkus.cms.core.schema.config;

import com.quarkus.cms.core.schema.annotation.AnnotationSchemaScanner;
import com.quarkus.cms.core.schema.config.SchemaConfigLoader.SchemaLoadResult;
import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Initialises the schema system on application startup.
 *
 * <p>Phases (in order):
 *
 * <ol>
 *   <li>Load schemas from the database (persisted definitions)
 *   <li>Load and upsert schemas from configuration files (JSON / YAML)
 *   <li>Scan for and register schemas from {@link
 *       com.quarkus.cms.core.schema.annotation.ContentType @ContentType} and {@link
 *       com.quarkus.cms.core.schema.annotation.Component @Component} annotations
 * </ol>
 *
 * <p>Later phases take precedence for same-UID schemas (annotation > config file > database).
 */
@ApplicationScoped
public class SchemaInitializer {

  @Inject SchemaStorageService storageService;

  @Inject SchemaConfigLoader configLoader;

  @Inject AnnotationSchemaScanner annotationScanner;

  @ConfigProperty(name = "cms.schema.init.enabled", defaultValue = "true")
  boolean schemaInitEnabled;

  void onStart(@Observes StartupEvent event) {
    if (!schemaInitEnabled) {
      Log.info("CMS schema initialization disabled via cms.schema.init.enabled=false");
      return;
    }

    Log.info("Initializing CMS schema system...");

    // Phase 1: load any schemas already persisted in the database
    storageService.loadAll();
    Log.infof(
        "Phase 1 complete: %d content types, %d components loaded from database",
        storageService.getAllContentTypes().size(), storageService.getAllComponents().size());

    // Phase 2: load and register schemas from config files
    try {
      SchemaLoadResult result =
          configLoader.loadFromClasspath(Thread.currentThread().getContextClassLoader(), "schemas");

      int registered = 0;
      for (ContentTypeDefinition ct : result.contentTypes()) {
        try {
          storageService.registerContentType(ct, "Loaded from config file", "system");
          registered++;
        } catch (Exception e) {
          Log.errorf(
              "Failed to register content type %s from config: %s", ct.getUid(), e.getMessage());
        }
      }
      for (ComponentDefinition comp : result.components()) {
        try {
          storageService.registerComponent(comp, "Loaded from config file", "system");
          registered++;
        } catch (Exception e) {
          Log.errorf(
              "Failed to register component %s from config: %s", comp.getUid(), e.getMessage());
        }
      }

      Log.infof(
          "Phase 2 complete: %d schemas loaded from config files (%d CTs, %d components)",
          registered, result.contentTypes().size(), result.components().size());
    } catch (Exception e) {
      Log.warnf("Could not load schemas from classpath config files: %s", e.getMessage());
    }

    // Phase 3: scan for and register schemas from @ContentType / @Component annotations
    try {
      int annotationCount = annotationScanner.scanAndRegister();
      Log.infof("Phase 3 complete: %d schemas registered from annotations", annotationCount);
    } catch (Exception e) {
      Log.warnf("Could not scan for annotated schemas: %s", e.getMessage());
    }

    int totalCT = storageService.getAllContentTypes().size();
    int totalComp = storageService.getAllComponents().size();
    Log.infof(
        "Schema system initialized: %d content types, %d components total", totalCT, totalComp);
  }
}
