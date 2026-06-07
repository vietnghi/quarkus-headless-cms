package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

import com.quarkus.cms.core.domain.CmsEntry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for the full content lifecycle via REST endpoints.
 *
 * <p>Covers: content-type schema creation → entry CRUD → schema cleanup.
 * Tests the end-to-end flow through the admin Content-Type Builder API
 * and the public Content API, all via REST endpoints (RestAssured).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Content Lifecycle")
class ContentLifecycleIT {

  private static final String CT_UID = "api::project.project";
  private static final String CT_SINGULAR = "project";
  private static final String CT_PLURAL = "projects";

  @BeforeEach
  @Transactional
  void setUp() {
    CmsEntry.deleteAll();
  }

  // =======================================================================
  // Schema Administration — Content-Type Builder
  // =======================================================================

  @Test
  @Order(1)
  @DisplayName("should create a new content type via admin API")
  void createContentType() {
    Map<String, Object> body = Map.of(
        "uid", CT_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", CT_SINGULAR,
        "pluralName", CT_PLURAL,
        "displayName", "Project",
        "description", "A project content type",
        "draftAndPublish", true,
        "fields", List.of(
            Map.of(
                "name", "title",
                "type", "STRING",
                "required", true,
                "maxLength", 255),
            Map.of(
                "name", "description",
                "type", "TEXT"),
            Map.of(
                "name", "priority",
                "type", "INTEGER",
                "min", 1,
                "max", 5),
            Map.of(
                "name", "active",
                "type", "BOOLEAN",
                "defaultValue", "true"))
    );

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
  @DisplayName("should list the new content type in the admin API")
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
        .body("data.displayName", is("Project"));
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

  // =======================================================================
  // Entry CRUD — Content API
  // =======================================================================

  @Test
  @Order(10)
  @DisplayName("should create an entry for the new content type")
  void createEntry() {
    Map<String, Object> body = Map.of(
        "title", "My First Project",
        "description", "A test project entry",
        "priority", 3,
        "active", true);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("My First Project"))
        .body("data.description", is("A test project entry"))
        .body("data.priority", is(3))
        .body("data.active", is(true))
        .body("data.status", is("draft"))
        .body("data.locale", is("en"));
  }

  @Test
  @Order(11)
  @DisplayName("should read an entry by documentId")
  void readEntry() {
    String docId = createEntryReturningId(Map.of(
        "title", "Readable Project",
        "description", "Can I read this?",
        "priority", 2,
        "active", true));

    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.title", is("Readable Project"))
        .body("data.priority", is(2));
  }

  @Test
  @Order(12)
  @DisplayName("should update an entry")
  void updateEntry() {
    String docId = createEntryReturningId(Map.of(
        "title", "Original Title",
        "description", "About to be updated",
        "priority", 1,
        "active", true));

    given()
        .contentType("application/json")
        .body(Map.of("title", "Updated Title", "priority", 4))
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.title", is("Updated Title"))
        .body("data.priority", is(4))
        .body("data.documentId", is(docId));
  }

  @Test
  @Order(13)
  @DisplayName("should list entries for the new content type")
  void listEntries() {
    String docA = createEntryReturningId(Map.of(
        "title", "Alpha", "description", "First", "priority", 1, "active", true));
    String docB = createEntryReturningId(Map.of(
        "title", "Beta", "description", "Second", "priority", 2, "active", true));

    // Publish both so they appear in the public API list
    given().when().post("/api/" + CT_SINGULAR + "/" + docA + "/publish").then().statusCode(200);
    given().when().post("/api/" + CT_SINGULAR + "/" + docB + "/publish").then().statusCode(200);

    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data", hasSize(2))
        .body("meta.pagination.total", is(2));
  }

  @Test
  @Order(14)
  @DisplayName("should delete an entry")
  void deleteEntry() {
    String docId = createEntryReturningId(Map.of(
        "title", "Delete Me",
        "description", "This entry will be deleted",
        "priority", 1,
        "active", false));

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

  @Test
  @Order(15)
  @DisplayName("should publish and unpublish an entry")
  void publishAndUnpublishEntry() {
    String docId = createEntryReturningId(Map.of(
        "title", "Publish Me",
        "description", "Going through lifecycle",
        "priority", 3,
        "active", true));

    // Publish
    given()
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/publish")
        .then()
        .statusCode(200)
        .body("data.status", is("published"))
        .body("data.publishedAt", notNullValue());

    // Unpublish
    given()
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/unpublish")
        .then()
        .statusCode(200)
        .body("unpublished", is(true));
  }

  @Test
  @Order(16)
  @DisplayName("should reject empty create body")
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
  @Order(17)
  @DisplayName("should return 404 for non-existent documentId")
  void nonExistentDocumentId() {
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/nonexistent-doc-id")
        .then()
        .statusCode(404);
  }

  // =======================================================================
  // Content-Type Update and Cleanup
  // =======================================================================

  @Test
  @Order(20)
  @DisplayName("should update content type display name")
  void updateContentType() {
    given()
        .contentType("application/json")
        .body(Map.ofEntries(
            Map.entry("uid", CT_UID),
            Map.entry("kind", "COLLECTION_TYPE"),
            Map.entry("singularName", CT_SINGULAR),
            Map.entry("pluralName", CT_PLURAL),
            Map.entry("displayName", "Updated Project"),
            Map.entry("description", "An updated project content type"),
            Map.entry("draftAndPublish", true),
            Map.entry("fields", List.of(
                Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 255),
                Map.of("name", "description", "type", "TEXT"),
                Map.of("name", "priority", "type", "INTEGER", "min", 1, "max", 5),
                Map.of("name", "active", "type", "BOOLEAN", "defaultValue", "true")))))
        .when()
        .put("/admin/content-type-builder/content-types/" + CT_UID)
        .then()
        .statusCode(200)
        .body("data.displayName", is("Updated Project"))
        .body("data.uid", is(CT_UID));
  }

  @Test
  @Order(21)
  @DisplayName("should get version history for content type")
  void getContentTypeVersions() {
    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + CT_UID + "/versions")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(22)
  @DisplayName("should delete the content type")
  void deleteContentType() {
    // Delete all entries first
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data", notNullValue());

    // Delete the content type schema
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

  // =======================================================================
  // Helper Methods
  // =======================================================================

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
