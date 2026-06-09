package com.quarkus.cms.it.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * QuarkusTestResource that starts a PostgreSQL Testcontainer and provides
 * datasource configuration properties for seed-data verification tests.
 *
 * <p>Usage: {@code @QuarkusTestResource(SampleDataResource.class)} or registered
 * via {@code SampleDataTestProfile.testResources()}.
 *
 * <p>This resource is identical to {@link PostgresTestResource} but exists
 * as a dedicated resource for {@code SampleDataVerificationIT} so that
 * seed-data tests can manage their own container lifecycle independently.
 */
public class SampleDataResource implements QuarkusTestResourceLifecycleManager {

  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("cms_test_seed")
      .withUsername("cms")
      .withPassword("cms");

  @Override
  public Map<String, String> start() {
    postgres.start();

    return Map.of(
        "quarkus.datasource.db-kind", "postgresql",
        "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
        "quarkus.datasource.username", postgres.getUsername(),
        "quarkus.datasource.password", postgres.getPassword(),
        "quarkus.hibernate-orm.database.generation", "drop-and-create",
        "quarkus.hibernate-orm.log.sql", "false",
        "quarkus.flyway.migrate-at-start", "false",
        "cms.schema.init.enabled", "true"
    );
  }

  @Override
  public void stop() {
    if (postgres != null) {
      postgres.stop();
    }
  }
}
