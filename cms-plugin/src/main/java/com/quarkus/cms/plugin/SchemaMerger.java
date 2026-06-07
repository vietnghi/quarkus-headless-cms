package com.quarkus.cms.plugin;

import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.plugin.content.ContentTypeExtension;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.*;

/**
 * Merges plugin-contributed content type extensions into existing content type definitions.
 *
 * <p>After plugins have been loaded and registered, this service iterates over all {@link
 * ContentTypeExtension}s contributed by plugins and applies them to the corresponding content types
 * in the schema storage.
 *
 * <p>Extensions with target {@code "*"} are applied to all content types. Extensions targeting a
 * specific UID are applied only to that content type. Extensions with {@code isNewContentType() ==
 * true} register entirely new content types.
 */
@ApplicationScoped
public class SchemaMerger {

  @Inject PluginRegistry pluginRegistry;

  @Inject SchemaStorageService schemaStorage;

  /** Applies all plugin content type extensions after the schema has been initialized. */
  void onSchemaReady(@Observes StartupEvent event) {
    if (!pluginRegistry.isEnabled()) return;

    Log.info("Applying plugin content type extensions...");
    applyAllExtensions();
  }

  /** Applies all registered content type extensions. */
  public synchronized void applyAllExtensions() {
    List<ContentTypeExtension> extensions = pluginRegistry.getAllContentTypeExtensions();
    if (extensions.isEmpty()) {
      Log.debug("No plugin content type extensions to apply");
      return;
    }

    // Separate new content types from field extensions
    List<ContentTypeExtension> newTypes =
        extensions.stream().filter(ContentTypeExtension::isNewContentType).toList();

    List<ContentTypeExtension> fieldExtensions =
        extensions.stream().filter(e -> !e.isNewContentType()).toList();

    // Register new content types
    for (ContentTypeExtension ext : newTypes) {
      registerNewContentType(ext);
    }

    // Apply field extensions
    List<ContentTypeDefinition> allTypes = schemaStorage.getAllContentTypes();

    // Split into universal ("*") and targeted extensions
    List<ContentTypeExtension> universal =
        fieldExtensions.stream().filter(e -> "*".equals(e.getTargetContentType())).toList();

    List<ContentTypeExtension> targeted =
        fieldExtensions.stream().filter(e -> !"*".equals(e.getTargetContentType())).toList();

    // Apply universal extensions to all content types
    for (ContentTypeDefinition ct : allTypes) {
      List<FieldDefinition> extraFields = new ArrayList<>();
      Map<String, Object> mergedPluginOptions = new HashMap<>(ct.getPluginOptions());

      for (ContentTypeExtension ext : universal) {
        extraFields.addAll(ext.getAdditionalFields());
        mergedPluginOptions.putAll(ext.getPluginOptions());
      }

      if (!extraFields.isEmpty()) {
        applyExtension(ct, extraFields, mergedPluginOptions, "plugin-universal");
      }
    }

    // Apply targeted extensions
    for (ContentTypeExtension ext : targeted) {
      String target = ext.getTargetContentType();
      ContentTypeDefinition ct = schemaStorage.getContentType(target);
      if (ct == null) {
        Log.warnf("Content type '%s' not found for plugin extension, skipping", target);
        continue;
      }

      Map<String, Object> mergedPluginOptions = new HashMap<>(ct.getPluginOptions());
      mergedPluginOptions.putAll(ext.getPluginOptions());
      applyExtension(ct, ext.getAdditionalFields(), mergedPluginOptions, target);
    }

    Log.infof("Applied %d plugin content type extension(s)", extensions.size());
  }

  private void registerNewContentType(ContentTypeExtension ext) {
    ContentTypeDefinition ct = ext.getContentTypeDefinition();
    if (ct == null) {
      Log.warnf(
          "Plugin extension claims new content type but no definition provided: %s",
          ext.getTargetContentType());
      return;
    }
    try {
      schemaStorage.registerContentType(ct, "Registered by plugin", "plugin-system");
      Log.infof("Registered new content type from plugin: %s", ct.getUid());
    } catch (Exception e) {
      Log.errorf("Failed to register plugin content type '%s': %s", ct.getUid(), e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private void applyExtension(
      ContentTypeDefinition ct,
      List<FieldDefinition> extraFields,
      Map<String, Object> pluginOptions,
      String source) {
    try {
      // Merge fields: existing fields take precedence (plugin fields don't overwrite)
      Map<String, FieldDefinition> existingFields = new LinkedHashMap<>();
      for (FieldDefinition f : ct.getFields()) {
        existingFields.put(f.getName(), f);
      }

      List<FieldDefinition> mergedFields = new ArrayList<>(ct.getFields());
      boolean added = false;

      for (FieldDefinition extra : extraFields) {
        if (!existingFields.containsKey(extra.getName())) {
          mergedFields.add(extra);
          added = true;
        }
      }

      if (!added) {
        return; // Nothing changed
      }

      // Rebuild content type with merged fields
      ContentTypeDefinition.Builder builder =
          ContentTypeDefinition.builder(ct.getUid(), ct.getKind())
              .singularName(ct.getSingularName())
              .pluralName(ct.getPluralName())
              .displayName(ct.getDisplayName())
              .description(ct.getDescription())
              .fields(mergedFields)
              .relations(ct.getRelations())
              .components(ct.getComponents())
              .dynamicZones(ct.getDynamicZones())
              .draftAndPublish(ct.isDraftAndPublish())
              .localized(ct.isLocalized())
              .options(ct.getOptions());

      // Deep merge pluginOptions
      Map<String, Object> merged = new HashMap<>(ct.getPluginOptions());
      merged.putAll(pluginOptions);
      builder.pluginOptions(merged);

      ContentTypeDefinition updated = builder.build();

      // Persist the updated schema
      schemaStorage.registerContentType(
          updated, "Plugin extension applied from: " + source, "plugin-system");

      Log.debugf(
          "Applied plugin field extension to content type '%s' from %s: %d field(s)",
          ct.getUid(), source, extraFields.size());

    } catch (Exception e) {
      Log.errorf(
          "Failed to apply plugin extension to content type '%s': %s", ct.getUid(), e.getMessage());
    }
  }
}
