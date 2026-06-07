package com.quarkus.cms.media.storage;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier for {@link StorageProvider} implementations.
 *
 * <p>Annotate each provider with {@code @StorageProviderType("key")} to register it
 * as a named CDI bean. The {@link StorageProviderProducer} uses {@code @Any} to
 * discover all providers and exposes the configured one as {@code @Default}.
 *
 * <p>Because this is a non-{@code @Named} qualifier, it suppresses the implicit
 * {@code @Default} qualifier on provider beans, eliminating CDI ambiguity.
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface StorageProviderType {

  /** The provider key (e.g., "local", "s3", "r2"). */
  String value();
}
