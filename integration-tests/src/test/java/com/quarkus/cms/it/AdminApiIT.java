package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for Admin API endpoints.
 *
 * <p>Covers dashboard statistics, system configuration, content type builder,
 * and content manager endpoints.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Admin API")
class AdminApiIT {

  // ========================================================================
  // Dashboard Statistics
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should return dashboard stats")
  void dashboardStats() {
    given()
        .when()
        .get("/admin/dashboard-stats")
        .then()
        .statusCode(200)
        .body("contentTypes", notNullValue())
        .body("totalEntries", notNullValue())
        .body("publishedEntries", notNullValue());
  }

  // ========================================================================
  // System Configuration
  // ========================================================================

  @Test
  @Order(2)
  @DisplayName("should get system config")
  void getConfig() {
    given()
        .when()
        .get("/admin/config")
        .then()
        .statusCode(200)
        .body("data.general.siteName", is("Quarkus CMS"));
  }

  @Test
  @Order(3)
  @DisplayName("should update system config")
  void updateConfig() {
    Map<String, Object> update = Map.of(
        "siteName", "Updated CMS Site",
        "defaultLocale", "fr");

    given()
        .contentType("application/json")
        .body(update)
        .when()
        .put("/admin/config/general")
        .then()
        .statusCode(200)
        .body("data.siteName", is("Updated CMS Site"));
  }

  // ========================================================================
  // Content Type Builder
  // ========================================================================

  @Test
  @Order(4)
  @DisplayName("should list content types via admin")
  void listContentTypes() {
    given()
        .when()
        .get("/admin/content-type-builder/content-types")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(5)
  @DisplayName("should get components via admin")
  void listComponents() {
    given()
        .when()
        .get("/admin/content-type-builder/components")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  // ========================================================================
  // Content Manager
  // ========================================================================

  @Test
  @Order(6)
  @DisplayName("should list articles via content manager")
  void contentManagerList() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/admin/content-manager/collection-types/api::article.article")
        .then()
        .statusCode(200);
  }

  @Test
  @Order(7)
  @DisplayName("should return single type via content manager")
  void contentManagerSingleType() {
    given()
        .when()
        .get("/admin/content-manager/single-types/api::global.global")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // Admin Media
  // ========================================================================

  @Test
  @Order(8)
  @DisplayName("should browse media via admin")
  void adminMediaBrowse() {
    given()
        .when()
        .get("/admin/upload/files")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // I18n Admin
  // ========================================================================

  @Test
  @Order(9)
  @DisplayName("should list locales via admin i18n")
  void adminI18nLocales() {
    given()
        .when()
        .get("/admin/i18n/locales")
        .then()
        .statusCode(200);
  }

  @Test
  @Order(10)
  @DisplayName("should get content type locale info")
  void adminI18nContentTypes() {
    given()
        .when()
        .get("/admin/i18n/content-types")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // Health Endpoint (unauthenticated)
  // ========================================================================

  @Test
  @Order(11)
  @DisplayName("health endpoint should be accessible")
  void healthEndpoint() {
    given()
        .when()
        .get("/api/health")
        .then()
        .statusCode(200)
        .body("status", is("ok"))
        .body("cms", is("quarkus-headless-cms"))
        .body("version", notNullValue());
  }
}
