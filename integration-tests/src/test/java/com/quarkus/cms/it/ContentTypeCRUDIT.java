package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;

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
 * Integration tests for REST CRUD operations across multiple content types.
 *
 * <p>Covers tags (no draft/publish, no localization), categories (localized),
 * authors (with relations), and content type interactions.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Content Type CRUD")
class ContentTypeCRUDIT {

  @BeforeEach
  @Transactional
  void setUp() {
    CmsEntry.deleteAll();
  }

  // ========================================================================
  // Tag CRUD (collection type, no draft/publish, no localization)
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should create a tag via POST /api/tag")
  void createTag() {
    Map<String, Object> body = Map.of(
        "name", "microservices",
        "slug", "microservices",
        "color", "#3b82f6");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/tag")
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.name", is("microservices"))
        .body("data.slug", is("microservices"))
        .body("data.color", is("#3b82f6"));
  }

  @Test
  @Order(2)
  @DisplayName("should read a tag by documentId")
  void readTag() {
    String docId = createReturningId("tag", Map.of("name", "Testing", "slug", "testing"));

    given()
        .when()
        .get("/api/tag/" + docId)
        .then()
        .statusCode(200)
        .body("data.documentId", is(docId))
        .body("data.name", is("Testing"))
        .body("data.slug", is("testing"));
  }

  @Test
  @Order(3)
  @DisplayName("should update a tag")
  void updateTag() {
    String docId = createReturningId("tag", Map.of("name", "Old Name", "slug", "old-name"));

    given()
        .contentType("application/json")
        .body(Map.of("name", "New Name"))
        .when()
        .put("/api/tag/" + docId)
        .then()
        .statusCode(200)
        .body("data.name", is("New Name"))
        .body("data.documentId", is(docId));
  }

  @Test
  @Order(4)
  @DisplayName("should delete a tag")
  void deleteTag() {
    String docId = createReturningId("tag", Map.of("name", "Delete Me", "slug", "delete-me"));

    given()
        .when()
        .delete("/api/tag/" + docId)
        .then()
        .statusCode(200)
        .body("documentId", is(docId))
        .body("deleted", is(true));

    given()
        .when()
        .get("/api/tag/" + docId)
        .then()
        .statusCode(404);
  }

  @Test
  @Order(5)
  @DisplayName("should list all tags with preview status")
  void listTags() {
    createReturningId("tag", Map.of("name", "Tag A", "slug", "tag-a"));
    createReturningId("tag", Map.of("name", "Tag B", "slug", "tag-b"));

    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/tag")
        .then()
        .statusCode(200)
        .body("data.size()", is(2))
        .body("meta.pagination.total", is(2))
        .body("data.name", hasItems("Tag A", "Tag B"));
  }

  // ========================================================================
  // Category CRUD (collection type, localized, no draft/publish)
  // ========================================================================

  @Test
  @Order(6)
  @DisplayName("should create a category")
  void createCategory() {
    Map<String, Object> body = Map.of(
        "name", "News",
        "slug", "news",
        "description", "Latest news and updates",
        "color", "#ef4444",
        "icon", "newspaper",
        "sortOrder", 10);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/category")
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.name", is("News"))
        .body("data.slug", is("news"))
        .body("data.description", is("Latest news and updates"))
        .body("data.color", is("#ef4444"))
        .body("data.sortOrder", is(10));
  }

  @Test
  @Order(7)
  @DisplayName("should create a category with locale parameter")
  void createCategoryLocalized() {
    Map<String, Object> body = Map.of(
        "name", "Actualités",
        "slug", "actualites",
        "description", "Les dernières actualités");

    given()
        .contentType("application/json")
        .queryParam("locale", "fr")
        .body(body)
        .when()
        .post("/api/category")
        .then()
        .statusCode(201)
        .body("data.locale", is("fr"))
        .body("data.name", is("Actualités"))
        .body("data.slug", is("actualites"));
  }

  @Test
  @Order(8)
  @DisplayName("should filter categories by locale")
  void filterCategoriesByLocale() {
    // Create English category
    String enDocId = createReturningIdWithLocale("category",
        Map.of("name", "Technology", "slug", "technology"), "en");
    // Create French variant
    createReturningIdWithLocale("category",
        Map.of("name", "Technologie", "slug", "technologie"), "fr");

    // Filter by English locale
    given()
        .queryParam("locale", "en")
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/category")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("data[0].name", is("Technology"));

    // Filter by French locale
    given()
        .queryParam("locale", "fr")
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/category")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("data[0].name", is("Technologie"));
  }

  // ========================================================================
  // Author CRUD (collection type with JSON fields)
  // ========================================================================

  @Test
  @Order(9)
  @DisplayName("should create an author with social links")
  void createAuthorWithSocialLinks() {
    Map<String, Object> socialLinks = Map.of(
        "twitter", "@janedoe",
        "github", "janedoe");

    Map<String, Object> body = Map.of(
        "name", "Jane Doe",
        "slug", "jane-doe",
        "email", "jane@example.com",
        "jobTitle", "Developer Advocate",
        "bio", "Jane is a developer advocate specializing in CMS platforms.",
        "featuredAuthor", true,
        "socialLinks", socialLinks);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/author")
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.name", is("Jane Doe"))
        .body("data.slug", is("jane-doe"))
        .body("data.email", is("jane@example.com"))
        .body("data.featuredAuthor", is(true))
        .body("data.socialLinks.twitter", is("@janedoe"))
        .body("data.socialLinks.github", is("janedoe"));
  }

  // ========================================================================
  // Article CRUD (collection type with draft/publish)
  // ========================================================================

  @Test
  @Order(10)
  @DisplayName("should create an article and publish it")
  void createAndPublishArticle() {
    Map<String, Object> body = Map.of(
        "title", "Getting Started with Quarkus",
        "slug", "getting-started-quarkus",
        "subtitle", "A beginner's guide",
        "body", "Quarkus is a Kubernetes-native Java stack...",
        "featured", true,
        "readingTime", 5);

    String docId = given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.status", is("draft"))
        .extract()
        .path("data.documentId");

    // Publish
    given()
        .when()
        .post("/api/article/" + docId + "/publish")
        .then()
        .statusCode(200)
        .body("data.status", is("published"))
        .body("data.publishedAt", notNullValue());

    // Verify it's visible via published endpoint
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("data[0].documentId", is(docId));
  }

  @Test
  @Order(11)
  @DisplayName("should filter articles by published status")
  void filterArticlesByStatus() {
    String draftId = createReturningId("article",
        Map.of("title", "Draft Article", "slug", "draft-article"));
    String pubId = createReturningId("article",
        Map.of("title", "Published Article", "slug", "published-article"));

    // Publish one
    given().when().post("/api/article/" + pubId + "/publish").then().statusCode(200);

    // Only draft
    given()
        .queryParam("publicationState", "preview")
        .queryParam("status", "draft")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("data[0].title", is("Draft Article"));

    // Only published
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(1))
        .body("data[0].title", is("Published Article"));
  }

  @Test
  @Order(12)
  @DisplayName("should unpublish an article and keep draft")
  void unpublishArticle() {
    String docId = createReturningId("article",
        Map.of("title", "Temp Article", "slug", "temp-article"));
    given().when().post("/api/article/" + docId + "/publish").then().statusCode(200);

    // Unpublish
    given()
        .when()
        .post("/api/article/" + docId + "/unpublish")
        .then()
        .statusCode(200)
        .body("unpublished", is(true));

    // Draft should still exist
    given()
        .queryParam("publicationState", "preview")
        .queryParam("status", "draft")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", greaterThan(0));

    // Published should be empty
    given()
        .queryParam("publicationState", "live")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(0));
  }

  // ========================================================================
  // Single-Type CRUD
  // ========================================================================

  @Test
  @Order(13)
  @DisplayName("should create and read a single-type entry")
  void createAndReadSingleType() {
    Map<String, Object> body = Map.of(
        "heroTitle", "Welcome",
        "heroSubtitle", "Welcome to our site",
        "heroCtaText", "Learn More",
        "heroCtaUrl", "/about");

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/homepage")
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.heroTitle", is("Welcome"));

    // Read back
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/homepage")
        .then()
        .statusCode(200)
        .body("data.heroTitle", is("Welcome"))
        .body("data.heroSubtitle", is("Welcome to our site"));
  }

  @Test
  @Order(14)
  @DisplayName("should update single-type entry")
  void updateSingleType() {
    // Create
    String docId = given()
        .contentType("application/json")
        .body(Map.of("siteName", "My Site", "siteDescription", "A great site"))
        .when()
        .post("/api/global")
        .then()
        .statusCode(201)
        .extract()
        .path("data.documentId");

    // Update
    given()
        .contentType("application/json")
        .body(Map.of("siteName", "Updated Site"))
        .when()
        .put("/api/global/" + docId)
        .then()
        .statusCode(200)
        .body("data.siteName", is("Updated Site"));
  }

  @Test
  @Order(15)
  @DisplayName("should create article with component field")
  void createArticleWithComponent() {
    Map<String, Object> seo = Map.of(
        "metaTitle", "SEO Title",
        "metaDescription", "SEO Description",
        "noIndex", false);

    Map<String, Object> body = Map.of(
        "title", "Article with SEO",
        "slug", "article-with-seo",
        "body", "Content here",
        "seo", seo);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/article")
        .then()
        .statusCode(201)
        .body("data.seo.metaTitle", is("SEO Title"))
        .body("data.seo.metaDescription", is("SEO Description"))
        .body("data.seo.noIndex", is(false));
  }

  // ========================================================================
  // Error Cases
  // ========================================================================

  @Test
  @Order(16)
  @DisplayName("should return 404 for non-existent content type")
  void nonExistentContentType() {
    given()
        .when()
        .get("/api/nonexistent")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(17)
  @DisplayName("should return 404 for non-existent documentId")
  void nonExistentDocumentId() {
    given()
        .when()
        .get("/api/article/nonexistent-doc-id")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(18)
  @DisplayName("should return 400 for empty create body")
  void emptyCreateBody() {
    given()
        .contentType("application/json")
        .body(Map.of())
        .when()
        .post("/api/article")
        .then()
        .statusCode(400);
  }

  // ========================================================================
  // Helper Methods
  // ========================================================================

  /** Creates an entry and returns its documentId. */
  private String createReturningId(String contentType, Map<String, Object> data) {
    return given()
        .contentType("application/json")
        .body(data)
        .when()
        .post("/api/" + contentType)
        .then()
        .statusCode(201)
        .extract()
        .path("data.documentId");
  }

  /** Creates an entry with locale and returns its documentId. */
  private String createReturningIdWithLocale(String contentType, Map<String, Object> data, String locale) {
    return given()
        .contentType("application/json")
        .queryParam("locale", locale)
        .body(data)
        .when()
        .post("/api/" + contentType)
        .then()
        .statusCode(201)
        .extract()
        .path("data.documentId");
  }
}
