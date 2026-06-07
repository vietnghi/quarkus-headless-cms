package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for dynamic zones in content types.
 *
 * <p>Tests that content entries with dynamic zone data can be created,
 * retrieved, and that zone arrays containing components are properly
 * serialized and deserialized.
 */
@QuarkusTest
@DisplayName("Dynamic Zones")
class DynamicZonesIT {

  // ========================================================================
  // Content with Dynamic Zone-like Arrays
  // ========================================================================

  @Test
  @DisplayName("should create entry with array of nested objects (dynamic zone pattern)")
  void createEntryWithZoneData() {
    Map<String, Object> section1 = Map.of(
        "__component", "shared.media",
        "altText", "Hero image",
        "caption", "Welcome banner");

    Map<String, Object> section2 = Map.of(
        "__component", "shared.seo",
        "metaTitle", "Dynamic Zone Test",
        "metaDescription", "Testing dynamic zones");

    Map<String, Object> body = Map.of(
        "title", "Dynamic Zone Article",
        "slug", "dynamic-zone-article",
        "body", "This article tests dynamic zones.",
        "contentSections", List.of(section1, section2));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Dynamic Zone Article"))
        .body("data.contentSections.size()", is(2))
        .body("data.contentSections[0].__component", is("shared.media"))
        .body("data.contentSections[0].altText", is("Hero image"))
        .body("data.contentSections[1].__component", is("shared.seo"))
        .body("data.contentSections[1].metaTitle", is("Dynamic Zone Test"));
  }

  @Test
  @DisplayName("should read back entry with zone data")
  void readEntryWithZoneData() {
    // First create
    Map<String, Object> zoneItem = Map.of(
        "__component", "shared.media",
        "altText", "Read back test");

    String docId = given()
        .contentType("application/json")
        .body(Map.of(
            "title", "Read Zone Test",
            "slug", "read-zone-test",
            "body", "Content",
            "contentSections", List.of(zoneItem)))
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .extract()
        .path("data.documentId");

    // Read back
    given()
        .when()
        .get("/api/article/" + docId)
        .then()
        .statusCode(200)
        .body("data.contentSections.size()", is(1))
        .body("data.contentSections[0].__component", is("shared.media"))
        .body("data.contentSections[0].altText", is("Read back test"));
  }

  @Test
  @DisplayName("should handle empty zone array")
  void emptyZoneArray() {
    given()
        .contentType("application/json")
        .body(Map.of(
            "title", "Empty Zone",
            "slug", "empty-zone",
            "body", "No zones",
            "contentSections", List.of()))
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.contentSections.size()", is(0));
  }

  // ========================================================================
  // Component Field Integration
  // ========================================================================

  @Test
  @DisplayName("should handle entry with both component and zone fields")
  void componentAndZoneFields() {
    Map<String, Object> seo = Map.of(
        "metaTitle", "Hybrid Test",
        "metaDescription", "Testing both patterns");

    Map<String, Object> zoneItem = Map.of(
        "__component", "shared.media",
        "altText", "Hybrid media");

    given()
        .contentType("application/json")
        .body(Map.of(
            "title", "Hybrid Article",
            "slug", "hybrid-article",
            "body", "Both component and zone",
            "seo", seo,
            "contentSections", List.of(zoneItem)))
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.seo.metaTitle", is("Hybrid Test"))
        .body("data.contentSections[0].__component", is("shared.media"));
  }
}
