package com.quarkus.cms.core.schema.component;

import com.quarkus.cms.core.schema.storage.SchemaCache;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

/**
 * Factory that creates a {@link SchemaStorageService} wired to a given {@link SchemaCache} for
 * unit-test scenarios where CDI is not available.
 */
final class SchemaStorageServiceFactory {

  private SchemaStorageServiceFactory() {}

  /**
   * Creates a fully wired {@link SchemaStorageService} backed by the supplied cache.
   *
   * <p>Because {@link SchemaStorageService#cache} is a package-private {@code @Inject} field,
   * we use reflection to set it from a test in a different package.
   */
  static SchemaStorageService createForTest(SchemaCache cache) {
    SchemaStorageService service = new SchemaStorageService();
    try {
      java.lang.reflect.Field field = SchemaStorageService.class.getDeclaredField("cache");
      field.setAccessible(true);
      field.set(service, cache);
    } catch (Exception e) {
      throw new RuntimeException("Failed to wire SchemaStorageService for test", e);
    }
    return service;
  }
}
