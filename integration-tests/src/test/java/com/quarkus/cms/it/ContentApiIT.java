package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import com.quarkus.cms.core.domain.CmsEntry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** Integration tests for the REST Content API endpoints. */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Content API")
class ContentApiIT {

  @BeforeEach
  @Transactional
  void setUp() {
    CmsEntry.deleteAll();
  }

  @Test
  @Order(1)
  @DisplayName("should create an entry via POST /api/{contentType}")
  void createEntry() {
    Map<String, Object> body = Map.of("title", "Test Article");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Test Article"))
        .body("data.status", is("draft"))
        .body("data.locale", is("en"));
  }

  @Test
  @Order(2)
  @DisplayName("should create entry with locale")
  void createEntryWithLocale() {
    Map<String, Object> body = Map.of("title", "Bonjour");

    given()
        .contentType("application/json")
        .queryParam("locale", "fr")
        .body(body)
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.locale", is("fr"))
        .body("data.title", is("Bonjour"));
  }

  @Test
  @Order(3)
  @DisplayName("should list published entries via GET /api/{contentType}")
  void listPublishedEntries() {
    // Create and publish
    Map<String, Object> body = Map.of("title", "Published Article");
    String docId =
        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/article")
            .then()
            .statusCode(201)
            .extract()
            .path("data.documentId");

    given().when().post("/api/article/" + docId + "/publish").then().statusCode(200);

    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("meta.pagination.total", is(1))
        .body("data[0].title", is("Published Article"));
  }

  @Test
  @Order(4)
  @DisplayName("should find one entry via GET /api/{contentType}/{documentId}")
  void findOneEntry() {
    Map<String, Object> body = Map.of("title", "Find Me");
    String docId =
        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/article")
            .then()
            .statusCode(201)
            .extract()
            .path("data.documentId");

    given().when().post("/api/article/" + docId + "/publish").then().statusCode(200);

    given()
        .when()
        .get("/api/article/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.title", is("Find Me"));
  }

  @Test
  @Order(5)
  @DisplayName("should return 404 for missing entry")
  void findOneNotFound() {
    given()
        .when()
        .get("/api/article/nonexistent-id")
        .then()
        .statusCode(404)
        .body("error.status", is(404))
        .body("error.name", is("NotFoundError"));
  }

  @Test
  @Order(6)
  @DisplayName("should update an entry via PUT /api/{contentType}/{documentId}")
  void updateEntry() {
    Map<String, Object> body = Map.of("title", "Original");
    String docId =
        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/article")
            .then()
            .statusCode(201)
            .extract()
            .path("data.documentId");

    given()
        .contentType("application/json")
        .body(Map.of("title", "Updated"))
        .when()
        .put("/api/article/" + docId)
        .then()
        .statusCode(200)
        .body("data.title", is("Updated"));
  }

  @Test
  @Order(7)
  @DisplayName("should delete an entry via DELETE /api/{contentType}/{documentId}")
  void deleteEntry() {
    Map<String, Object> body = Map.of("title", "To Delete");
    String docId =
        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/article")
            .then()
            .statusCode(201)
            .extract()
            .path("data.documentId");

    given()
        .when()
        .delete("/api/article/" + docId)
        .then()
        .statusCode(200)
        .body("documentId", is(docId))
        .body("deleted", is(true));

    given()
        .when()
        .get("/api/article/" + docId)
        .then()
        .statusCode(404);
  }

  @Test
  @Order(8)
  @DisplayName("should publish a draft entry via POST /publish")
  void publishEntry() {
    Map<String, Object> body = Map.of("title", "Draft Article");
    String docId =
        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/article")
            .then()
            .statusCode(201)
            .extract()
            .path("data.documentId");

    given()
        .when()
        .post("/api/article/" + docId + "/publish")
        .then()
        .statusCode(200)
        .body("data.status", is("published"))
        .body("data.publishedAt", notNullValue());
  }

  @Test
  @Order(9)
  @DisplayName("should unpublish an entry via POST /unpublish")
  void unpublishEntry() {
    Map<String, Object> body = Map.of("title", "Unpublish Me");
    String docId =
        given()
            .contentType("application/json")
            .body(body)
            .when()
            .post("/api/article")
            .then()
            .statusCode(201)
            .extract()
            .path("data.documentId");

    given().when().post("/api/article/" + docId + "/publish").then().statusCode(200);

    given()
        .when()
        .post("/api/article/" + docId + "/unpublish")
        .then()
        .statusCode(200)
        .body("unpublished", is(true));
  }

  @Test
  @Order(10)
  @DisplayName("should filter entries by field")
  void filterEntries() {
    Map<String, Object> body = Map.of("title", "Alpha", "category", "A");
    String docA = given().contentType("application/json").body(body).when()
        .post("/api/article").then().statusCode(201).extract().path("data.documentId");

    body = Map.of("title", "Beta", "category", "B");
    String docB = given().contentType("application/json").body(body).when()
        .post("/api/article").then().statusCode(201).extract().path("data.documentId");

    given().when().post("/api/article/" + docA + "/publish").then().statusCode(200);
    given().when().post("/api/article/" + docB + "/publish").then().statusCode(200);

    given()
        .queryParam("filters[category][$eq]", "B")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("data[0].category", is("B"));
  }

  @Test
  @Order(11)
  @DisplayName("should sort entries")
  void sortEntries() {
    Map<String, Object> body = Map.of("title", "Zebra");
    String doc1 = given().contentType("application/json").body(body).when()
        .post("/api/article").then().statusCode(201).extract().path("data.documentId");
    body = Map.of("title", "Alpha");
    String doc2 = given().contentType("application/json").body(body).when()
        .post("/api/article").then().statusCode(201).extract().path("data.documentId");

    given().when().post("/api/article/" + doc1 + "/publish").then().statusCode(200);
    given().when().post("/api/article/" + doc2 + "/publish").then().statusCode(200);

    given()
        .queryParam("sort", "title:asc")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("data[0].title", is("Alpha"))
        .body("data[1].title", is("Zebra"));
  }

  @Test
  @Order(12)
  @DisplayName("should paginate results")
  void paginateEntries() {
    for (int i = 1; i <= 5; i++) {
      String docId = given().contentType("application/json")
          .body(Map.of("title", "Entry " + i)).when()
          .post("/api/article").then().statusCode(201).extract().path("data.documentId");
      given().when().post("/api/article/" + docId + "/publish").then().statusCode(200);
    }

    given()
        .queryParam("pagination[page]", "1")
        .queryParam("pagination[pageSize]", "2")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.pagination.page", is(1))
        .body("meta.pagination.total", is(5))
        .body("meta.pagination.pageCount", is(3));
  }

  @Test
  @Order(13)
  @DisplayName("should accept populate=* without error")
  void populateAll() {
    given()
        .queryParam("populate", "*")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200);
  }

  @Test
  @Order(14)
  @DisplayName("should accept populate with field name without error")
  void populateField() {
    given()
        .queryParam("populate", "author")
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200);
  }

  @Test
  @Order(15)
  @DisplayName("should accept populate=* on single entry without error")
  void populateSingleEntry() {
    Map<String, Object> body = Map.of("title", "Populatable");
    String docId = given().contentType("application/json").body(body).when()
        .post("/api/article").then().statusCode(201)
        .extract().path("data.documentId");

    given().when().post("/api/article/" + docId + "/publish").then().statusCode(200);

    given()
        .queryParam("populate", "*")
        .when()
        .get("/api/article/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId));
  }
}
