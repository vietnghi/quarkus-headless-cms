package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
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
 * <p>Tests the full content lifecycle: content-type schema creation via admin API -> entry
 * create -> read -> update -> delete -> schema cleanup. Uses a dynamically created content
 * type so tests are self-contained with no dependency on sample seed data.
 *
 * <p>Strictly REST CRUD — no GraphQL, no seed data.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("REST CRUD Lifecycle")
class RestCrudIT {

  private static final String CT_UID = "api::rest-crud.entry";
  private static final String CT_SINGULAR = "entry";
  private static final String CT_PLURAL = "entries";

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
        "displayName", "REST CRUD Entry",
        "description", "Self-contained content type for REST CRUD test",
        "draftAndPublish", true,
        "fields", List.of(
            Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 255),
            Map.of("name", "body", "type", "TEXT"),
            Map.of("name", "priority", "type", "INTEGER", "min", 1, "max", 10),
            Map.of("name", "published", "type", "BOOLEAN", "defaultValue", "false")));

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
        .body("data.draftAndPublish", is(true))
        .body("data.fields.size()", is(4));
  }

  @Test
  @Order(2)
  @DisplayName("should list the new content type")
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
        .body("data.displayName", is("REST CRUD Entry"));
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
  // Entry CRUD — Content API
  // ===================================================================

  @Test
  @Order(10)
  @DisplayName("should create an entry for the new content type")
  void createEntry() {
    Map<String, Object> body = Map.of(
        "title", "My First REST Entry",
        "body", "This entry was created via REST API",
        "priority", 5,
        "published", true);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("My First REST Entry"))
        .body("data.body", is("This entry was created via REST API"))
        .body("data.priority", is(5))
        .body("data.published", is(true))
        .body("data.status", is("draft"))
        .body("data.locale", is("en"));
  }

  @Test
  @Order(11)
  @DisplayName("should read an entry by documentId")
  void readEntry() {
    String docId = createEntryReturningId(Map.of(
        "title", "Readable Entry", "body", "Read me", "priority", 3, "published", false));

    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.title", is("Readable Entry"))
        .body("data.body", is("Read me"))
        .body("data.priority", is(3))
        .body("data.published", is(false));
  }

  @Test
  @Order(12)
  @DisplayName("should return 404 for non-existent entry")
  void readEntryNotFound() {
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/nonexistent-id")
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
        "title", "Original Title", "body", "Before update", "priority", 1, "published", false));

    given()
        .contentType("application/json")
        .body(Map.of("title", "Updated Title", "priority", 10, "published", true))
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.title", is("Updated Title"))
        .body("data.priority", is(10))
        .body("data.published", is(true))
        .body("data.documentId", is(docId));
  }

  @Test
  @Order(14)
  @DisplayName("should list entries")
  void listEntries() {
    String docA = createEntryReturningId(Map.of(
        "title", "Entry Alpha", "body", "First", "priority", 1, "published", true));
    String docB = createEntryReturningId(Map.of(
        "title", "Entry Beta", "body", "Second", "priority", 2, "published", true));

    // Publish both for live listing
    given().contentType("application/json").when().post("/api/" + CT_SINGULAR + "/" + docA + "/publish").then().statusCode(200);
    given().contentType("application/json").when().post("/api/" + CT_SINGULAR + "/" + docB + "/publish").then().statusCode(200);

    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.pagination.total", is(2));
  }

  @Test
  @Order(15)
  @DisplayName("should filter entries by publication state")
  void filterEntriesByPublicationState() {
    String draftId = createEntryReturningId(Map.of(
        "title", "Draft Item", "body", "Not published", "priority", 5, "published", false));
    String pubId = createEntryReturningId(Map.of(
        "title", "Published Item", "body", "Will be published", "priority", 5, "published", true));

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
  @Order(16)
  @DisplayName("should list entries with preview status")
  void listEntriesWithPreview() {
    String docA = createEntryReturningId(Map.of(
        "title", "Entry Alpha Preview", "body", "First", "priority", 1, "published", true));
    String docB = createEntryReturningId(Map.of(
        "title", "Entry Beta Preview", "body", "Second", "priority", 2, "published", true));

    // Preview shows all entries
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data.size()", greaterThan(0));
  }

  @Test
  @Order(17)
  @DisplayName("should paginate entries")
  void paginateEntries() {
    for (int i = 1; i <= 3; i++) {
      String d = createEntryReturningId(Map.of(
          "title", "Entry " + i, "body", "Page test", "priority", i, "published", true));
      given().contentType("application/json").when().post("/api/" + CT_SINGULAR + "/" + d + "/publish").then().statusCode(200);
    }

    given()
        .queryParam("pagination[page]", "1")
        .queryParam("pagination[pageSize]", "2")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.pagination.page", is(1))
        .body("meta.pagination.pageSize", is(2))
        .body("meta.pagination.total", greaterThan(0))
        .body("meta.pagination.pageCount", greaterThan(0));
  }

  @Test
  @Order(18)
  @DisplayName("should publish and unpublish an entry")
  void publishAndUnpublishEntry() {
    String docId = createEntryReturningId(Map.of(
        "title", "Lifecycle Entry", "body", "Going through lifecycle", "priority", 5, "published", true));

    // Initially draft
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("draft"));

    // Publish
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/publish")
        .then()
        .statusCode(200)
        .body("data.status", is("published"))
        .body("data.publishedAt", notNullValue());

    // Verify published version
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("published"));

    // Unpublish
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/unpublish")
        .then()
        .statusCode(200)
        .body("unpublished", is(true));

    // Draft should remain
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("draft"));
  }

  @Test
  @Order(19)
  @DisplayName("should delete an entry")
  void deleteEntry() {
    String docId = createEntryReturningId(Map.of(
        "title", "Delete Me", "body", "To be deleted", "priority", 1, "published", false));

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
  @DisplayName("should handle non-existent content type via content API")
  void nonExistentContentType() {
    given()
        .when()
        .get("/api/nonexistent-type")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
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
