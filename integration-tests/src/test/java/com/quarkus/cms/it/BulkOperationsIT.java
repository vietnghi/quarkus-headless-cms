package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for bulk operations and batch endpoint.
 *
 * <p>Data is seeded directly via Panache (CmsEntry) to bypass the @PermissionCheck
 * interceptor which requires authentication in the test environment.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Bulk Operations")
class BulkOperationsIT {

  @BeforeEach
  @Transactional
  void setUp() {
    CmsEntry.deleteAll();
  }

  // ========================================================================
  // Bulk Create
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should bulk create entries via POST /api/{contentType}/bulk")
  void bulkCreate() {
    List<Map<String, Object>> entries = List.of(
        Map.of("title", "Article 1", "category", "tech"),
        Map.of("title", "Article 2", "category", "science"),
        Map.of("title", "Article 3", "category", "tech")
    );

    given()
        .contentType("application/json")
        .body(entries)
        .when()
        .post("/api/article/bulk")
        .then()
        .statusCode(200)
        .body("data.size()", is(3))
        .body("meta.bulk.total", is(3))
        .body("meta.bulk.succeeded", is(3))
        .body("meta.bulk.failed", is(0))
        .body("data[0].data.documentId", notNullValue())
        .body("data[0].data.title", is("Article 1"))
        .body("data[0].data.status", is("draft"))
        .body("data[1].data.title", is("Article 2"))
        .body("data[2].data.title", is("Article 3"))
        .body("data[0].error", nullValue())
        .body("data[0].status", is(200));
  }

  @Test
  @Order(2)
  @DisplayName("should bulk create entries with locale")
  void bulkCreateWithLocale() {
    List<Map<String, Object>> entries = List.of(
        Map.of("title", "Bonjour"),
        Map.of("title", "Salut")
    );

    given()
        .contentType("application/json")
        .queryParam("locale", "fr")
        .body(entries)
        .when()
        .post("/api/article/bulk")
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.bulk.succeeded", is(2))
        .body("data[0].data.locale", is("fr"))
        .body("data[1].data.locale", is("fr"));
  }

  // ========================================================================
  // Bulk Update
  // ========================================================================

  @Test
  @Order(3)
  @DisplayName("should bulk update entries via PUT /api/{contentType}/bulk")
  void bulkUpdate() {
    // Seed entries directly
    List<String> documentIds = seedEntries("article", 2);

    // Prepare updates
    List<Map<String, Object>> updates = List.of(
        Map.of("documentId", documentIds.get(0), "data", Map.of("title", "Updated 1")),
        Map.of("documentId", documentIds.get(1), "data", Map.of("title", "Updated 2"))
    );

    given()
        .contentType("application/json")
        .body(updates)
        .when()
        .put("/api/article/bulk")
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.bulk.succeeded", is(2))
        .body("data[0].data.title", is("Updated 1"))
        .body("data[1].data.title", is("Updated 2"));
  }

  @Test
  @Order(4)
  @DisplayName("should fail bulk update when documentId is missing")
  void bulkUpdateValidation() {
    List<Map<String, Object>> updates = List.of(
        Map.of("data", Map.of("title", "No ID")) // missing documentId
    );

    given()
        .contentType("application/json")
        .body(updates)
        .when()
        .put("/api/article/bulk")
        .then()
        .statusCode(400);
  }

  // ========================================================================
  // Bulk Delete
  // ========================================================================

  @Test
  @Order(5)
  @DisplayName("should bulk delete entries via DELETE /api/{contentType}/bulk")
  void bulkDelete() {
    // Seed entries directly
    List<String> docIds = seedEntries("article", 2);

    given()
        .contentType("application/json")
        .body(Map.of("documentIds", docIds))
        .when()
        .delete("/api/article/bulk")
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.bulk.succeeded", is(2))
        .body("data[0].data.deleted", is(true))
        .body("data[1].data.deleted", is(true));

    // Verify entries are gone via direct query
    assertNoEntries(docIds);
  }

  @Test
  @Order(6)
  @DisplayName("should report not-found for bulk delete of missing entries")
  void bulkDeleteNotFound() {
    given()
        .contentType("application/json")
        .body(Map.of("documentIds", List.of("non-existent-id")))
        .when()
        .delete("/api/article/bulk")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("data[0].error", is("Entry not found: non-existent-id"))
        .body("data[0].status", is(404));
  }

  // ========================================================================
  // Batch Endpoint
  // ========================================================================

  @Test
  @Order(7)
  @DisplayName("should execute batch create operations via POST /api/batch")
  void batchCreate() {
    Map<String, Object> batchBody = Map.of("requests", List.of(
        Map.of("method", "POST", "path", "/api/article", "body", Map.of("title", "Batch 1")),
        Map.of("method", "POST", "path", "/api/article", "body", Map.of("title", "Batch 2"))
    ));

    given()
        .contentType("application/json")
        .body(batchBody)
        .when()
        .post("/api/batch")
        .then()
        .statusCode(200)
        .body("responses.size()", is(2))
        .body("meta.succeeded", is(2))
        .body("meta.failed", is(0))
        .body("responses[0].status", is(201))
        .body("responses[0].method", is("POST"))
        .body("responses[1].status", is(201));
  }

  @Test
  @Order(8)
  @DisplayName("should handle mixed batch operations including update and delete")
  void batchMixedOperations() {
    // Seed an entry directly
    String docId = seedEntry("article", "Batch Target");

    Map<String, Object> batchBody = Map.of("requests", List.of(
        Map.of("method", "POST", "path", "/api/article", "body", Map.of("title", "New Entry")),
        Map.of("method", "PUT", "path", "/api/article/" + docId, "body", Map.of("title", "Updated via Batch"))
    ));

    given()
        .contentType("application/json")
        .body(batchBody)
        .when()
        .post("/api/batch")
        .then()
        .statusCode(200)
        .body("responses.size()", is(2))
        .body("meta.succeeded", is(2))
        .body("responses[0].status", is(201))
        .body("responses[1].status", is(200));

    // Verify update took effect via direct query
    CmsEntry updated = CmsEntry.findByDocumentId(docId, "draft", "en");
    assert updated != null : "Entry should exist after batch update";
    assert "Updated via Batch".equals(updated.data.get("title"))
        : "Title should be updated: " + updated.data.get("title");
  }

  @Test
  @Order(9)
  @DisplayName("should reject unsupported methods in batch")
  void batchUnsupportedMethod() {
    Map<String, Object> batchBody = Map.of("requests", List.of(
        Map.of("method", "OPTIONS", "path", "/api/article", "body", Map.of())
    ));

    given()
        .contentType("application/json")
        .body(batchBody)
        .when()
        .post("/api/batch")
        .then()
        .statusCode(200)
        .body("responses.size()", is(1))
        .body("meta.failed", is(1))
        .body("responses[0].status", is(400))
        .body("responses[0].error", is("Unsupported method: OPTIONS"));
  }

  @Test
  @Order(10)
  @DisplayName("should reject nested batch operations")
  void batchNestedRejected() {
    Map<String, Object> batchBody = Map.of("requests", List.of(
        Map.of("method", "POST", "path", "/api/batch", "body", Map.of())
    ));

    given()
        .contentType("application/json")
        .body(batchBody)
        .when()
        .post("/api/batch")
        .then()
        .statusCode(200)
        .body("responses.size()", is(1))
        .body("meta.failed", is(1))
        .body("responses[0].status", is(400))
        .body("responses[0].error", is("Nested batch operations are not supported"));
  }

  @Test
  @Order(11)
  @DisplayName("should reject invalid paths in batch")
  void batchInvalidPath() {
    Map<String, Object> batchBody = Map.of("requests", List.of(
        Map.of("method", "GET", "path", "/not-api/path", "body", Map.of())
    ));

    given()
        .contentType("application/json")
        .body(batchBody)
        .when()
        .post("/api/batch")
        .then()
        .statusCode(200)
        .body("responses.size()", is(1))
        .body("meta.failed", is(1))
        .body("responses[0].status", is(400))
        .body("responses[0].error", is("Path must start with /api/"));
  }

  @Test
  @Order(12)
  @DisplayName("should delete entry via batch endpoint")
  void batchDeleteEntry() {
    // Seed an entry directly
    String docId = seedEntry("article", "Delete via Batch");

    Map<String, Object> batchBody = Map.of("requests", List.of(
        Map.of("method", "DELETE", "path", "/api/article/" + docId, "body", Map.of())
    ));

    given()
        .contentType("application/json")
        .body(batchBody)
        .when()
        .post("/api/batch")
        .then()
        .statusCode(200)
        .body("responses.size()", is(1))
        .body("meta.succeeded", is(1))
        .body("responses[0].status", is(200));

    // Verify deleted via direct query
    assert CmsEntry.findByDocumentId(docId, "draft", "en") == null
        : "Entry should be deleted";
  }

  // ========================================================================
  // Data Seeding Helpers (bypass PermissionCheck, use Panache directly)
  // ========================================================================

  /** Seeds a single entry directly and returns its document ID. */
  @Transactional
  String seedEntry(String contentType, String title) {
    CmsEntry entry = new CmsEntry();
    entry.documentId = UUID.randomUUID().toString();
    entry.contentType = contentType;
    entry.locale = "en";
    entry.status = "draft";
    entry.versionNumber = 0;
    entry.data = new java.util.HashMap<>(Map.of("title", title));
    entry.persist();
    return entry.documentId;
  }

  /** Seeds multiple entries directly and returns their document IDs. */
  @Transactional
  List<String> seedEntries(String contentType, int count) {
    List<String> ids = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      ids.add(seedEntry(contentType, "Entry " + i));
    }
    return ids;
  }

  /** Asserts that no entries with the given document IDs exist. */
  @Transactional
  void assertNoEntries(List<String> documentIds) {
    for (String docId : documentIds) {
      long count = CmsEntry.count("documentId", docId);
      assert count == 0 : "Entry should be deleted: " + docId;
    }
  }
}
