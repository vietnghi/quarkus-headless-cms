package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for user authentication flows.
 *
 * <p>Covers registration, login, JWT token issuance, token refresh,
 * password reset initiation, and error scenarios.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auth Flow")
class AuthFlowIT {

  private static final String TEST_USERNAME = "ituser-" + UUID.randomUUID().toString().substring(0, 8);
  private static final String TEST_EMAIL = TEST_USERNAME + "@cms-test.local";
  private static final String TEST_PASSWORD = "IntegrationTest123!";

  // ========================================================================
  // Registration
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should register a new user")
  void registerUser() {
    Map<String, Object> body = Map.of(
        "username", TEST_USERNAME,
        "email", TEST_EMAIL,
        "password", TEST_PASSWORD,
        "firstName", "Integration",
        "lastName", "Tester");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/register")
        .then()
        .statusCode(200)
        .body("accessToken", notNullValue())
        .body("refreshToken", notNullValue())
        .body("user.id", notNullValue())
        .body("user.username", is(TEST_USERNAME))
        .body("user.email", is(TEST_EMAIL))
        .body("user.firstName", is("Integration"))
        .body("user.lastName", is("Tester"));
  }

  @Test
  @Order(2)
  @DisplayName("should reject duplicate username registration")
  void registerDuplicateUsername() {
    Map<String, Object> body = Map.of(
        "username", TEST_USERNAME,
        "email", "other-" + TEST_EMAIL,
        "password", TEST_PASSWORD);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/register")
        .then()
        .statusCode(400)
        .body("error.message", containsString("already exists"));
  }

  @Test
  @Order(3)
  @DisplayName("should reject registration with short password")
  void registerShortPassword() {
    Map<String, Object> body = Map.of(
        "username", "shortpw-user",
        "email", "short@cms-test.local",
        "password", "12345");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/register")
        .then()
        .statusCode(400);
  }

  @Test
  @Order(4)
  @DisplayName("should reject registration with missing fields")
  void registerMissingFields() {
    given()
        .contentType("application/json")
        .body(Map.of("username", "nobody"))
        .when()
        .post("/admin/register")
        .then()
        .statusCode(400);
  }

  // ========================================================================
  // Login
  // ========================================================================

  @Test
  @Order(5)
  @DisplayName("should login with username and password")
  void loginWithUsername() {
    Map<String, Object> body = Map.of(
        "identifier", TEST_USERNAME,
        "password", TEST_PASSWORD);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/login")
        .then()
        .statusCode(200)
        .body("accessToken", notNullValue())
        .body("refreshToken", notNullValue())
        .body("user.username", is(TEST_USERNAME));
  }

  @Test
  @Order(6)
  @DisplayName("should login with email and password")
  void loginWithEmail() {
    Map<String, Object> body = Map.of(
        "identifier", TEST_EMAIL,
        "password", TEST_PASSWORD);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/login")
        .then()
        .statusCode(200)
        .body("accessToken", notNullValue())
        .body("user.email", is(TEST_EMAIL));
  }

  @Test
  @Order(7)
  @DisplayName("should reject login with wrong password")
  void loginWrongPassword() {
    Map<String, Object> body = Map.of(
        "identifier", TEST_USERNAME,
        "password", "WrongPassword123!");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/login")
        .then()
        .statusCode(401)
        .body("error.name", is("UnauthorizedError"));
  }

  @Test
  @Order(8)
  @DisplayName("should reject login with non-existent user")
  void loginNonExistentUser() {
    Map<String, Object> body = Map.of(
        "identifier", "nonexistent-" + UUID.randomUUID(),
        "password", TEST_PASSWORD);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/login")
        .then()
        .statusCode(401);
  }

  // ========================================================================
  // Token Refresh
  // ========================================================================

  @Test
  @Order(9)
  @DisplayName("should refresh access token using refresh token")
  void refreshToken() {
    // Login to get refresh token
    String refreshToken = given()
        .contentType("application/json")
        .body(Map.of("identifier", TEST_USERNAME, "password", TEST_PASSWORD))
        .when()
        .post("/admin/login")
        .then()
        .statusCode(200)
        .extract()
        .path("refreshToken");

    given()
        .contentType("application/json")
        .body(Map.of("refreshToken", refreshToken))
        .when()
        .post("/admin/refresh-token")
        .then()
        .statusCode(200)
        .body("accessToken", notNullValue())
        .body("user.username", is(TEST_USERNAME));
  }

  @Test
  @Order(10)
  @DisplayName("should reject token refresh with invalid token")
  void refreshTokenInvalid() {
    given()
        .contentType("application/json")
        .body(Map.of("refreshToken", "invalid-token-value"))
        .when()
        .post("/admin/refresh-token")
        .then()
        .statusCode(401)
        .body("error.message", is("Invalid refresh token"));
  }

  // ========================================================================
  // Password Reset Flow
  // ========================================================================

  @Test
  @Order(11)
  @DisplayName("should initiate forgot password for known email")
  void forgotPassword() {
    given()
        .contentType("application/json")
        .body(Map.of("email", TEST_EMAIL))
        .when()
        .post("/admin/forgot-password")
        .then()
        .statusCode(200)
        .body("ok", is(true));
  }

  @Test
  @Order(12)
  @DisplayName("should succeed forgot password even for unknown email (no data leak)")
  void forgotPasswordUnknownEmail() {
    given()
        .contentType("application/json")
        .body(Map.of("email", "unknown@nowhere.local"))
        .when()
        .post("/admin/forgot-password")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // JWT Usage on Protected Endpoints
  // ========================================================================

  @Test
  @Order(13)
  @DisplayName("should use JWT to access authenticated endpoint")
  void jwtAuthenticatedRequest() {
    String accessToken = given()
        .contentType("application/json")
        .body(Map.of("identifier", TEST_USERNAME, "password", TEST_PASSWORD))
        .when()
        .post("/admin/login")
        .then()
        .statusCode(200)
        .extract()
        .path("accessToken");

    // Use JWT to access admin users endpoint
    given()
        .header("Authorization", "Bearer " + accessToken)
        .when()
        .get("/admin/users")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(14)
  @DisplayName("should reject request without JWT on protected endpoint")
  void noJwtRejected() {
    given()
        .when()
        .get("/admin/users")
        .then()
        .statusCode(401);
  }

  @Test
  @Order(15)
  @DisplayName("should reject request with invalid JWT")
  void invalidJwtRejected() {
    given()
        .header("Authorization", "Bearer invalid-jwt-token")
        .when()
        .get("/admin/users")
        .then()
        .statusCode(401);
  }
}
