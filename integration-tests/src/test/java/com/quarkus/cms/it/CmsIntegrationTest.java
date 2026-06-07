package com.quarkus.cms.it;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Integration test for the Quarkus Headless CMS extension.
 * <p>
 * Validates that the extension boots correctly and exposes expected endpoints.
 * Each feature module will add its own test classes to this module.
 */
@QuarkusTest
class CmsIntegrationTest {

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .body("status", is("ok"))
            .body("cms", is("quarkus-headless-cms"))
            .body("version", notNullValue());
    }
}
