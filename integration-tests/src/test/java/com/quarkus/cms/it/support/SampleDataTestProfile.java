package com.quarkus.cms.it.support;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * QuarkusTestProfile that enables the sample content seeder for tests
 * that need seeded data (e.g., {@code SampleDataVerificationIT}).
 *
 * <p>Usage: {@code @TestProfile(SampleDataTestProfile.class)}
 */
public class SampleDataTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("cms.sample-content.enabled", "true");
    }

    @Override
    public String getConfigProfile() {
        return "sample-data";
    }
}
