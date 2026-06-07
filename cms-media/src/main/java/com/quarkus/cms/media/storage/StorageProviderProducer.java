package com.quarkus.cms.media.storage;

import com.quarkus.cms.media.config.MediaConfig;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer that selects the active {@link StorageProvider} based on the
 * {@code quarkus.cms.media.storage-provider} configuration property.
 *
 * <p>Each provider carries a {@code @StorageProviderType("key")} qualifier
 * that suppresses the implicit {@code @Default}. This producer resolves the
 * configured key and exposes the matching bean as {@code @Default}, giving
 * injection points a single unambiguous target.
 *
 * <p>To add a custom provider, implement {@link StorageProvider}, annotate
 * it with {@code @StorageProviderType("your-key") @ApplicationScoped}, and
 * extend this producer to include it.
 */
@ApplicationScoped
public class StorageProviderProducer {

  @Inject MediaConfig config;

  @Inject @StorageProviderType("local") StorageProvider localProvider;

  @Inject @StorageProviderType("s3") StorageProvider s3Provider;

  @Inject @StorageProviderType("r2") StorageProvider r2Provider;

  @Produces
  @Default
  @ApplicationScoped
  public StorageProvider produceStorageProvider() {
    String key = config.storageProvider();
    StorageProvider provider = switch (key) {
      case "s3" -> s3Provider;
      case "r2" -> r2Provider;
      default -> localProvider;
    };
    Log.debugf("Selected storage provider: %s", provider.providerKey());
    return provider;
  }
}
