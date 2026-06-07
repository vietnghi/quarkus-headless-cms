package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for role-based permission enforcement.
 *
 * <p>When {@code quarkus.cms.auth.api-tokens.enabled=false} (the default test configuration),
 * admin endpoints are open for development convenience. This test verifies the current behavior
 * and also validates the JWT auth flow (register, login, authenticated requests).
 *
 * <p>For tests that need strict permission enforcement, use a {@code @TestProfile} that
 * enables {@code quarkus.cms.auth.api-tokens.enabled=true}.
 */
@QuarkusTest
@DisplayName("Permission Enforcement")
class PermissionEnforcementIT {

  // ========================================================================
  // Admin Endpoints — accessible when auth is disabled (dev mode)
  // ========================================================================

  @Test
  @DisplayName("should allow unauthenticated access to admin users in dev mode")
  void adminUsersAccessible() {
    given()
        .when()
        .get("/admin/users")
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("should allow unauthenticated access to admin roles in dev mode")
  void adminRolesAccessible() {
    given()
        .when()
        .get("/admin/roles")
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("should allow unauthenticated access to admin API tokens in dev mode")
  void adminTokensAccessible() {
    given()
        .when()
        .get("/admin/api-tokens")
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("should allow unauthenticated access to admin config in dev mode")
  void adminConfigAccessible() {
    given()
        .when()
        .get("/admin/config")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // Content API — Authenticated with JWT
  // ========================================================================

  @Test
  @DisplayName("should allow authenticated write operations with JWT")
  void authenticatedCreate() {
    String token = loginAsAdmin();

    Map<String, Object> body = Map.of("title", "Auth Test Article");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Auth Test Article"));
  }

  // ========================================================================
  // Admin Dashboard
  // ========================================================================

  @Test
  @DisplayName("should allow unauthenticated access to admin dashboard in dev mode")
  void adminDashboardAccessible() {
    given()
        .when()
        .get("/admin/dashboard/stats")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // Plugin Admin
  // ========================================================================

  @Test
  @DisplayName("should allow unauthenticated access to plugin admin in dev mode")
  void pluginAdminAccessible() {
    given()
        .when()
        .get("/admin/plugins")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // Webhook Admin
  // ========================================================================

  @Test
  @DisplayName("should allow unauthenticated access to webhook admin in dev mode")
  void webhookAdminAccessible() {
    given()
        .when()
        .get("/admin/webhooks")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private String loginAsAdmin() {
    // Register first
    given()
        .contentType("application/json")
        .body(Map.of(
            "username", "permadmin",
            "email", "permadmin@cms-test.local",
            "password", "AdminTest123!"))
        .when()
        .post("/admin/register")
        .then()
        .statusCode(anyOf(is(200), is(400))); // 400 if already exists

    return given()
        .contentType("application/json")
        .body(Map.of("identifier", "permadmin", "password", "AdminTest123!"))
        .when()
        .post("/admin/login")
        .then()
        .statusCode(200)
        .extract()
        .path("accessToken");
  }
}
