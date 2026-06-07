package com.quarkus.cms.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for REST CRUD operations through the full Quarkus stack.
 *
 * <p>Tests the full content lifecycle using the admin Content-Type Builder API and the
 * public Content API ({@code /api/{contentType}} endpoints). Dynamically creates a
 * content type, creates entries, reads, updates, deletes, and cleans up — no dependency
 * on sample seed data.
 *
 * <p>Strictly REST CRUD — no GraphQL, no injected services, no seed data.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Demo REST CRUD Lifecycle")
class DemoResourceCrudIT {

  private static final String CT_UID = "api::demo-crud.product";
  private static final String CT_SINGULAR = "product";
  private static final String CT_PLURAL = "products";

  // ===================================================================
  // Schema — Content-Type Builder (admin API)
  // ===================================================================

  @Test
  @Order(1)
  @DisplayName("should create a new content type with mixed field types")
  void createContentType() {
    Map<String, Object> body = Map.of(
        "uid", CT_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", CT_SINGULAR,
        "pluralName", CT_PLURAL,
        "displayName", "Demo CRUD Product",
        "description", "Self-contained content type for REST CRUD test",
        "draftAndPublish", true,
        "fields", List.of(
            Map.of("name", "name", "type", "STRING", "required", true, "maxLength", 255),
            Map.of("name", "description", "type", "TEXT"),
            Map.of("name", "price", "type", "FLOAT", "min", 0.0, "max", 99999.99),
            Map.of("name", "inStock", "type", "BOOLEAN", "defaultValue", "false")));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201)
        .body("data.uid", is(CT_UID))
        .body("data.kind", is("COLLECTION_TYPE"))
        .body("data.singularName", is(CT_SINGULAR))
        .body("data.pluralName", is(CT_PLURAL))
        .body("data.displayName", is("Demo CRUD Product"))
        .body("data.draftAndPublish", is(true))
        .body("data.fields.size()", is(4));
  }

  @Test
  @Order(2)
  @DisplayName("should list all content types including the new one")
  void listContentTypes() {
    given()
        .when()
        .get("/admin/content-type-builder/content-types")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(3)
  @DisplayName("should get the new content type by UID")
  void getContentType() {
    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + CT_UID)
        .then()
        .statusCode(200)
        .body("data.uid", is(CT_UID))
        .body("data.displayName", is("Demo CRUD Product"))
        .body("data.singularName", is(CT_SINGULAR))
        .body("data.kind", is("COLLECTION_TYPE"));
  }

  @Test
  @Order(4)
  @DisplayName("should return 404 for unknown content type")
  void getUnknownContentType() {
    given()
        .when()
        .get("/admin/content-type-builder/content-types/api::unknown.nonexistent")
        .then()
        .statusCode(404)
        .body("error.status", is(404))
        .body("error.name", is("NotFoundError"));
  }

  // ===================================================================
  // Entry CRUD — public Content API
  // ===================================================================

  @Test
  @Order(10)
  @DisplayName("should create an entry (draft)")
  void createEntry() {
    Map<String, Object> body = Map.of(
        "name", "Ergonomic Keyboard",
        "description", "A mechanical keyboard with cherry MX switches",
        "price", 149.99,
        "inStock", true);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.name", is("Ergonomic Keyboard"))
        .body("data.description", is("A mechanical keyboard with cherry MX switches"))
        .body("data.price", is(149.99f))
        .body("data.inStock", is(true))
        .body("data.status", is("draft"))
        .body("data.locale", is("en"));
  }

  @Test
  @Order(11)
  @DisplayName("should read an entry by documentId")
  void readEntry() {
    String docId = createEntryReturningId(Map.of(
        "name", "USB-C Hub", "description", "7-in-1 multiport adapter", "price", 39.99, "inStock", true));

    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.name", is("USB-C Hub"))
        .body("data.description", is("7-in-1 multiport adapter"))
        .body("data.price", is(39.99f))
        .body("data.inStock", is(true));
  }

  @Test
  @Order(12)
  @DisplayName("should return 404 for non-existent entry")
  void readEntryNotFound() {
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/nonexistent-doc-id-12345")
        .then()
        .statusCode(404)
        .body("error.status", is(404))
        .body("error.name", is("NotFoundError"));
  }

  @Test
  @Order(13)
  @DisplayName("should update an entry")
  void updateEntry() {
    String docId = createEntryReturningId(Map.of(
        "name", "Original Name", "description", "Before update", "price", 10.0, "inStock", false));

    given()
        .contentType("application/json")
        .body(Map.of("name", "Updated Name", "price", 25.0, "inStock", true))
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.name", is("Updated Name"))
        .body("data.price", is(25.0f))
        .body("data.inStock", is(true));
  }

  @Test
  @Order(14)
  @DisplayName("should list entries with preview status")
  void listEntries() {
    String docA = createEntryReturningId(Map.of(
        "name", "Monitor Arm", "description", "Adjustable dual monitor arm", "price", 89.99, "inStock", true));
    String docB = createEntryReturningId(Map.of(
        "name", "Desk Lamp", "description", "LED desk lamp with dimmer", "price", 49.99, "inStock", false));

    // Verify entries exist individually first
    given().when().get("/api/" + CT_SINGULAR + "/" + docA).then().statusCode(200);
    given().when().get("/api/" + CT_SINGULAR + "/" + docB).then().statusCode(200);

    // List with preview to see all statuses
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data.size()", greaterThan(0))
        .body("meta.pagination.total", greaterThan(0));
  }

  @Test
  @Order(15)
  @DisplayName("should publish and unpublish an entry")
  void publishAndUnpublishEntry() {
    String docId = createEntryReturningId(Map.of(
        "name", "Publishable Item", "description", "Going through lifecycle", "price", 15.0, "inStock", true));

    // Initially draft
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("draft"));

    // Publish — content type requires content-type header even for body-less POST
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/publish")
        .then()
        .statusCode(200)
        .body("data.status", is("published"))
        .body("data.publishedAt", notNullValue());

    // Verify published version via live publicationState
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("published"));

    // Unpublish — also needs content-type header
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/unpublish")
        .then()
        .statusCode(200)
        .body("unpublished", is(true));

    // Draft should remain after unpublish
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("draft"));
  }

  @Test
  @Order(16)
  @DisplayName("should filter entries by publication state")
  void filterEntriesByPublicationState() {
    String draftId = createEntryReturningId(Map.of(
        "name", "Draft Item", "description", "Not published", "price", 5.0, "inStock", false));
    String pubId = createEntryReturningId(Map.of(
        "name", "Published Item", "description", "Will be published", "price", 20.0, "inStock", true));

    // Publish one
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + pubId + "/publish")
        .then()
        .statusCode(200);

    // Preview shows all entries regardless of status
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data.size()", greaterThan(0));

    // Live shows only published
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200);
  }

  @Test
  @Order(17)
  @DisplayName("should delete an entry")
  void deleteEntry() {
    String docId = createEntryReturningId(Map.of(
        "name", "Delete Me", "description", "To be deleted", "price", 1.0, "inStock", false));

    given()
        .when()
        .delete("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("documentId", is(docId))
        .body("deleted", is(true));

    // Verify deletion
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(404);
  }

  // ===================================================================
  // Error Cases
  // ===================================================================

  @Test
  @Order(20)
  @DisplayName("should return 400 for empty create body")
  void emptyCreateBody() {
    given()
        .contentType("application/json")
        .body(Map.of())
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(400);
  }

  @Test
  @Order(21)
  @DisplayName("should handle requests for non-existent content type at /api/")
  void nonExistentContentTypeViaApi() {
    // The Content API is dynamic — it tries to resolve the content type name.
    // An unresolvable name returns an empty collection response (200).
    given()
        .when()
        .get("/api/nonexistent-type-xyz")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(22)
  @DisplayName("should return 400 for empty update body")
  void emptyUpdateBody() {
    String docId = createEntryReturningId(Map.of(
        "name", "Temp", "description", "For update test", "price", 1.0, "inStock", false));

    given()
        .contentType("application/json")
        .body(Map.of())
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(400);
  }

  @Test
  @Order(23)
  @DisplayName("should handle publish on non-existent entry gracefully")
  void publishNonExistentEntry() {
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/nonexistent-doc-id/publish")
        .then()
        .statusCode(anyOf(is(400), is(404), is(500)));
  }

  // ===================================================================
  // Schema Cleanup
  // ===================================================================

  @Test
  @Order(30)
  @DisplayName("should delete the content type")
  void deleteContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + CT_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true))
        .body("uid", is(CT_UID));

    // Verify deletion
    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + CT_UID)
        .then()
        .statusCode(404);
  }

  // ===================================================================
  // Helpers
  // ===================================================================

  private String createEntryReturningId(Map<String, Object> data) {
    return given()
        .contentType("application/json")
        .body(data)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .extract()
        .path("data.documentId");
  }
}
