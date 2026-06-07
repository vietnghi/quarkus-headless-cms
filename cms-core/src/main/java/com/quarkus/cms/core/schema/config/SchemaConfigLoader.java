package com.quarkus.cms.core.schema.config;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Loads content-type and component schema definitions from configuration files on the classpath or
 * filesystem.
 *
 * <p>Supported formats:
 *
 * <ul>
 *   <li>JSON files under {@code schemas/} on the classpath
 *   <li>YAML files under {@code schemas/} on the classpath
 *   <li>A filesystem directory specified by {@code cms.schema.dir} config property
 * </ul>
 *
 * <p>Each file defines one or more content types/components. The file naming convention follows
 * Strapi: content types are named {@code <pluralName>.json} and components are named {@code
 * <category>/<name>.json}.
 */
@ApplicationScoped
public class SchemaConfigLoader {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  /**
   * Loads schema definition files from the classpath directory {@code schemas/} and returns the
   * parsed content-type and component definitions.
   *
   * @param classLoader the classloader to scan (usually the TCCL)
   * @return a pair of lists: [0] = content types, [1] = components
   */
  @SuppressWarnings("unchecked")
  public SchemaLoadResult loadFromClasspath(ClassLoader classLoader, String resourceDir) {
    List<ContentTypeDefinition> contentTypes = new ArrayList<>();
    List<ComponentDefinition> components = new ArrayList<>();

    try {
      // Use classpath scanning via resources listing
      var resources = classLoader.getResources(resourceDir);
      while (resources.hasMoreElements()) {
        var url = resources.nextElement();
        Path dirPath = Path.of(url.toURI());
        if (Files.isDirectory(dirPath)) {
          loadFromDirectory(dirPath, contentTypes, components, "classpath:" + resourceDir);
        }
      }
    } catch (Exception e) {
      Log.warnf("Could not scan classpath directory '%s': %s", resourceDir, e.getMessage());
    }

    return new SchemaLoadResult(contentTypes, components);
  }

  /** Loads schema definition files from a filesystem directory. */
  public SchemaLoadResult loadFromFilesystem(Path dir) throws IOException {
    List<ContentTypeDefinition> contentTypes = new ArrayList<>();
    List<ComponentDefinition> components = new ArrayList<>();

    if (!Files.isDirectory(dir)) {
      Log.warnf("Schema directory does not exist: %s", dir);
      return new SchemaLoadResult(contentTypes, components);
    }

    loadFromDirectory(dir, contentTypes, components, dir.toString());
    return new SchemaLoadResult(contentTypes, components);
  }

  /** Loads schema definitions from a single JSON or YAML string. */
  public SchemaLoadResult parseJson(String json) throws IOException {
    List<ContentTypeDefinition> contentTypes = new ArrayList<>();
    List<ComponentDefinition> components = new ArrayList<>();

    // Try single content type
    try {
      ContentTypeDefinition ct = JSON_MAPPER.readValue(json, ContentTypeDefinition.class);
      if (ct.getUid() != null) {
        contentTypes.add(ct);
        return new SchemaLoadResult(contentTypes, components);
      }
    } catch (Exception ignored) {
      // not a single CT, try components
    }

    // Try single component
    try {
      ComponentDefinition comp = JSON_MAPPER.readValue(json, ComponentDefinition.class);
      if (comp.getUid() != null) {
        components.add(comp);
        return new SchemaLoadResult(contentTypes, components);
      }
    } catch (Exception ignored) {
      // not a single component
    }

    // Try a map with "contentTypes" and "components" keys (batch format)
    // Use JsonNode instead of TypeReference to avoid anonymous inner class
    // which can cause VerifyError with some Jackson versions in Quarkus.
    try {
      JsonNode root = JSON_MAPPER.readTree(json);
      if (root.has("contentTypes")) {
        JsonNode ctArray = root.get("contentTypes");
        if (ctArray.isArray()) {
          for (JsonNode ctNode : ctArray) {
            contentTypes.add(JSON_MAPPER.treeToValue(ctNode, ContentTypeDefinition.class));
          }
        }
      }
      if (root.has("components")) {
        JsonNode compArray = root.get("components");
        if (compArray.isArray()) {
          for (JsonNode compNode : compArray) {
            components.add(JSON_MAPPER.treeToValue(compNode, ComponentDefinition.class));
          }
        }
      }
    } catch (Exception ignored) {
      // not batch format either
    }

    return new SchemaLoadResult(contentTypes, components);
  }

  private void loadFromDirectory(
      Path dir, List<ContentTypeDefinition> cts, List<ComponentDefinition> comps, String source)
      throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        String filename = entry.getFileName().toString();
        if (Files.isDirectory(entry)) {
          // Recurse into subdirectories (component categories)
          loadFromDirectory(entry, cts, comps, source);
          continue;
        }
        if (!filename.endsWith(".json")
            && !filename.endsWith(".yaml")
            && !filename.endsWith(".yml")) {
          continue;
        }
        try {
          String content = Files.readString(entry);
          SchemaLoadResult result =
              filename.endsWith(".json") ? parseJson(content) : parseYaml(content);
          cts.addAll(result.contentTypes());
          comps.addAll(result.components());
          Log.debugf(
              "Loaded schema file: %s/%s (%d CTs, %d components)",
              source, filename, result.contentTypes().size(), result.components().size());
        } catch (Exception e) {
          Log.errorf("Failed to parse schema file %s/%s: %s", source, filename, e.getMessage());
        }
      }
    }
  }

  private SchemaLoadResult parseYaml(String yaml) throws IOException {
    // YAML files can be parsed similarly to JSON but through the YAML mapper
    List<ContentTypeDefinition> contentTypes = new ArrayList<>();
    List<ComponentDefinition> components = new ArrayList<>();

    try {
      var node = YAML_MAPPER.readTree(yaml);
      if (node.isArray()) {
        for (var item : node) {
          ContentTypeDefinition ct = YAML_MAPPER.treeToValue(item, ContentTypeDefinition.class);
          contentTypes.add(ct);
        }
      } else {
        ContentTypeDefinition ct = YAML_MAPPER.treeToValue(node, ContentTypeDefinition.class);
        contentTypes.add(ct);
      }
    } catch (Exception e) {
      // Try component array
      try {
        var node = YAML_MAPPER.readTree(yaml);
        if (node.isArray()) {
          for (var item : node) {
            ComponentDefinition comp = YAML_MAPPER.treeToValue(item, ComponentDefinition.class);
            components.add(comp);
          }
        } else {
          ComponentDefinition comp = YAML_MAPPER.treeToValue(node, ComponentDefinition.class);
          components.add(comp);
        }
      } catch (Exception e2) {
        throw new IOException(
            "Could not parse YAML as content type or component: " + e2.getMessage(), e2);
      }
    }

    return new SchemaLoadResult(contentTypes, components);
  }

  /** Result container for schema file loading. */
  public record SchemaLoadResult(
      List<ContentTypeDefinition> contentTypes, List<ComponentDefinition> components) {
    public int totalCount() {
      return contentTypes.size() + components.size();
    }
  }
}
