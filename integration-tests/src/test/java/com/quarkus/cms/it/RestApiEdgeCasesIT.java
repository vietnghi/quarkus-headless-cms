package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.anyOf;
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
 * Self-contained integration tests for REST API edge cases not covered by
 * other test classes.
 *
 * <p>Covers field selection (Strapi v5 {@code fields[]}), the Strapi v5
 * {@code data} envelope format, specific populate, and locale resolution
 * via headers. Creates its own content type and cleans up after itself.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("REST API Edge Cases")
class RestApiEdgeCasesIT {

  private static final String CT_UID = "api::rest-edge.edge";
  private static final String CT_SINGULAR = "rest-edge";
  private static final String CT_PLURAL = "rest-edges";

  // ===================================================================
  // Schema — Content-Type Builder
  // ===================================================================

  @Test
  @Order(1)
  @DisplayName("should create content type with varied field types")
  void createContentType() {
    Map<String, Object> body = Map.of(
        "uid", CT_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", CT_SINGULAR,
        "pluralName", CT_PLURAL,
        "displayName", "REST API Edge Cases",
        "description", "Self-contained edge case content type",
        "draftAndPublish", true,
        "fields", List.of(
            Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 255),
            Map.of("name", "body", "type", "TEXT"),
            Map.of("name", "priority", "type", "INTEGER", "min", 1, "max", 10),
            Map.of("name", "visible", "type", "BOOLEAN", "defaultValue", "false"),
            Map.of("name", "score", "type", "FLOAT", "min", 0, "max", 100)));

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
        .body("data.fields.size()", is(5));
  }

  // ===================================================================
  // Strapi v5 "data" envelope format
  // ===================================================================

  private static String envelopeDocId;

  @Test
  @Order(10)
  @DisplayName("should create entry with Strapi v5 data envelope")
  void createWithDataEnvelope() {
    // Strapi v5 clients can send body wrapped in {"data": {...}}
    Map<String, Object> envelope = Map.of(
        "data", Map.of(
            "title", "Envelope Create",
            "body", "Created via data envelope",
            "priority", 7,
            "visible", true,
            "score", 85.5));

    envelopeDocId = given()
        .contentType("application/json")
        .body(envelope)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Envelope Create"))
        .body("data.body", is("Created via data envelope"))
        .body("data.priority", is(7))
        .body("data.visible", is(true))
        .body("data.score", is(85.5f))
        .body("data.status", is("draft"))
        .body("data.locale", is("en"))
        .extract()
        .path("data.documentId");
  }

  @Test
  @Order(11)
  @DisplayName("should update entry with Strapi v5 data envelope")
  void updateWithDataEnvelope() {
    Map<String, Object> envelope = Map.of(
        "data", Map.of("title", "Updated via Envelope", "priority", 10));

    given()
        .contentType("application/json")
        .body(envelope)
        .when()
        .put("/api/" + CT_SINGULAR + "/" + envelopeDocId)
        .then()
        .statusCode(200)
        .body("data.title", is("Updated via Envelope"))
        .body("data.priority", is(10))
        .body("data.documentId", is(envelopeDocId));
  }

  // ===================================================================
  // Fields selection (Strapi v5 fields[])
  // ===================================================================

  @Test
  @Order(20)
  @DisplayName("should filter response fields via fields[] on findMany")
  void fieldsSelectionOnFindMany() {
    // Create entries with various fields set
    String docA = createEntryReturningId(Map.of(
        "title", "Alpha", "body", "Alpha body", "priority", 1, "visible", true, "score", 10.0));
    String docB = createEntryReturningId(Map.of(
        "title", "Beta", "body", "Beta body", "priority", 2, "visible", true, "score", 20.0));

    // Publish both for live listing
    given().contentType("application/json").when()
        .post("/api/" + CT_SINGULAR + "/" + docA + "/publish").then().statusCode(200);
    given().contentType("application/json").when()
        .post("/api/" + CT_SINGULAR + "/" + docB + "/publish").then().statusCode(200);

    // Request only title and score fields
    given()
        .queryParam("fields[0]", "title")
        .queryParam("fields[1]", "score")
        .queryParam("publicationState", "live")
        .contentType("application/json")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        // Both titles should be present (order not guaranteed)
        .body("data.title.flatten()", hasItems("Alpha", "Beta"))
        // Score should be present
        .body("data.score.flatten()", hasItems(10.0f, 20.0f))
        // Excluded fields should not appear
        .body("data[0].body", nullValue())
        .body("data[0].priority", nullValue())
        .body("data[0].visible", nullValue())
        // Standard metadata should always be present
        .body("data[0].id", notNullValue())
        .body("data[0].documentId", notNullValue())
        .body("data[0].locale", is("en"))
        .body("data[0].status", is("published"));

    // Clean up
    given().when().delete("/api/" + CT_SINGULAR + "/" + docA).then().statusCode(200);
    given().when().delete("/api/" + CT_SINGULAR + "/" + docB).then().statusCode(200);
  }

  @Test
  @Order(21)
  @DisplayName("should filter response fields via fields[] on findOne")
  void fieldsSelectionOnFindOne() {
    String docId = createEntryReturningId(Map.of(
        "title", "FindOne Fields", "body", "Should be hidden", "priority", 5,
        "visible", true, "score", 50.0));

    // Publish
    given().contentType("application/json").when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/publish").then().statusCode(200);

    // Request only title field
    given()
        .contentType("application/json")
        .queryParam("fields", "title")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.title", is("FindOne Fields"))
        // Excluded fields
        .body("data.body", nullValue())
        .body("data.priority", nullValue())
        .body("data.score", nullValue());

    // Clean up
    given().when().delete("/api/" + CT_SINGULAR + "/" + docId).then().statusCode(200);
  }

  // ===================================================================
  // Populate with specific field name on findOne
  // ===================================================================

  @Test
  @Order(30)
  @DisplayName("should accept populate with specific field name on findOne")
  void populateSpecificFieldOnFindOne() {
    String docId = createEntryReturningId(Map.of(
        "title", "Populate Test", "body", "Populate specific field", "priority", 3));

    // Publish
    given().contentType("application/json").when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/publish").then().statusCode(200);

    // Request populate with specific field name (even though there's no relation,
    // the endpoint should handle it gracefully and return the entry)
    given()
        .contentType("application/json")
        .queryParam("populate", "author")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.title", is("Populate Test"));

    // Clean up
    given().when().delete("/api/" + CT_SINGULAR + "/" + docId).then().statusCode(200);
  }

  // ===================================================================
  // Locale resolution via Accept-Language header
  // ===================================================================

  @Test
  @Order(35)
  @DisplayName("should resolve locale from Accept-Language header")
  void localeFromAcceptLanguageHeader() {
    String docId = createEntryReturningId(Map.of(
        "title", "Locale Header Test", "body", "Should resolve locale from header"));

    // Read without explicit locale query param — should use Accept-Language
    // The default locale is "en", so header "en" should resolve successfully
    given()
        .contentType("application/json")
        .header("Accept-Language", "en")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.locale", is("en"));

    // Clean up
    given().when().delete("/api/" + CT_SINGULAR + "/" + docId).then().statusCode(200);
  }

  @Test
  @Order(36)
  @DisplayName("should create entry with locale from query param")
  void createWithFrenchLocale() {
    Map<String, Object> body = Map.of("title", "Bonjour", "priority", 1);

    given()
        .contentType("application/json")
        .queryParam("locale", "fr")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.locale", is("fr"))
        .body("data.title", is("Bonjour"));
  }

  // ===================================================================
  // Query errors and edge cases
  // ===================================================================

  @Test
  @Order(40)
  @DisplayName("should handle pagination gracefully with invalid values")
  void emptyPagination() {
    // The API returns 400 for empty pagination values — verify it handles
    // gracefully (doesn't crash) with a meaningful error
    given()
        .contentType("application/json")
        .queryParam("pagination[page]", "")
        .queryParam("pagination[pageSize]", "")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(anyOf(is(200), is(400)));
  }

  @Test
  @Order(41)
  @DisplayName("should accept publicationState=preview without error")
  void publicationStatePreview() {
    given()
        .contentType("application/json")
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  // ===================================================================
  // Schema Cleanup
  // ===================================================================

  @Test
  @Order(50)
  @DisplayName("should delete the content type")
  void deleteContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + CT_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true))
        .body("uid", is(CT_UID));

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
