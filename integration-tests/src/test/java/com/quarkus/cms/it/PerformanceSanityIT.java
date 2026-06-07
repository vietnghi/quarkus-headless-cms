package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Performance sanity checks for key API endpoints.
 *
 * <p>Verifies that critical endpoints respond within acceptable
 * time bounds. These are NOT load-test assertions — they serve as
 * early-warning canaries that catch accidental N+1 queries or
 * blocking I/O regressions.
 */
@QuarkusTest
@DisplayName("Performance Sanity")
class PerformanceSanityIT {

  /** Maximum acceptable response time for simple endpoints (ms). */
  private static final long FAST_THRESHOLD_MS = 5000;

  /** Maximum acceptable response time for list/collection endpoints (ms). */
  private static final long LIST_THRESHOLD_MS = 10000;

  // ========================================================================
  // Fast Endpoints
  // ========================================================================

  @Test
  @DisplayName("health endpoint should respond within 5 seconds")
  void healthEndpointFast() {
    given()
        .when()
        .get("/api/health")
        .then()
        .statusCode(200)
        .time(lessThan(FAST_THRESHOLD_MS), TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("list tags should respond within 10 seconds")
  void listTagsFast() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/tag")
        .then()
        .statusCode(200)
        .time(lessThan(LIST_THRESHOLD_MS), TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("list articles should respond within 10 seconds")
  void listArticlesFast() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .time(lessThan(LIST_THRESHOLD_MS), TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("create and get article should respond within 5 seconds")
  void createArticleFast() {
    String docId = given()
        .contentType("application/json")
        .body(java.util.Map.of("title", "Perf Test", "slug", "perf-test", "body", "Testing"))
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .time(lessThan(FAST_THRESHOLD_MS), TimeUnit.MILLISECONDS)
        .extract()
        .path("data.documentId");

    given()
        .when()
        .get("/api/article/" + docId)
        .then()
        .statusCode(200)
        .time(lessThan(FAST_THRESHOLD_MS), TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("dashboard stats should respond within 10 seconds")
  void dashboardStatsFast() {
    given()
        .when()
        .get("/admin/dashboard-stats")
        .then()
        .statusCode(200)
        .time(lessThan(LIST_THRESHOLD_MS), TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("graphql schema introspection should respond within 10 seconds")
  void graphqlIntrospectionFast() {
    String query = "{ __schema { queryType { name fields { name } } } }";

    given()
        .contentType("application/json")
        .body(java.util.Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .time(lessThan(LIST_THRESHOLD_MS), TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("webhook list should respond within 5 seconds")
  void webhookListFast() {
    given()
        .when()
        .get("/admin/webhooks")
        .then()
        .statusCode(200)
        .time(lessThan(FAST_THRESHOLD_MS), TimeUnit.MILLISECONDS);
  }
}
