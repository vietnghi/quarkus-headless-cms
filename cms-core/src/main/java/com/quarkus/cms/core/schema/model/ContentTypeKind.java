package com.quarkus.cms.core.schema.model;

/**
 * Classifies a content type as either a collection type or a single type.
 *
 * <ul>
 *   <li><b>COLLECTION_TYPE</b> — multiple entries, like "Article" or "Product".
 *   <li><b>SINGLE_TYPE</b> — a single instance, like "Homepage" or "SiteConfig".
 * </ul>
 */
public enum ContentTypeKind {
  COLLECTION_TYPE,
  SINGLE_TYPE
}
