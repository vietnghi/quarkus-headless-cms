package com.quarkus.cms.it.support;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * QuarkusTestProfile that enables the sample content seeder for tests
 * that need seeded data (e.g., {@code SampleDataVerificationIT}).
 *
 * <p>This profile starts a PostgreSQL Testcontainer via {@link SampleDataResource}
 * and enables {@code cms.sample-content.enabled=true} so that the
 * {@code SampleContentSeeder} runs during application startup.
 *
 * <p>Usage: {@code @TestProfile(SampleDataTestProfile.class)}
 */
public class SampleDataTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("cms.sample-content.enabled", "true");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.singletonList(
            new TestResourceEntry(SampleDataResource.class)
        );
    }

    @Override
    public String getConfigProfile() {
        return "sample-data";
    }
}
