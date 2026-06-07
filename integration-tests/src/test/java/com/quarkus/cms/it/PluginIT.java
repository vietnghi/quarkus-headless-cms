package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for plugin loading and lifecycle.
 *
 * <p>Verifies that the SEO plugin is loaded and accessible,
 * and the plugin admin API works correctly.
 */
@QuarkusTest
@DisplayName("Plugin System")
class PluginIT {

  // ========================================================================
  // Plugin Admin API
  // ========================================================================

  @Test
  @DisplayName("should list loaded plugins")
  void listPlugins() {
    given()
        .when()
        .get("/admin/plugins")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.size()", greaterThanOrEqualTo(0));
  }

  @Test
  @DisplayName("should have SEO plugin metadata available")
  void seoPluginExists() {
    given()
        .when()
        .get("/admin/plugins")
        .then()
        .statusCode(200)
        .body("data.name", hasItem("seo"));
  }

  // ========================================================================
  // Sample Content Verification (proves plugin loaded at startup)
  // ========================================================================

  @Test
  @DisplayName("sample content should be loaded at startup (proves plugin init ran)")
  void sampleContentLoaded() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/tag")
        .then()
        .statusCode(200)
        .body("data.size()", greaterThanOrEqualTo(10));
  }
}
