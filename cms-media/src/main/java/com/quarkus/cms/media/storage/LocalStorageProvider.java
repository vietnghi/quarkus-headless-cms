package com.quarkus.cms.media.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Local filesystem storage provider.
 *
 * <p>Stores uploaded files on the local filesystem under the configured upload directory.
 * File names are UUID-based to prevent collisions; the original file name is preserved in the
 * database record.
 */
@StorageProviderType("local")
@ApplicationScoped
@RegisterForReflection
public class LocalStorageProvider implements StorageProvider {

  private final Path uploadDir;

  public LocalStorageProvider() {
    this("uploads");
  }

  public LocalStorageProvider(String uploadDirectory) {
    this.uploadDir = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    try {
      Files.createDirectories(uploadDir);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create upload directory: " + uploadDir, e);
    }
  }

  @Override
  public String providerKey() {
    return "local";
  }

  @Override
  public StoreResult store(InputStream inputStream, String fileName, String mimeType,
      Map<String, String> metadata) {
    String ext = extractExtension(fileName);
    String storageName = UUID.randomUUID() + ext;
    Path filePath = resolvePath(storageName, metadata);

    try {
      Files.createDirectories(filePath.getParent());
      long size = Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
      String url = "/uploads/" + relativePath(filePath);
      Log.debugf("Stored file locally: %s (%d bytes)", filePath, size);
      return new StoreResult(url, relativePath(filePath), size);
    } catch (IOException e) {
      throw new RuntimeException("Failed to store file: " + fileName, e);
    }
  }

  @Override
  public InputStream retrieve(String storageKey) {
    Path filePath = uploadDir.resolve(storageKey).normalize();
    if (!filePath.startsWith(uploadDir)) {
      throw new SecurityException("Path traversal attempt: " + storageKey);
    }
    try {
      return Files.newInputStream(filePath);
    } catch (IOException e) {
      Log.warnf("File not found: %s", storageKey);
      return null;
    }
  }

  @Override
  public boolean delete(String storageKey) {
    Path filePath = uploadDir.resolve(storageKey).normalize();
    if (!filePath.startsWith(uploadDir)) {
      throw new SecurityException("Path traversal attempt: " + storageKey);
    }
    try {
      return Files.deleteIfExists(filePath);
    } catch (IOException e) {
      Log.warnf("Failed to delete file: %s - %s", storageKey, e.getMessage());
      return false;
    }
  }

  @Override
  public boolean exists(String storageKey) {
    Path filePath = uploadDir.resolve(storageKey).normalize();
    return Files.exists(filePath);
  }

  @Override
  public String getPublicUrl(String storageKey) {
    return "/uploads/" + storageKey;
  }

  private Path resolvePath(String storageName, Map<String, String> metadata) {
    String folderPath = metadata != null ? metadata.get("folderPath") : null;
    if (folderPath != null && !folderPath.isBlank()) {
      // Strip leading slash and append storage name
      String clean = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
      return uploadDir.resolve(clean).resolve(storageName).normalize();
    }
    return uploadDir.resolve(storageName).normalize();
  }

  private String relativePath(Path filePath) {
    Path relative = uploadDir.relativize(filePath);
    return relative.toString().replace('\\', '/');
  }

  private String extractExtension(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return "";
    }
    return fileName.substring(dot);
  }
}
