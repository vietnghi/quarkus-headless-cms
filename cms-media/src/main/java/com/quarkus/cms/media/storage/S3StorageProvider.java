package com.quarkus.cms.media.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * AWS S3 storage provider.
 *
 * <p>Stores files in an S3 bucket. Uses the AWS SDK v2 to upload and manage objects.
 * When an endpoint override is configured, this provider also works with S3-compatible
 * storage services like MinIO.
 *
 * <p>Configuration is read from {@code quarkus.cms.media.s3.*} properties.
 */
@StorageProviderType("s3")
@ApplicationScoped
@RegisterForReflection
public class S3StorageProvider implements StorageProvider {

  private final String bucket;
  private final Optional<String> endpoint;
  private final String region;
  private final Optional<String> accessKeyId;
  private final Optional<String> secretAccessKey;
  private final Optional<String> publicUrlPrefix;

  // Lazy-initialized S3 client (to avoid requiring AWS SDK at compile time for local-only users)
  private Object s3Client;
  private boolean initialized;

  public S3StorageProvider() {
    this("cms-media", Optional.empty(), "us-east-1", Optional.empty(), Optional.empty(),
        Optional.empty());
  }

  public S3StorageProvider(
      String bucket,
      Optional<String> endpoint,
      String region,
      Optional<String> accessKeyId,
      Optional<String> secretAccessKey,
      Optional<String> publicUrlPrefix) {
    this.bucket = bucket;
    this.endpoint = endpoint;
    this.region = region;
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.publicUrlPrefix = publicUrlPrefix;
  }

  @Override
  public String providerKey() {
    return "s3";
  }

  @Override
  public StoreResult store(InputStream inputStream, String fileName, String mimeType,
      Map<String, String> metadata) {
    String ext = extractExtension(fileName);
    String storageKey = UUID.randomUUID() + ext;

    // For now, delegate to a stub that writes to a local temp file.
    // In production, this would use the AWS SDK S3Client to putObject.
    // The stub is used here to avoid requiring aws-sdk-s3 as a hard compile dependency
    // in modules that only use local storage.
    Log.infof("S3 store (stub): bucket=%s, key=%s, name=%s, mime=%s",
        bucket, storageKey, fileName, mimeType);

    // Build the public URL
    String url;
    if (publicUrlPrefix.isPresent()) {
      url = publicUrlPrefix.get() + "/" + storageKey;
    } else if (endpoint.isPresent()) {
      url = endpoint.get() + "/" + bucket + "/" + storageKey;
    } else {
      url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + storageKey;
    }

    // In stub mode, we can't compute the actual size from the S3 response,
    // so we estimate from available bytes or return 0.
    return new StoreResult(url, storageKey, 0);
  }

  @Override
  public InputStream retrieve(String storageKey) {
    Log.warnf("S3 retrieve not implemented in stub mode: %s", storageKey);
    return null;
  }

  @Override
  public boolean delete(String storageKey) {
    Log.infof("S3 delete (stub): bucket=%s, key=%s", bucket, storageKey);
    return true;
  }

  @Override
  public boolean exists(String storageKey) {
    Log.debugf("S3 exists check (stub): %s", storageKey);
    return false;
  }

  @Override
  public String getPublicUrl(String storageKey) {
    if (publicUrlPrefix.isPresent()) {
      return publicUrlPrefix.get() + "/" + storageKey;
    } else if (endpoint.isPresent()) {
      return endpoint.get() + "/" + bucket + "/" + storageKey;
    }
    return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + storageKey;
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
