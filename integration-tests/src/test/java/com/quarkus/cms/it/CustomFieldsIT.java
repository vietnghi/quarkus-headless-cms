package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for the Custom Fields API.
 *
 * <p>Covers field type listing, definition CRUD, and validation endpoints.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Custom Fields API")
class CustomFieldsIT {

  private static Long createdDefinitionId;

  // ========================================================================
  // Field Type Listing
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should list all registered field types")
  void listFieldTypes() {
    given()
        .when()
        .get("/admin/custom-fields/types")
        .then()
        .statusCode(200)
        .body("data.size()", greaterThanOrEqualTo(10));
  }

  @Test
  @Order(2)
  @DisplayName("should get a specific field type")
  void getFieldType() {
    given()
        .when()
        .get("/admin/custom-fields/types/string")
        .then()
        .statusCode(200)
        .body("data.typeId", is("string"))
        .body("data.displayName", notNullValue());
  }

  @Test
  @Order(3)
  @DisplayName("should return 404 for unknown field type")
  void getUnknownFieldType() {
    given()
        .when()
        .get("/admin/custom-fields/types/unknown-type-xyz")
        .then()
        .statusCode(404);
  }

  // ========================================================================
  // Custom Field Definition CRUD
  // ========================================================================

  @Test
  @Order(4)
  @DisplayName("should create a custom field definition")
  void createDefinition() {
    Map<String, Object> body = Map.of(
        "contentType", "api::article.article",
        "fieldName", "custom_seo_keywords",
        "label", "SEO Keywords",
        "fieldType", "string",
        "required", true,
        "description", "Custom SEO keywords field",
        "sortOrder", 100);

    createdDefinitionId = given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/custom-fields/definitions")
        .then()
        .statusCode(201)
        .body("data.id", notNullValue())
        .body("data.fieldName", is("custom_seo_keywords"))
        .body("data.label", is("SEO Keywords"))
        .extract()
        .path("data.id");
  }

  @Test
  @Order(5)
  @DisplayName("should list definitions for a content type")
  void listDefinitions() {
    given()
        .queryParam("contentType", "api::article.article")
        .when()
        .get("/admin/custom-fields/definitions")
        .then()
        .statusCode(200)
        .body("data.size()", greaterThanOrEqualTo(1));
  }

  @Test
  @Order(6)
  @DisplayName("should require contentType query param")
  void listDefinitionsNoContentType() {
    given()
        .when()
        .get("/admin/custom-fields/definitions")
        .then()
        .statusCode(400)
        .body("error.message", containsString("contentType"));
  }

  @Test
  @Order(7)
  @DisplayName("should get a single definition")
  void getDefinition() {
    given()
        .when()
        .get("/admin/custom-fields/definitions/" + createdDefinitionId)
        .then()
        .statusCode(200)
        .body("data.id", is(createdDefinitionId.intValue()))
        .body("data.fieldName", is("custom_seo_keywords"));
  }

  @Test
  @Order(8)
  @DisplayName("should update a definition")
  void updateDefinition() {
    Map<String, Object> update = Map.of(
        "label", "Updated SEO Keywords",
        "required", false,
        "sortOrder", 200);

    given()
        .contentType("application/json")
        .body(update)
        .when()
        .put("/admin/custom-fields/definitions/" + createdDefinitionId)
        .then()
        .statusCode(200)
        .body("data.label", is("Updated SEO Keywords"))
        .body("data.required", is(false));
  }

  // ========================================================================
  // Field Validation
  // ========================================================================

  @Test
  @Order(9)
  @DisplayName("should validate a field value successfully")
  void validateValidValue() {
    Map<String, Object> body = Map.of(
        "fieldName", "test_field",
        "fieldType", "string",
        "value", "hello");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/custom-fields/validate")
        .then()
        .statusCode(200)
        .body("valid", is(true));
  }

  @Test
  @Order(10)
  @DisplayName("should reject validation with missing field type")
  void validateMissingFieldType() {
    given()
        .contentType("application/json")
        .body(Map.of("fieldName", "test", "value", "x"))
        .when()
        .post("/admin/custom-fields/validate")
        .then()
        .statusCode(400);
  }

  // ========================================================================
  // Cleanup
  // ========================================================================

  @Test
  @Order(11)
  @DisplayName("should delete a definition")
  void deleteDefinition() {
    given()
        .when()
        .delete("/admin/custom-fields/definitions/" + createdDefinitionId)
        .then()
        .statusCode(200)
        .body("deleted", is(true));

    // Verify it's gone
    given()
        .when()
        .get("/admin/custom-fields/definitions/" + createdDefinitionId)
        .then()
        .statusCode(404);
  }
}
