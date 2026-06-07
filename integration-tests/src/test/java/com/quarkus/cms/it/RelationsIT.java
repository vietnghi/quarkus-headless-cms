package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasItems;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Self-contained integration tests for the Relations API endpoint
 * at {@code /api/{contentType}/{documentId}/relations}.
 *
 * <p>Creates two content types (parent, child) with a many-to-one relation,
 * then exercises the relations endpoints end-to-end. Cleans up after itself.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Relations API")
class RelationsIT {

  private static final String PARENT_UID = "api::relations-parent.parent";
  private static final String PARENT_SINGULAR = "relations-parent";
  private static final String PARENT_PLURAL = "relations-parents";

  private static final String CHILD_UID = "api::relations-child.child";
  private static final String CHILD_SINGULAR = "relations-child";
  private static final String CHILD_PLURAL = "relations-children";

  private static String parentDocId;
  private static String childDocId;

  // ===================================================================
  // Schema — Content-Type Builder
  // ===================================================================

  @Test
  @Order(1)
  @DisplayName("should create parent content type")
  void createParentContentType() {
    Map<String, Object> body = Map.of(
        "uid", PARENT_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", PARENT_SINGULAR,
        "pluralName", PARENT_PLURAL,
        "displayName", "Relations Parent",
        "description", "Parent content type for relations testing",
        "draftAndPublish", false,
        "localized", false,
        "fields", List.of(
            Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 255)));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201)
        .body("data.uid", is(PARENT_UID))
        .body("data.kind", is("COLLECTION_TYPE"))
        .body("data.singularName", is(PARENT_SINGULAR));
  }

  @Test
  @Order(2)
  @DisplayName("should create child content type with relation to parent")
  void createChildContentType() {
    Map<String, Object> body = Map.of(
        "uid", CHILD_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", CHILD_SINGULAR,
        "pluralName", CHILD_PLURAL,
        "displayName", "Relations Child",
        "description", "Child content type with many-to-one relation to parent",
        "draftAndPublish", false,
        "localized", false,
        "fields", List.of(
            Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 255)),
        "relations", List.of(
            Map.of(
                "fieldName", "parent",
                "type", "MANY_TO_ONE",
                "target", PARENT_UID,
                "targetAttribute", "children")));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201)
        .body("data.uid", is(CHILD_UID))
        .body("data.relations.size()", is(1))
        .body("data.relations[0].fieldName", is("parent"))
        .body("data.relations[0].type", is("MANY_TO_ONE"))
        .body("data.relations[0].target", is(PARENT_UID));
  }

  // ===================================================================
  // Entry creation
  // ===================================================================

  @Test
  @Order(10)
  @DisplayName("should create a parent entry")
  void createParentEntry() {
    Map<String, Object> body = Map.of("title", "Test Parent");

    parentDocId = given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + PARENT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Test Parent"))
        .extract()
        .path("data.documentId");
  }

  @Test
  @Order(11)
  @DisplayName("should create a child entry")
  void createChildEntry() {
    Map<String, Object> body = Map.of("title", "Test Child");

    childDocId = given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CHILD_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Test Child"))
        .extract()
        .path("data.documentId");
  }

  // ===================================================================
  // Relations API — list relation fields
  // ===================================================================

  @Test
  @Order(20)
  @DisplayName("should list relation field definitions for child content type")
  void listRelationFields() {
    given()
        .when()
        .get("/api/" + CHILD_SINGULAR + "/" + childDocId + "/relations")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.size()", is(1))
        .body("data[0].fieldName", is("parent"))
        .body("data[0].type", is("MANY_TO_ONE"))
        .body("data[0].target", is(PARENT_UID))
        .body("data[0].bidirectional", is(true));
  }

  @Test
  @Order(21)
  @DisplayName("should list empty relations for parent content type (no relations defined)")
  void listParentRelationFields() {
    given()
        .when()
        .get("/api/" + PARENT_SINGULAR + "/" + parentDocId + "/relations")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.size()", is(0));
  }

  // ===================================================================
  // Relations API — resolve relation fields (empty before attaching)
  // ===================================================================

  @Test
  @Order(22)
  @DisplayName("should return empty result for unresolved parent relation")
  void resolveParentRelationEmpty() {
    given()
        .when()
        .get("/api/" + CHILD_SINGULAR + "/" + childDocId + "/relations/parent")
        .then()
        .statusCode(200)
        .body("data", nullValue());
  }

  // ===================================================================
  // Attach relation via ContentManagerService (simulated via direct endpoints)
  // ===================================================================

  @Test
  @Order(30)
  @DisplayName("should create relation by updating child entry with parent reference")
  void attachRelation() {
    // Update the child entry to include a parent reference
    given()
        .contentType("application/json")
        .body(Map.of("parent", parentDocId))
        .when()
        .put("/api/" + CHILD_SINGULAR + "/" + childDocId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(childDocId));
  }

  // ===================================================================
  // Relations API — resolve relation fields (after attaching)
  // ===================================================================

  @Test
  @Order(31)
  @DisplayName("should resolve parent relation target after attaching")
  void resolveParentRelation() {
    given()
        .when()
        .get("/api/" + CHILD_SINGULAR + "/" + childDocId + "/relations/parent")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.documentId", is(parentDocId))
        .body("data.title", is("Test Parent"));
  }

  // ===================================================================
  // Error cases
  // ===================================================================

  @Test
  @Order(40)
  @DisplayName("should return 404 for unknown content type")
  void resolveUnknownContentType() {
    given()
        .when()
        .get("/api/nonexistent/doc-id/relations/parent")
        .then()
        .statusCode(404)
        .body("error.status", is(404))
        .body("error.name", is("NotFoundError"));
  }

  @Test
  @Order(41)
  @DisplayName("should return 404 for non-existent documentId")
  void resolveUnknownDocument() {
    given()
        .when()
        .get("/api/" + CHILD_SINGULAR + "/nonexistent-doc-id/relations/parent")
        .then()
        .statusCode(404)
        .body("error.status", is(404))
        .body("error.name", is("NotFoundError"));
  }

  @Test
  @Order(42)
  @DisplayName("should return 404 for non-existent relation field name")
  void resolveUnknownRelationField() {
    given()
        .when()
        .get("/api/" + CHILD_SINGULAR + "/" + childDocId + "/relations/unknownField")
        .then()
        .statusCode(404)
        .body("error.status", is(404))
        .body("error.name", is("NotFoundError"));
  }

  @Test
  @Order(43)
  @DisplayName("should return 404 for unknown content type in list endpoint")
  void listUnknownContentType() {
    given()
        .when()
        .get("/api/nonexistent/doc-id/relations")
        .then()
        .statusCode(404)
        .body("error.status", is(404))
        .body("error.name", is("NotFoundError"));
  }

  @Test
  @Order(44)
  @DisplayName("should return 404 for non-existent documentId in resolve but list endpoint ignores it")
  void listUnknownDocument() {
    // The list endpoint returns the content type's relation definitions without
    // validating the documentId, so this returns 200 even with a non-existent doc ID.
    given()
        .when()
        .get("/api/" + CHILD_SINGULAR + "/nonexistent-doc-id/relations")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  // ===================================================================
  // Locale/status filter variants
  // ===================================================================

  @Test
  @Order(50)
  @DisplayName("should accept locale query param when resolving relation")
  void resolveWithLocale() {
    given()
        .queryParam("locale", "en")
        .when()
        .get("/api/" + CHILD_SINGULAR + "/" + childDocId + "/relations/parent")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.documentId", is(parentDocId));
  }

  @Test
  @Order(51)
  @DisplayName("should accept status query param when resolving relation")
  void resolveWithStatus() {
    given()
        .queryParam("status", "draft")
        .when()
        .get("/api/" + CHILD_SINGULAR + "/" + childDocId + "/relations/parent")
        .then()
        .statusCode(200);
  }

  // ===================================================================
  // Schema Cleanup
  // ===================================================================

  @Test
  @Order(60)
  @DisplayName("should delete child content type")
  void deleteChildContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + CHILD_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true))
        .body("uid", is(CHILD_UID));

    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + CHILD_UID)
        .then()
        .statusCode(404);
  }

  @Test
  @Order(61)
  @DisplayName("should delete parent content type")
  void deleteParentContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + PARENT_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true))
        .body("uid", is(PARENT_UID));

    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + PARENT_UID)
        .then()
        .statusCode(404);
  }
}
