package com.quarkus.cms.it.support;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * QuarkusTestProfile that configures the integration test to use a real PostgreSQL
 * database via Testcontainers.
 *
 * <p>Usage: {@code @TestProfile(PostgresTestProfile.class)} on a test class.
 *
 * <p>When using this profile, the test will connect to a PostgreSQL container
 * started by {@link PostgresTestResource} instead of the default H2 in-memory
 * database.
 */
public class PostgresTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.datasource.db-kind", "postgresql",
            "quarkus.hibernate-orm.database.generation", "drop-and-create",
            "quarkus.hibernate-orm.log.sql", "false",
            "quarkus.flyway.migrate-at-start", "false",
            "cms.schema.init.enabled", "true",
            "quarkus.cms.auth.api-tokens.enabled", "false"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.singletonList(
            new TestResourceEntry(PostgresTestResource.class)
        );
    }

    @Override
    public String getConfigProfile() {
        return "postgres";
    }
}
