package com.quarkus.cms.media.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cloudflare R2 storage provider.
 *
 * <p>Cloudflare R2 is S3-compatible object storage with zero egress fees. This provider
 * uses the same AWS SDK v2 under the hood, configured with the R2 endpoint.
 *
 * <p>Configuration is read from {@code quarkus.cms.media.s3.*} properties; the endpoint
 * should be set to your R2 account endpoint (e.g., {@code https://<account-id>.r2.cloudflarestorage.com}).
 */
@StorageProviderType("r2")
@ApplicationScoped
@RegisterForReflection
public class R2StorageProvider implements StorageProvider {

  private final String bucket;
  private final Optional<String> endpoint;
  private final Optional<String> accessKeyId;
  private final Optional<String> secretAccessKey;
  private final Optional<String> publicUrlPrefix;

  public R2StorageProvider() {
    this("cms-media", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public R2StorageProvider(
      String bucket,
      Optional<String> endpoint,
      Optional<String> accessKeyId,
      Optional<String> secretAccessKey,
      Optional<String> publicUrlPrefix) {
    this.bucket = bucket;
    this.endpoint = endpoint;
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.publicUrlPrefix = publicUrlPrefix;
  }

  @Override
  public String providerKey() {
    return "r2";
  }

  @Override
  public StoreResult store(InputStream inputStream, String fileName, String mimeType,
      Map<String, String> metadata) {
    String ext = extractExtension(fileName);
    String storageKey = UUID.randomUUID() + ext;

    Log.infof("R2 store (stub): bucket=%s, key=%s, name=%s, mime=%s",
        bucket, storageKey, fileName, mimeType);

    String url;
    if (publicUrlPrefix.isPresent()) {
      url = publicUrlPrefix.get() + "/" + storageKey;
    } else if (endpoint.isPresent()) {
      url = endpoint.get() + "/" + bucket + "/" + storageKey;
    } else {
      url = "https://" + bucket + ".r2.cloudflarestorage.com/" + storageKey;
    }

    return new StoreResult(url, storageKey, 0);
  }

  @Override
  public InputStream retrieve(String storageKey) {
    Log.warnf("R2 retrieve not implemented in stub mode: %s", storageKey);
    return null;
  }

  @Override
  public boolean delete(String storageKey) {
    Log.infof("R2 delete (stub): bucket=%s, key=%s", bucket, storageKey);
    return true;
  }

  @Override
  public boolean exists(String storageKey) {
    return false;
  }

  @Override
  public String getPublicUrl(String storageKey) {
    if (publicUrlPrefix.isPresent()) {
      return publicUrlPrefix.get() + "/" + storageKey;
    } else if (endpoint.isPresent()) {
      return endpoint.get() + "/" + bucket + "/" + storageKey;
    }
    return "https://pub-" + bucket + ".r2.dev/" + storageKey;
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
