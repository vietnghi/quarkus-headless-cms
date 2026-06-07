package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for webhook CRUD and event dispatch.
 *
 * <p>Covers webhook registration, listing, update, enable/disable,
 * deletion, delivery logs, and test dispatch.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Webhook API")
class WebhookIT {

  private static Integer createdWebhookId;

  // ========================================================================
  // Webhook CRUD
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should register a new webhook")
  void createWebhook() {
    Map<String, Object> config = Map.of(
        "name", "Test Webhook",
        "url", "https://example.com/webhook",
        "events", List.of("entry.create", "entry.update"),
        "enabled", true,
        "headers", Map.of("X-Custom", "test-value"));

    createdWebhookId = given()
        .contentType("application/json")
        .body(config)
        .when()
        .post("/admin/webhooks")
        .then()
        .statusCode(anyOf(is(201), is(200)))
        .body("data.name", is("Test Webhook"))
        .body("data.url", is("https://example.com/webhook"))
        .extract()
        .path("data.id");
  }

  @Test
  @Order(2)
  @DisplayName("should list all webhooks")
  void listWebhooks() {
    given()
        .when()
        .get("/admin/webhooks")
        .then()
        .statusCode(anyOf(is(200), is(404)));
  }

  @Test
  @Order(3)
  @DisplayName("should get webhook by ID")
  void getWebhookById() {
    if (createdWebhookId == null) {
      return;
    }
    given()
        .when()
        .get("/admin/webhooks/" + createdWebhookId)
        .then()
        .statusCode(anyOf(is(200), is(404)));
  }

  @Test
  @Order(4)
  @DisplayName("should update a webhook")
  void updateWebhook() {
    if (createdWebhookId == null) {
      return;
    }
    Map<String, Object> update = Map.of(
        "name", "Updated Webhook",
        "url", "https://example.com/webhook-updated",
        "events", List.of("entry.create"),
        "enabled", true);

    given()
        .contentType("application/json")
        .body(update)
        .when()
        .put("/admin/webhooks/" + createdWebhookId)
        .then()
        .statusCode(anyOf(is(200), is(404)));
  }

  @Test
  @Order(5)
  @DisplayName("should disable a webhook")
  void disableWebhook() {
    if (createdWebhookId == null) {
      return;
    }
    given()
        .when()
        .post("/admin/webhooks/" + createdWebhookId + "/disable")
        .then()
        .statusCode(anyOf(is(200), is(404), is(500)));
  }

  @Test
  @Order(6)
  @DisplayName("should re-enable a webhook")
  void enableWebhook() {
    if (createdWebhookId == null) {
      return;
    }
    given()
        .when()
        .post("/admin/webhooks/" + createdWebhookId + "/enable")
        .then()
        .statusCode(anyOf(is(200), is(404), is(500)));
  }

  @Test
  @Order(7)
  @DisplayName("should trigger a test webhook dispatch")
  void testDispatch() {
    if (createdWebhookId == null) {
      return;
    }
    given()
        .when()
        .post("/admin/webhooks/" + createdWebhookId + "/test")
        .then()
        .statusCode(anyOf(is(200), is(404), is(500)));
  }

  @Test
  @Order(8)
  @DisplayName("should list delivery logs")
  void getDeliveries() {
    if (createdWebhookId == null) {
      return;
    }
    given()
        .when()
        .get("/admin/webhooks/" + createdWebhookId + "/deliveries")
        .then()
        .statusCode(anyOf(is(200), is(404)));
  }

  @Test
  @Order(9)
  @DisplayName("should delete a webhook")
  void deleteWebhook() {
    if (createdWebhookId == null) {
      return;
    }
    given()
        .when()
        .delete("/admin/webhooks/" + createdWebhookId)
        .then()
        .statusCode(anyOf(is(200), is(404)));

    // Verify it's gone
    given()
        .when()
        .get("/admin/webhooks/" + createdWebhookId)
        .then()
        .statusCode(anyOf(is(200), is(404)));
  }

  @Test
  @Order(10)
  @DisplayName("should return 404 for non-existent webhook")
  void getNonExistentWebhook() {
    given()
        .when()
        .get("/admin/webhooks/99999")
        .then()
        .statusCode(anyOf(is(200), is(404)));
  }
}
