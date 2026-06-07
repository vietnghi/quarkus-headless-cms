package com.quarkus.cms.media.storage;

import java.io.InputStream;
import java.util.Map;

/**
 * Abstraction for file storage backends.
 *
 * <p>Implementations handle the actual persistence of uploaded file bytes. Built-in providers
 * include {@code local} (filesystem), {@code s3} (AWS S3), and {@code r2} (Cloudflare R2).
 *
 * <p>Custom providers can be registered by implementing this interface and annotating the class
 * with {@code @Named("provider-key")} — they are discovered automatically via CDI.
 */
public interface StorageProvider {

  /**
   * Returns the unique key identifying this provider (e.g., "local", "s3", "r2").
   */
  String providerKey();

  /**
   * Stores file content and returns a {@link StoreResult} containing the public URL and
   * provider-specific storage key.
   *
   * @param inputStream the file content stream
   * @param fileName    the original file name
   * @param mimeType    the MIME type of the file
   * @param metadata    optional metadata (e.g., folder path, content hash)
   * @return result with URL and storage key
   */
  StoreResult store(InputStream inputStream, String fileName, String mimeType,
      Map<String, String> metadata);

  /**
   * Retrieves the file content as an {@link InputStream}.
   *
   * @param storageKey the provider-specific storage key
   * @return input stream of the file content, or {@code null} if not found
   */
  InputStream retrieve(String storageKey);

  /**
   * Deletes a stored file.
   *
   * @param storageKey the provider-specific storage key
   * @return {@code true} if the file was deleted, {@code false} if it didn't exist
   */
  boolean delete(String storageKey);

  /**
   * Checks whether a file exists at the given storage key.
   */
  boolean exists(String storageKey);

  /**
   * Generates a public URL for the given storage key.
   */
  String getPublicUrl(String storageKey);

  /**
   * Result of a store operation.
   */
  record StoreResult(String url, String storageKey, long size) {}
}
