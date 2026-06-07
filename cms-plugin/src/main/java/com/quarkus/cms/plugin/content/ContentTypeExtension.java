package com.quarkus.cms.plugin.content;

import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;

import java.util.List;
import java.util.Map;

/**
 * SPI interface for plugins that extend content types.
 *
 * <p>A content type extension can either add additional fields to an existing content type
 * (identified by its UID) or register an entirely new content type. This mirrors Strapi's
 * content-type extension mechanism where plugins can inject fields into any registered schema.
 *
 * <p>Example: an SEO plugin adds {@code metaTitle} and {@code metaDescription} fields to all
 * collection types.
 */
public interface ContentTypeExtension {

  /**
   * Returns the UID of the content type to extend. Use {@code "*"} to apply to all content types,
   * or a specific UID like {@code "api::article.article"}.
   */
  String getTargetContentType();

  /**
   * Returns additional field definitions to add to the target content type. These are merged with
   * the existing fields during schema registration.
   */
  List<FieldDefinition> getAdditionalFields();

  /**
   * Returns additional plugin options to merge into the content type's {@link
   * ContentTypeDefinition#getPluginOptions()} map. Keys are plugin names, values are
   * plugin-specific option maps.
   */
  default Map<String, Object> getPluginOptions() {
    return Map.of();
  }

  /**
   * Whether this extension should create a new content type instead of extending an existing one.
   * When true, {@link #getTargetContentType()} defines the new content type UID, and {@link
   * #getContentTypeDefinition()} is used instead of {@link #getAdditionalFields()}.
   */
  default boolean isNewContentType() {
    return false;
  }

  /**
   * When {@link #isNewContentType()} is true, returns the full content type definition to register.
   * The UID must match {@link #getTargetContentType()}.
   */
  default ContentTypeDefinition getContentTypeDefinition() {
    return null;
  }
}
