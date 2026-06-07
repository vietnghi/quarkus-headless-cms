package com.quarkus.cms.runtime;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.schema.storage.SchemaValidationException;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runtime service for managing content-type and component schemas.
 *
 * <p>Delegates to the {@link SchemaStorageService} from cms-core, providing a simplified
 * JSON-string-based API for the runtime and REST layers.
 */
@ApplicationScoped
public class CmsSchemaService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CmsConfig config;
  private final SchemaStorageService storageService;

  @Inject
  public CmsSchemaService(CmsConfig config, SchemaStorageService storageService) {
    this.config = config;
    this.storageService = storageService;
  }

  /**
   * Registers a new content-type or component schema from a JSON definition string.
   *
   * @param name the UID of the content type or component
   * @param definition the JSON schema definition
   * @return the generated/registered UID
   */
  public String registerContentType(String name, String definition) {
    try {
      ContentTypeDefinition ct = MAPPER.readValue(definition, ContentTypeDefinition.class);
      storageService.registerContentType(ct, "Registered via runtime API", "system");
      Log.infof("Registered content type: %s", ct.getUid());
      return ct.getUid();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid content type JSON: " + e.getMessage(), e);
    } catch (SchemaValidationException e) {
      throw new IllegalArgumentException("Schema validation failed: " + e.getMessage(), e);
    }
  }

  /** Registers a new component schema from a JSON definition string. */
  public String registerComponent(String name, String definition) {
    try {
      ComponentDefinition comp = MAPPER.readValue(definition, ComponentDefinition.class);
      storageService.registerComponent(comp, "Registered via runtime API", "system");
      Log.infof("Registered component: %s", comp.getUid());
      return comp.getUid();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid component JSON: " + e.getMessage(), e);
    } catch (SchemaValidationException e) {
      throw new IllegalArgumentException("Schema validation failed: " + e.getMessage(), e);
    }
  }

  /**
   * Creates a schema — auto-detects whether it's a content type or component based on the JSON
   * structure.
   *
   * @param name the UID (used as fallback if not in JSON)
   * @param definition the JSON schema definition
   * @return the generated/registered UID
   */
  public String createSchema(String name, String definition) {
    try {
      // Try content type first
      try {
        return registerContentType(name, definition);
      } catch (Exception ctEx) {
        // Try component
        try {
          return registerComponent(name, definition);
        } catch (Exception compEx) {
          throw ctEx; // rethrow original
        }
      }
    } catch (IllegalArgumentException e) {
      throw new UnsupportedOperationException(
          "Schema creation failed for '" + name + "': " + e.getMessage(), e);
    }
  }

  /**
   * Retrieves a content-type schema by UID.
   *
   * @param name the content-type UID
   * @return schema definition as JSON string, or empty if not found
   */
  public Optional<String> getSchema(String name) {
    ContentTypeDefinition ct = storageService.getContentType(name);
    if (ct != null) {
      try {
        return Optional.of(MAPPER.writeValueAsString(ct));
      } catch (JsonProcessingException e) {
        Log.errorf("Failed to serialize content type %s: %s", name, e.getMessage());
        return Optional.empty();
      }
    }

    ComponentDefinition comp = storageService.getComponent(name);
    if (comp != null) {
      try {
        return Optional.of(MAPPER.writeValueAsString(comp));
      } catch (JsonProcessingException e) {
        Log.errorf("Failed to serialize component %s: %s", name, e.getMessage());
        return Optional.empty();
      }
    }

    return Optional.empty();
  }

  /** Retrieves a content-type definition object. */
  public ContentTypeDefinition getContentType(String uid) {
    return storageService.getContentType(uid);
  }

  /** Retrieves a component definition object. */
  public ComponentDefinition getComponent(String uid) {
    return storageService.getComponent(uid);
  }

  /**
   * Deletes a content-type or component schema by UID.
   *
   * @param name the UID to delete
   * @return true if deleted, false if not found
   */
  public boolean deleteSchema(String name) {
    ContentTypeDefinition ct = storageService.getContentType(name);
    if (ct != null) {
      storageService.deleteContentType(name);
      Log.infof("Deleted content type: %s", name);
      return true;
    }

    ComponentDefinition comp = storageService.getComponent(name);
    if (comp != null) {
      storageService.deleteComponent(name);
      Log.infof("Deleted component: %s", name);
      return true;
    }

    return false;
  }

  /**
   * Lists all registered content-type and component schemas.
   *
   * @return map of schema UID to JSON definition
   */
  public Map<String, String> listSchemas() {
    Map<String, String> result = new LinkedHashMap<>();

    for (ContentTypeDefinition ct : storageService.getAllContentTypes()) {
      try {
        result.put(ct.getUid(), MAPPER.writeValueAsString(ct));
      } catch (JsonProcessingException e) {
        Log.errorf("Failed to serialize content type %s: %s", ct.getUid(), e.getMessage());
      }
    }

    for (ComponentDefinition comp : storageService.getAllComponents()) {
      try {
        result.put(comp.getUid(), MAPPER.writeValueAsString(comp));
      } catch (JsonProcessingException e) {
        Log.errorf("Failed to serialize component %s: %s", comp.getUid(), e.getMessage());
      }
    }

    return result;
  }

  public CmsConfig getConfig() {
    return config;
  }
}
