package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
@DisplayName("OpenAPI Documentation")
class OpenApiIT {

  @SuppressWarnings("unchecked")
  private JsonPath fetchOpenApi() {
    return given()
        .accept(ContentType.JSON)
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .extract()
        .jsonPath();
  }

  @Test
  @DisplayName("should serve valid OpenAPI JSON at /openapi")
  void openApiEndpointReturnsValidJson() {
    JsonPath jp = fetchOpenApi();
    assertThat(jp.getString("openapi"), notNullValue());
    assertThat(jp.getString("info.title"), equalTo("Quarkus Headless CMS API"));
    assertThat(jp.getString("info.version"), equalTo("1.0.0-SNAPSHOT"));
  }

  @Test
  @DisplayName("should contain all expected API tags")
  void openApiContainsExpectedTags() {
    JsonPath jp = fetchOpenApi();
    List<String> tagNames = jp.getList("tags.name");
    assertThat(tagNames, hasItem("Content API"));
    assertThat(tagNames, hasItem("Bulk Operations"));
    assertThat(tagNames, hasItem("Relations API"));
    assertThat(tagNames, hasItem("Schema Introspection"));
  }

  @Test
  @DisplayName("should document Content API endpoints")
  void openApiDocumentsContentApi() {
    JsonPath jp = fetchOpenApi();
    Map<String, Object> paths = jp.getMap("paths");
    assertThat(paths, hasKey("/api/{contentType}"));
    Map<String, Object> endpoint = (Map<String, Object>) paths.get("/api/{contentType}");
    Map<String, Object> getOp = (Map<String, Object>) endpoint.get("get");
    assertThat(getOp.get("summary"), notNullValue());
    assertThat((List<String>) getOp.get("tags"), hasItem("Content API"));
  }

  @Test
  @DisplayName("should document Bulk Operations and Batch endpoints")
  void openApiDocumentsBulkOperations() {
    JsonPath jp = fetchOpenApi();
    Map<String, Object> paths = jp.getMap("paths");
    assertThat(paths, hasKey("/api/{contentType}/bulk"));
    assertThat(paths, hasKey("/api/batch"));
    Map<String, Object> batchEndpoint = (Map<String, Object>) paths.get("/api/batch");
    Map<String, Object> batchPost = (Map<String, Object>) batchEndpoint.get("post");
    assertThat((List<String>) batchPost.get("tags"), hasItem("Bulk Operations"));
    Map<String, Object> bulkEndpoint = (Map<String, Object>) paths.get("/api/{contentType}/bulk");
    Map<String, Object> bulkPost = (Map<String, Object>) bulkEndpoint.get("post");
    assertThat((List<String>) bulkPost.get("tags"), hasItem("Bulk Operations"));
  }

  @Test
  @DisplayName("should document Relations API endpoints")
  void openApiDocumentsRelationsApi() {
    JsonPath jp = fetchOpenApi();
    Map<String, Object> paths = jp.getMap("paths");
    assertThat(paths, hasKey("/api/{contentType}/{documentId}/relations/{fieldName}"));
    Map<String, Object> endpoint = (Map<String, Object>) paths.get("/api/{contentType}/{documentId}/relations/{fieldName}");
    Map<String, Object> getOp = (Map<String, Object>) endpoint.get("get");
    assertThat((List<String>) getOp.get("tags"), hasItem("Relations API"));
  }

  @Test
  @DisplayName("should document Schema Introspection endpoints")
  void openApiDocumentsSchemaIntrospection() {
    JsonPath jp = fetchOpenApi();
    Map<String, Object> paths = jp.getMap("paths");
    assertThat(paths, hasKey("/api/schemas"));
    assertThat(paths, hasKey("/api/schemas/{uid}"));
    Map<String, Object> schemasEndpoint = (Map<String, Object>) paths.get("/api/schemas");
    Map<String, Object> schemasGet = (Map<String, Object>) schemasEndpoint.get("get");
    assertThat((List<String>) schemasGet.get("tags"), hasItem("Schema Introspection"));
    Map<String, Object> schemasUidEndpoint = (Map<String, Object>) paths.get("/api/schemas/{uid}");
    Map<String, Object> schemasUidGet = (Map<String, Object>) schemasUidEndpoint.get("get");
    assertThat((List<String>) schemasUidGet.get("tags"), hasItem("Schema Introspection"));
  }

  @Test
  @DisplayName("should include DTO schema definitions from @Schema annotations")
  void openApiContainsDtoSchemas() {
    JsonPath jp = fetchOpenApi();
    Map<String, Object> schemas = jp.getMap("components.schemas");
    assertThat(schemas, hasKey("CollectionResponse"));
    assertThat(schemas, hasKey("SingleResponse"));
    assertThat(schemas, hasKey("ErrorResponse"));
    assertThat(schemas, hasKey("PaginationMeta"));
    assertThat(schemas, hasKey("ContentEntry"));
    assertThat(schemas, hasKey("BatchRequest"));
    assertThat(schemas, hasKey("BatchResponse"));
    assertThat(schemas, hasKey("BulkCreateEntry"));
    assertThat(schemas, hasKey("BulkUpdateEntry"));
    assertThat(schemas, hasKey("BulkDeleteRequest"));
    assertThat(schemas, hasKey("BulkOperationResult"));
    assertThat(schemas, hasKey("BulkOperationMeta"));
  }

  @Test
  @DisplayName("should include proper response schema references on Content API operations")
  void openApiContentApiHasResponseSchemas() {
    JsonPath jp = fetchOpenApi();
    Map<String, Object> paths = jp.getMap("paths");
    Map<String, Object> contentTypeEndpoint = (Map<String, Object>) paths.get("/api/{contentType}");
    // POST create -> 201 response references SingleResponse
    Map<String, Object> postOp = (Map<String, Object>) contentTypeEndpoint.get("post");
    Map<String, Object> postResponses = (Map<String, Object>) postOp.get("responses");
    Map<String, Object> post201 = (Map<String, Object>) postResponses.get("201");
    Map<String, Object> post201Content = (Map<String, Object>) post201.get("content");
    Map<String, Object> post201Json = (Map<String, Object>) post201Content.get("application/json");
    Map<String, Object> post201Schema = (Map<String, Object>) post201Json.get("schema");
    assertThat(post201Schema.get("$ref"), notNullValue());
    assertThat(post201Schema.get("$ref"), equalTo("#/components/schemas/SingleResponse"));
    // GET findMany -> 200 response references CollectionResponse
    Map<String, Object> getOp = (Map<String, Object>) contentTypeEndpoint.get("get");
    Map<String, Object> getResponses = (Map<String, Object>) getOp.get("responses");
    Map<String, Object> get200 = (Map<String, Object>) getResponses.get("200");
    Map<String, Object> get200Content = (Map<String, Object>) get200.get("content");
    Map<String, Object> get200Json = (Map<String, Object>) get200Content.get("application/json");
    Map<String, Object> get200Schema = (Map<String, Object>) get200Json.get("schema");
    assertThat(get200Schema.get("$ref"), equalTo("#/components/schemas/CollectionResponse"));
  }

  @Test
  @DisplayName("should have parameter descriptions on operations")
  void openApiHasParameterDescriptions() {
    JsonPath jp = fetchOpenApi();
    Map<String, Object> paths = jp.getMap("paths");
    Map<String, Object> contentTypeEndpoint = (Map<String, Object>) paths.get("/api/{contentType}");
    Map<String, Object> getOp = (Map<String, Object>) contentTypeEndpoint.get("get");
    List<Map<String, Object>> parameters = (List<Map<String, Object>>) getOp.get("parameters");
    assertThat(parameters.get(0).get("description"), notNullValue());
    assertThat(parameters.get(0).get("name"), equalTo("contentType"));
    assertThat(parameters.get(0).get("required"), equalTo(true));
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
