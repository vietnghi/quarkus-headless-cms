package com.quarkus.cms.media.validation;

import com.quarkus.cms.media.config.MediaConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates file uploads against configured limits (file size, MIME type allowlist).
 */
@ApplicationScoped
public class UploadValidator {

  @Inject MediaConfig config;

  /** Lazily-parsed set of allowed MIME types. */
  private volatile Set<String> allowedTypes;

  /**
   * Validates a file upload.
   *
   * @param fileName the original file name
   * @param mimeType the detected MIME type
   * @param fileSize the file size in bytes
   * @throws ValidationException if validation fails
   */
  public void validate(String fileName, String mimeType, long fileSize) {
    if (fileName == null || fileName.isBlank()) {
      throw new ValidationException("File name is required");
    }
    if (mimeType == null || mimeType.isBlank()) {
      throw new ValidationException("MIME type is required");
    }

    // Check MIME type
    if (!isAllowedType(mimeType)) {
      throw new ValidationException(
          "File type not allowed: " + mimeType + ". Allowed types: " + String.join(", ",
              getAllowedTypes()));
    }

    // Check file size
    long maxBytes = parseSize(config.maxUploadSize());
    if (fileSize > maxBytes) {
      throw new ValidationException(
          "File too large: " + fileSize + " bytes (max: " + maxBytes + " bytes, "
              + formatSize(maxBytes) + ")");
    }
  }

  /** Checks if a MIME type is in the allowed list. */
  public boolean isAllowedType(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    Set<String> types = getAllowedTypes();
    // Allow exact matches and wildcard matches (e.g., "image/*" matches "image/jpeg")
    for (String allowed : types) {
      if (allowed.equals(mimeType)) {
        return true;
      }
      if (allowed.endsWith("/*")) {
        String prefix = allowed.substring(0, allowed.length() - 1);
        if (mimeType.startsWith(prefix)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the maximum allowed file size in bytes. */
  public long getMaxUploadSizeBytes() {
    return parseSize(config.maxUploadSize());
  }

  /** Returns the parsed set of allowed MIME types. */
  public Set<String> getAllowedTypes() {
    if (allowedTypes == null) {
      synchronized (this) {
        if (allowedTypes == null) {
          String raw = config.allowedTypes();
          allowedTypes = new HashSet<>(Arrays.asList(raw.split(",")));
        }
      }
    }
    return allowedTypes;
  }

  /**
   * Parses a human-readable size string like "10M" or "1G" into bytes.
   */
  public static long parseSize(String raw) {
    if (raw == null || raw.isBlank()) {
      return 10 * 1024 * 1024; // default 10 MB
    }
    raw = raw.trim().toUpperCase();
    long multiplier = 1;
    if (raw.endsWith("K")) {
      multiplier = 1024L;
      raw = raw.substring(0, raw.length() - 1);
    } else if (raw.endsWith("M")) {
      multiplier = 1024L * 1024;
      raw = raw.substring(0, raw.length() - 1);
    } else if (raw.endsWith("G")) {
      multiplier = 1024L * 1024 * 1024;
      raw = raw.substring(0, raw.length() - 1);
    } else if (raw.endsWith("B")) {
      raw = raw.substring(0, raw.length() - 1);
    }
    try {
      return Long.parseLong(raw.trim()) * multiplier;
    } catch (NumberFormatException e) {
      return 10 * 1024 * 1024;
    }
  }

  /** Formats a byte count into a human-readable string. */
  public static String formatSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024 * 1024) {
      return String.format("%.1f KB", bytes / 1024.0);
    }
    if (bytes < 1024L * 1024 * 1024) {
      return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
    return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
  }

  /** Validation exception for upload failures. */
  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }
}
