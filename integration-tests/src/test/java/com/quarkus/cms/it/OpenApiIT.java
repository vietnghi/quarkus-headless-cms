package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.CoreMatchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests verifying the OpenAPI/Swagger documentation generation.
 *
 * <p>Ensures that:
 * <ul>
 *   <li>The OpenAPI endpoint returns a valid JSON schema at {@code /openapi}</li>
 *   <li>All expected API paths, tags, operation summaries, and response schemas are present</li>
 *   <li>DTO model schemas are generated from {@code @Schema} annotations</li>
 *   <li>Swagger UI is served at {@code /swagger-ui}</li>
 * </ul>
 */
@QuarkusTest
@Disabled("REST Assured 5.x Groovy API incompatibility — needs migration to JsonPath API")
@DisplayName("OpenAPI Documentation")
class OpenApiIT {

  @Test
  @DisplayName("should serve valid OpenAPI JSON at /openapi")
  void openApiEndpointReturnsValidJson() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("openapi", notNullValue())
        .body("info.title", equalTo("Quarkus Headless CMS API"))
        .body("info.version", equalTo("1.0.0-SNAPSHOT"))
        .body("info.description", notNullValue());
  }

  @Test
  @DisplayName("should contain all expected API tags")
  void openApiContainsExpectedTags() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .body("tags.name", hasItem("Content API"))
        .body("tags.name", hasItem("Bulk Operations"))
        .body("tags.name", hasItem("Relations API"))
        .body("tags.name", hasItem("Schema Introspection"));
  }

  @Test
  @DisplayName("should document Content API endpoints")
  void openApiDocumentsContentApi() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .body("paths", hasKey("/api/{contentType}"))
        .body("paths./api/{contentType}.get.summary", notNullValue())
        .body("paths./api/{contentType}.get.tags", hasItem("Content API"));
  }

  @Test
  @DisplayName("should document Bulk Operations and Batch endpoints")
  void openApiDocumentsBulkOperations() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .body("paths", hasKey("/api/{contentType}/bulk"))
        .body("paths", hasKey("/api/batch"))
        .body("paths./api/batch.post.tags", hasItem("Bulk Operations"))
        .body("paths./api/{contentType}/bulk.post.tags", hasItem("Bulk Operations"));
  }

  @Test
  @DisplayName("should document Relations API endpoints")
  void openApiDocumentsRelationsApi() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .body("paths", hasKey("/api/{contentType}/{documentId}/relations/{fieldName}"))
        .body("paths./api/{contentType}/{documentId}/relations/{fieldName}.get.tags",
            hasItem("Relations API"));
  }

  @Test
  @DisplayName("should document Schema Introspection endpoints")
  void openApiDocumentsSchemaIntrospection() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .body("paths", hasKey("/api/schemas"))
        .body("paths", hasKey("/api/schemas/{uid}"))
        .body("paths./api/schemas.get.tags", hasItem("Schema Introspection"))
        .body("paths./api/schemas/{uid}.get.tags", hasItem("Schema Introspection"));
  }

  @Test
  @DisplayName("should include DTO schema definitions from @Schema annotations")
  void openApiContainsDtoSchemas() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .body("components.schemas", hasKey("CollectionResponse"))
        .body("components.schemas", hasKey("SingleResponse"))
        .body("components.schemas", hasKey("ErrorResponse"))
        .body("components.schemas", hasKey("PaginationMeta"))
        .body("components.schemas", hasKey("ContentEntry"))
        .body("components.schemas", hasKey("BatchRequest"))
        .body("components.schemas", hasKey("BatchResponse"))
        .body("components.schemas", hasKey("BulkCreateEntry"))
        .body("components.schemas", hasKey("BulkUpdateEntry"))
        .body("components.schemas", hasKey("BulkDeleteRequest"))
        .body("components.schemas", hasKey("BulkOperationResult"))
        .body("components.schemas", hasKey("BulkOperationMeta"));
  }

  @Test
  @DisplayName("should include proper response schema references on Content API operations")
  void openApiContentApiHasResponseSchemas() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        // POST create -> 201 response references SingleResponse
        .body("paths./api/{contentType}.post.responses.201.content.[application/json].schema.$ref",
            notNullValue())
        .body("paths./api/{contentType}.post.responses.201.content.[application/json].schema.$ref",
            equalTo("#/components/schemas/SingleResponse"))
        // GET findMany -> 200 response references CollectionResponse
        .body("paths./api/{contentType}.get.responses.200.content.[application/json].schema.$ref",
            equalTo("#/components/schemas/CollectionResponse"));
  }

  @Test
  @DisplayName("should have parameter descriptions on operations")
  void openApiHasParameterDescriptions() {
    given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        // Verify the first parameter of GET /api/{contentType} has description
        .body("paths./api/{contentType}.get.parameters[0].description", notNullValue())
        .body("paths./api/{contentType}.get.parameters[0].name", equalTo("contentType"))
        .body("paths./api/{contentType}.get.parameters[0].required", equalTo(true));
  }

  @Test
  @DisplayName("should serve Swagger UI at /swagger-ui")
  void swaggerUiEndpointWorks() {
    given()
        .when()
        .get("/swagger-ui")
        .then()
        .statusCode(200)
        .contentType(ContentType.HTML);
  }
}
