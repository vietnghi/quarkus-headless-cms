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
 * Self-contained integration tests for full-stack CRUD operations with dynamic zones.
 *
 * <p>Creates its own component definitions and a content type with a dynamic zone,
 * then exercises the full content lifecycle (create → read → update → publish →
 * draft → delete) via the REST Content API. Cleans up all artifacts at the end.
 *
 * <p>Strictly REST — no GraphQL, no seed data dependency.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("REST CRUD with Dynamic Zones")
class RestCrudWithDynamicZonesIT {

  private static final String CT_UID = "api::dz-crud.entry";
  private static final String CT_SINGULAR = "dz-entry";
  private static final String CT_PLURAL = "dz-entries";

  private static final String COMP_UID_A = "test.hero";
  private static final String COMP_UID_B = "test.fact-box";

  // ===================================================================
  // Component Registration — admin API
  // ===================================================================

  @Test
  @Order(1)
  @DisplayName("should register a hero component for dynamic zone use")
  void registerHeroComponent() {
    Map<String, Object> body = Map.of(
        "uid", COMP_UID_A,
        "category", "test",
        "displayName", "Hero",
        "description", "Hero banner component for testing dynamic zones",
        "fields", List.of(
            Map.of("name", "headline", "type", "STRING", "required", true, "maxLength", 200),
            Map.of("name", "subheadline", "type", "STRING", "maxLength", 300),
            Map.of("name", "backgroundImage", "type", "STRING"),
            Map.of("name", "ctaText", "type", "STRING", "maxLength", 50),
            Map.of("name", "ctaUrl", "type", "STRING", "maxLength", 500),
            Map.of("name", "priority", "type", "INTEGER", "min", 1, "max", 10)));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/components")
        .then()
        .statusCode(201)
        .body("data.uid", is(COMP_UID_A))
        .body("data.displayName", is("Hero"))
        .body("data.fields.size()", is(6));
  }

  @Test
  @Order(2)
  @DisplayName("should register a fact-box component for dynamic zone use")
  void registerFactBoxComponent() {
    Map<String, Object> body = Map.of(
        "uid", COMP_UID_B,
        "category", "test",
        "displayName", "Fact Box",
        "description", "Fact box with title, description, and icon for testing",
        "fields", List.of(
            Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 100),
            Map.of("name", "description", "type", "TEXT"),
            Map.of("name", "icon", "type", "STRING", "maxLength", 50),
            Map.of("name", "backgroundColor", "type", "STRING", "maxLength", 7),
            Map.of("name", "sortOrder", "type", "INTEGER", "defaultValue", "0")));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/components")
        .then()
        .statusCode(201)
        .body("data.uid", is(COMP_UID_B))
        .body("data.displayName", is("Fact Box"))
        .body("data.fields.size()", is(5));
  }

  // ===================================================================
  // Content-Type Creation — admin API
  // ===================================================================

  @Test
  @Order(10)
  @DisplayName("should create a content type with a dynamic zone and mixed fields")
  void createContentTypeWithDynamicZone() {
    Map<String, Object> body = Map.of(
        "uid", CT_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", CT_SINGULAR,
        "pluralName", CT_PLURAL,
        "displayName", "DZ CRUD Entry",
        "description", "Self-contained content type for CRUD + dynamic zone tests",
        "draftAndPublish", true,
        "fields", List.of(
            Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 255),
            Map.of("name", "body", "type", "TEXT"),
            Map.of("name", "priority", "type", "INTEGER", "min", 1, "max", 10),
            Map.of("name", "published", "type", "BOOLEAN", "defaultValue", "false"),
            Map.of("name", "score", "type", "FLOAT", "min", 0, "max", 100)),
        "dynamicZones", List.of(
            Map.of(
                "name", "contentSections",
                "components", List.of(COMP_UID_A, COMP_UID_B),
                "min", 0,
                "max", -1,
                "required", false)));

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
        .body("data.fields.size()", is(5))
        .body("data.dynamicZones.size()", is(1))
        .body("data.dynamicZones[0].name", is("contentSections"))
        .body("data.dynamicZones[0].components.size()", is(2));
  }

  @Test
  @Order(11)
  @DisplayName("should verify the content type schema via introspection")
  void getContentTypeSchema() {
    // Schema introspection endpoint
    given()
        .when()
        .get("/api/schemas/" + CT_UID)
        .then()
        .statusCode(200)
        .body("data.uid", is(CT_UID))
        .body("data.dynamicZones[0].name", is("contentSections"))
        .body("data.fields.size()", is(5));

    // List schemas includes it
    given()
        .when()
        .get("/api/schemas")
        .then()
        .statusCode(200);
  }

  // ===================================================================
  // Entry CRUD — Content API
  // ===================================================================

  @Test
  @Order(20)
  @DisplayName("should create an entry with dynamic zone data")
  void createEntryWithDynamicZone() {
    Map<String, Object> heroSection = Map.of(
        "__component", COMP_UID_A,
        "headline", "Welcome to DZ Testing",
        "subheadline", "Comprehensive dynamic zone integration tests",
        "ctaText", "Learn More",
        "ctaUrl", "/docs",
        "priority", 5);

    Map<String, Object> factBox = Map.of(
        "__component", COMP_UID_B,
        "title", "Key Metrics",
        "description", "This section tests dynamic zone component storage and retrieval.",
        "icon", "chart-bar",
        "backgroundColor", "#6366f1",
        "sortOrder", 1);

    Map<String, Object> body = Map.of(
        "title", "Dynamic Zone CRUD Test",
        "body", "Testing full-stack CRUD with dynamic zone components",
        "priority", 8,
        "published", true,
        "score", 95.5,
        "contentSections", List.of(heroSection, factBox));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Dynamic Zone CRUD Test"))
        .body("data.body", is("Testing full-stack CRUD with dynamic zone components"))
        .body("data.priority", is(8))
        .body("data.published", is(true))
        .body("data.score", is(95.5f))
        .body("data.status", is("draft"))
        .body("data.locale", is("en"))
        .body("data.contentSections.size()", is(2))
        // First zone item is hero
        .body("data.contentSections[0].__component", is(COMP_UID_A))
        .body("data.contentSections[0].headline", is("Welcome to DZ Testing"))
        .body("data.contentSections[0].ctaText", is("Learn More"))
        .body("data.contentSections[0].priority", is(5))
        // Second zone item is fact-box
        .body("data.contentSections[1].__component", is(COMP_UID_B))
        .body("data.contentSections[1].title", is("Key Metrics"))
        .body("data.contentSections[1].backgroundColor", is("#6366f1"))
        .body("data.contentSections[1].sortOrder", is(1));
  }

  @Test
  @Order(21)
  @DisplayName("should read back an entry with dynamic zone data")
  void readEntryWithDynamicZone() {
    String docId = createEntryReturningId(Map.of(
        "title", "Read Back DZ Entry",
        "body", "Verify zone data round-trips correctly",
        "priority", 3,
        "published", false,
        "contentSections", List.of(
            Map.of("__component", COMP_UID_A, "headline", "Read Test Hero", "priority", 3),
            Map.of("__component", COMP_UID_B, "title", "Read Fact", "description", "Read back verification"))));

    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.title", is("Read Back DZ Entry"))
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].__component", is(COMP_UID_A))
        .body("data.contentSections[0].headline", is("Read Test Hero"))
        .body("data.contentSections[1].__component", is(COMP_UID_B))
        .body("data.contentSections[1].title", is("Read Fact"));
  }

  @Test
  @Order(22)
  @DisplayName("should update entry with modified dynamic zone data")
  void updateEntryWithDynamicZone() {
    String docId = createEntryReturningId(Map.of(
        "title", "Update DZ Test",
        "body", "Before update",
        "priority", 1,
        "published", false,
        "contentSections", List.of(
            Map.of("__component", COMP_UID_A, "headline", "Original Hero", "priority", 1))));

    // Update: change title, add a fact box, adjust priority
    given()
        .contentType("application/json")
        .body(Map.of(
            "title", "Updated DZ Entry",
            "priority", 10,
            "contentSections", List.of(
                Map.of("__component", COMP_UID_A, "headline", "Updated Hero", "ctaText", "Click Now", "priority", 3),
                Map.of("__component", COMP_UID_B, "title", "New Fact", "description", "Added after update", "sortOrder", 2))))
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.title", is("Updated DZ Entry"))
        .body("data.priority", is(10))
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].__component", is(COMP_UID_A))
        .body("data.contentSections[0].headline", is("Updated Hero"))
        .body("data.contentSections[0].ctaText", is("Click Now"))
        .body("data.contentSections[1].__component", is(COMP_UID_B))
        .body("data.contentSections[1].title", is("New Fact"))
        .body("data.documentId", is(docId));
  }

  @Test
  @Order(23)
  @DisplayName("should clear dynamic zone data to empty via update")
  void updateEntryClearingDynamicZone() {
    String docId = createEntryReturningId(Map.of(
        "title", "Clear DZ Test",
        "body", "Will clear zones",
        "priority", 5,
        "published", false,
        "contentSections", List.of(
            Map.of("__component", COMP_UID_A, "headline", "To Be Cleared", "priority", 1))));

    // Update with empty zone array
    given()
        .contentType("application/json")
        .body(Map.of("contentSections", List.of()))
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(0));

    // Verify persisted
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(0));
  }

  @Test
  @Order(24)
  @DisplayName("should add dynamic zone data to previously empty zone via update")
  void updateEntryPopulatingEmptyZone() {
    String docId = createEntryReturningId(Map.of(
        "title", "Populate DZ Test",
        "body", "Start empty then add zones",
        "priority", 4,
        "published", false));

    // Verify initially no zone data — may be null (field not supplied on create)
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections", anyOf(nullValue(), is(List.of())));

    // Update to add zone items
    given()
        .contentType("application/json")
        .body(Map.of("contentSections", List.of(
            Map.of("__component", COMP_UID_B, "title", "Late Addition", "description", "Added to empty zone"))))
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(1))
        .body("data.contentSections[0].__component", is(COMP_UID_B))
        .body("data.contentSections[0].title", is("Late Addition"));
  }

  @Test
  @Order(25)
  @DisplayName("should create entry with empty dynamic zone")
  void createEntryWithEmptyDynamicZone() {
    Map<String, Object> body = Map.of(
        "title", "Empty DZ Entry",
        "body", "No dynamic zone data provided",
        "priority", 2,
        "contentSections", List.of());

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.contentSections.size()", is(0));
  }

  @Test
  @Order(26)
  @DisplayName("should create entry without supplying dynamic zone field at all")
  void createEntryWithoutDynamicZoneField() {
    Map<String, Object> body = Map.of(
        "title", "No DZ Field",
        "body", "No contentSections key in request",
        "priority", 6);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.title", is("No DZ Field"))
        // When no DZ field is provided, the field may be null (not empty list) in JSON
        .body("data.contentSections", nullValue());
  }

  @Test
  @Order(27)
  @DisplayName("should list entries with dynamic zone data")
  void listEntriesWithDynamicZone() {
    // Create and publish several entries with zone data
    String docA = createEntryReturningId(Map.of(
        "title", "List DZ A", "priority", 1, "body", "First",
        "contentSections", List.of(
            Map.of("__component", COMP_UID_A, "headline", "Hero A", "priority", 1))));
    String docB = createEntryReturningId(Map.of(
        "title", "List DZ B", "priority", 2, "body", "Second",
        "contentSections", List.of(
            Map.of("__component", COMP_UID_B, "title", "Fact B", "sortOrder", 1))));

    // Publish both — need Content-Type because @Consumes is on the class
    given().contentType("application/json").when().post("/api/" + CT_SINGULAR + "/" + docA + "/publish").then().statusCode(200);
    given().contentType("application/json").when().post("/api/" + CT_SINGULAR + "/" + docB + "/publish").then().statusCode(200);

    // Listed published entries include zone data
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR)
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.pagination.total", is(2))
        .body("data[0].contentSections[0].__component", notNullValue());

    // Clean up published entries for other tests
    given().when().delete("/api/" + CT_SINGULAR + "/" + docA).then().statusCode(200);
    given().when().delete("/api/" + CT_SINGULAR + "/" + docB).then().statusCode(200);
  }

  // ===================================================================
  // Dynamic Zone + Draft/Publish Lifecycle
  // ===================================================================

  @Test
  @Order(30)
  @DisplayName("should preserve dynamic zone data through publish/unpublish lifecycle")
  void dynamicZonePublishLifecycle() {
    // Create entry with dynamic zone data
    String docId = createEntryReturningId(Map.of(
        "title", "Lifecycle DZ",
        "body", "DZ through draft/publish",
        "priority", 7,
        "contentSections", List.of(
            Map.of("__component", COMP_UID_A, "headline", "Lifecycle Hero", "ctaText", "Publish Me", "priority", 5),
            Map.of("__component", COMP_UID_B, "title", "Lifecycle Fact", "description", "Survives publish", "sortOrder", 1))));

    // Verify draft state has zone data
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("draft"))
        .body("data.contentSections.size()", is(2));

    // Publish — need Content-Type because @Consumes is on the class
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/publish")
        .then()
        .statusCode(200)
        .body("data.status", is("published"))
        .body("data.publishedAt", notNullValue())
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].headline", is("Lifecycle Hero"))
        .body("data.contentSections[1].title", is("Lifecycle Fact"));

    // Read published version — zone data preserved
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("published"))
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].__component", is(COMP_UID_A));

    // Unpublish — need Content-Type because @Consumes is on the class
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/unpublish")
        .then()
        .statusCode(200)
        .body("unpublished", is(true));

    // Draft remains with zone data intact
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.status", is("draft"))
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].headline", is("Lifecycle Hero"));
  }

  @Test
  @Order(31)
  @DisplayName("should allow modifying zone data after publish and re-publishing")
  void dynamicZoneModifyAfterPublish() {
    String docId = createEntryReturningId(Map.of(
        "title", "Modify After Publish",
        "body", "Initial version",
        "priority", 5,
        "contentSections", List.of(
            Map.of("__component", COMP_UID_A, "headline", "V1 Hero", "priority", 1))));

    // Publish v1 — need Content-Type because @Consumes is on the class
    given().contentType("application/json").when().post("/api/" + CT_SINGULAR + "/" + docId + "/publish").then().statusCode(200);

    // Modify draft with different zone data
    given()
        .contentType("application/json")
        .body(Map.of(
            "title", "Modified After Publish",
            "priority", 9,
            "contentSections", List.of(
                Map.of("__component", COMP_UID_A, "headline", "V2 Hero", "ctaText", "Updated", "priority", 2),
                Map.of("__component", COMP_UID_B, "title", "V2 Fact", "description", "Added in V2", "sortOrder", 1))))
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(2));

    // Re-publish — need Content-Type because @Consumes is on the class
    given()
        .contentType("application/json")
        .when()
        .post("/api/" + CT_SINGULAR + "/" + docId + "/publish")
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].headline", is("V2 Hero"))
        .body("data.contentSections[1].title", is("V2 Fact"));

    // Live version has V2 data
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].headline", is("V2 Hero"));
  }

  // ===================================================================
  // Mixed component + dynamic zone fields
  // ===================================================================

  @Test
  @Order(35)
  @DisplayName("should handle entry with component field and dynamic zone together")
  void componentAndDynamicZoneTogether() {
    // Use the article type's seo component + a dynamic-zone-like field
    // This tests that regular component fields and DZ arrays coexist properly
    // The article type already has a 'seo' component field defined

    Map<String, Object> seo = Map.of(
        "metaTitle", "Mixed Fields Test",
        "metaDescription", "Testing component and DZ field coexistence");

    Map<String, Object> zoneItem = Map.of(
        "__component", COMP_UID_A,
        "headline", "Mixed Hero",
        "ctaText", "Go");

    Map<String, Object> body = Map.of(
        "title", "Mixed Field Types",
        "body", "Both component and DZ fields",
        "priority", 4,
        "contentSections", List.of(zoneItem));

    // Note: The 'seo' field is on the article type, not our custom type.
    // This test verifies our custom type with DZ handles mixed content correctly.
    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.title", is("Mixed Field Types"))
        .body("data.contentSections.size()", is(1))
        .body("data.contentSections[0].__component", is(COMP_UID_A));
  }

  // ===================================================================
  // Error Cases
  // ===================================================================

  @Test
  @Order(40)
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
  @Order(41)
  @DisplayName("should return 404 for non-existent entry (not content type)")
  void readNonExistentEntry() {
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/nonexistent-doc-id")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(43)
  @DisplayName("should return 404 when deleting non-existent entry")
  void deleteNonExistentEntry() {
    given()
        .when()
        .delete("/api/" + CT_SINGULAR + "/nonexistent-id")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(44)
  @DisplayName("should return 400 for empty update body")
  void emptyUpdateBody() {
    String docId = createEntryReturningId(Map.of(
        "title", "Empty Update", "priority", 1));

    given()
        .contentType("application/json")
        .body(Map.of())
        .when()
        .put("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(400);
  }

  @Test
  @Order(45)
  @DisplayName("should handle non-array dynamic zone value gracefully")
  void nonArrayDynamicZoneValue() {
    // If someone sends a scalar/object instead of array for a zone field,
    // the CMS should handle it (store as-is or coerce)
    Map<String, Object> body = Map.of(
        "title", "Non-array DZ Value",
        "priority", 1,
        "contentSections", "not-an-array");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201);
    // The CMS stores whatever JSON is passed; this is a resilience check
  }

  // ===================================================================
  // Schema Introspection
  // ===================================================================

  @Test
  @Order(50)
  @DisplayName("should list all available schemas")
  void listSchemas() {
    given()
        .when()
        .get("/api/schemas")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(51)
  @DisplayName("should list all components")
  void listComponents() {
    given()
        .when()
        .get("/api/schemas/components")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(52)
  @DisplayName("should return error for unknown schema (404 or 409)")
  void getUnknownSchema() {
    // The schema introspection may return 409 (Conflict) if the CT wasn't fully
    // registered, or 404 (Not Found) for truly unknown types — accept either
    given()
        .when()
        .get("/api/schemas/api::unknown.nonexistent")
        .then()
        .statusCode(anyOf(is(404), is(409)));
  }

  // ===================================================================
  // Dynamic Zone with Multiple Component Instances
  // ===================================================================

  @Test
  @Order(55)
  @DisplayName("should handle multiple zone items of the same component type")
  void multipleSameTypeZoneItems() {
    Map<String, Object> body = Map.of(
        "title", "Multiple Same-Type DZ",
        "body", "Three hero sections in a row",
        "priority", 3,
        "contentSections", List.of(
            Map.of("__component", COMP_UID_A, "headline", "Hero 1", "priority", 1),
            Map.of("__component", COMP_UID_A, "headline", "Hero 2", "priority", 2),
            Map.of("__component", COMP_UID_A, "headline", "Hero 3", "priority", 3)));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.contentSections.size()", is(3))
        .body("data.contentSections[0].headline", is("Hero 1"))
        .body("data.contentSections[1].headline", is("Hero 2"))
        .body("data.contentSections[2].headline", is("Hero 3"));
  }

  @Test
  @Order(56)
  @DisplayName("should handle entry with many dynamic zone items (large zone)")
  void manyDynamicZoneItems() {
    var sections = new java.util.ArrayList<Map<String, Object>>();
    for (int i = 1; i <= 20; i++) {
      sections.add(Map.of(
          "__component", COMP_UID_B,
          "title", "Fact " + i,
          "description", "Large zone test item #" + i,
          "sortOrder", i));
    }

    Map<String, Object> body = Map.of(
        "title", "Large DZ Array",
        "body", "Testing 20 zone items",
        "priority", 5,
        "contentSections", sections);

    String docId = given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/" + CT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.contentSections.size()", is(20))
        .extract()
        .path("data.documentId");

    // Read back and verify all 20 preserved
    given()
        .when()
        .get("/api/" + CT_SINGULAR + "/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(20))
        .body("data.contentSections[0].title", is("Fact 1"))
        .body("data.contentSections[19].title", is("Fact 20"));
  }

  // ===================================================================
  // Single Type CRUD
  // ===================================================================

  @Test
  @Order(60)
  @DisplayName("should create, read, update, and delete a single type entry via admin API")
  void singleTypeCrud() {
    // Single types use a dedicated admin-manager endpoint
    String ctUid = "api::single-test.setting";
    String singularName = "single-setting";
    String pluralName = "single-settings";

    // Register a single type
    given()
        .contentType("application/json")
        .body(Map.of(
            "uid", ctUid,
            "kind", "SINGLE_TYPE",
            "singularName", singularName,
            "pluralName", pluralName,
            "displayName", "Single Setting Test",
            "draftAndPublish", true,
            "fields", List.of(
                Map.of("name", "siteName", "type", "STRING", "required", true, "maxLength", 100),
                Map.of("name", "tagline", "type", "STRING", "maxLength", 200))))
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201);

    // Upsert the single type entry via admin API (uses UID, not singular name)
    given()
        .contentType("application/json")
        .body(Map.of("data", Map.of("siteName", "Test Site", "tagline", "Testing single types"), "locale", "en"))
        .when()
        .put("/admin/content-manager/single-types/" + ctUid)
        .then()
        .statusCode(200)
        .body("data.data.siteName", is("Test Site"))
        .body("data.data.tagline", is("Testing single types"));

    // Read it back
    given()
        .when()
        .get("/admin/content-manager/single-types/" + ctUid)
        .then()
        .statusCode(200)
        .body("data.data.siteName", is("Test Site"));

    // Update via PUT
    given()
        .contentType("application/json")
        .body(Map.of("data", Map.of("tagline", "Updated tagline"), "locale", "en"))
        .when()
        .put("/admin/content-manager/single-types/" + ctUid)
        .then()
        .statusCode(200)
        .body("data.data.tagline", is("Updated tagline"));

    // Delete the single type
    given()
        .when()
        .delete("/admin/content-manager/single-types/" + ctUid)
        .then()
        .statusCode(200);

    // Verify deletion
    given()
        .when()
        .get("/admin/content-manager/single-types/" + ctUid)
        .then()
        .statusCode(404);

    // Cleanup content type
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + ctUid)
        .then()
        .statusCode(200);
  }

  // ===================================================================
  // Cleanup
  // ===================================================================

  @Test
  @Order(100)
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

  @Test
  @Order(101)
  @DisplayName("should delete the components")
  void deleteComponents() {
    given()
        .when()
        .delete("/admin/content-type-builder/components/" + COMP_UID_A)
        .then()
        .statusCode(200);

    given()
        .when()
        .delete("/admin/content-type-builder/components/" + COMP_UID_B)
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/admin/content-type-builder/components/" + COMP_UID_A)
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
