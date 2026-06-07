package com.quarkus.cms.example;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the demo application.
 * <p>
 * Tests the full REST API surface of DemoResource, including:
 * <ul>
 *   <li>Content type metadata and discovery</li>
 *   <li>Article CRUD and draft/publish lifecycle</li>
 *   <li>Author and category listing</li>
 *   <li>Single-type access (homepage, settings)</li>
 *   <li>Relation management</li>
 *   <li>Search functionality</li>
 * </ul>
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoResourceTest {

    private static String articleDocId;
    private static String authorDocId;
    private static String categoryDocId;

    // ---------------------------------------------------------------
    // Health & Overview
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void testHealthEndpoint() {
        given()
            .when().get("/demo/health")
            .then()
            .statusCode(200)
            .body("status", is("ok"))
            .body("cms", is("quarkus-headless-cms"))
            .body("contentTypes", greaterThan(0));
    }

    @Test
    @Order(2)
    void testOverview() {
        given()
            .when().get("/demo/overview")
            .then()
            .statusCode(200)
            .body("title", notNullValue())
            .body("contentTypes", notNullValue())
            .body("totalContentTypes", greaterThan(0));
    }

    // ---------------------------------------------------------------
    // Content Type Discovery
    // ---------------------------------------------------------------

    @Test
    @Order(3)
    void testListContentTypes() {
        var response = given()
            .when().get("/demo/content-types")
            .then()
            .statusCode(200);

        // Verify at least the 5 demo content types exist
        var types = response.extract().as(new TypeRef<List<Map<String, Object>>>() {});
        assertTrue(types.size() >= 5);

        // Verify article content type details
        var articleType = types.stream()
            .filter(t -> "api::article.article".equals(t.get("uid")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Article content type not found"));

        assertEquals("COLLECTION_TYPE", articleType.get("kind"));
        assertNotNull(articleType.get("fields"));
        assertNotNull(articleType.get("relations"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) articleType.get("fields");
        assertTrue(fields.size() >= 5); // title, slug, excerpt, body, featured, coverImage, publishedAt
    }

    @Test
    @Order(4)
    void testGetContentTypeByUid() {
        given()
            .when().get("/demo/content-types/api::article.article")
            .then()
            .statusCode(200)
            .body("uid", is("api::article.article"))
            .body("singularName", is("article"))
            .body("pluralName", is("articles"))
            .body("displayName", is("Article"))
            .body("draftAndPublish", is(true));

        // Unknown content type
        given()
            .when().get("/demo/content-types/api::unknown.unknown")
            .then()
            .statusCode(404);
    }

    // ---------------------------------------------------------------
    // Authors
    // ---------------------------------------------------------------

    @Test
    @Order(10)
    void testListAuthors() {
        var response = given()
            .when().get("/demo/authors")
            .then()
            .statusCode(200)
            .body("total", greaterThan(0));

        var data = response.extract().path("data");
        assertNotNull(data);
        assertInstanceOf(List.class, data);
    }

    // ---------------------------------------------------------------
    // Categories
    // ---------------------------------------------------------------

    @Test
    @Order(11)
    void testListCategories() {
        var response = given()
            .when().get("/demo/categories")
            .then()
            .statusCode(200)
            .body("total", greaterThan(0));

        var data = response.extract().path("data");
        assertNotNull(data);
    }

    // ---------------------------------------------------------------
    // Articles — List, Create, Read, Update, Delete
    // ---------------------------------------------------------------

    @Test
    @Order(20)
    void testListArticles() {
        given()
            .when().get("/demo/articles")
            .then()
            .statusCode(200)
            .body("total", greaterThan(0))
            .body("data", hasSize(greaterThan(0)));
    }

    @Test
    @Order(21)
    void testListArticlesWithStatusFilter() {
        given()
            .queryParam("status", "published")
            .when().get("/demo/articles")
            .then()
            .statusCode(200)
            .body("total", greaterThan(0));

        given()
            .queryParam("status", "draft")
            .when().get("/demo/articles")
            .then()
            .statusCode(200)
            .body("total", greaterThan(0));
    }

    @Test
    @Order(22)
    void testCreateArticle() {
        Map<String, Object> request = Map.of(
            "title", "Integration Test Article",
            "slug", "integration-test-article",
            "excerpt", "Created during integration test",
            "body", "## Test\n\nThis article was created by a test.",
            "featured", false
        );

        var response = given()
            .contentType("application/json")
            .body(Map.of("data", request))
            .when().post("/demo/articles")
            .then()
            .statusCode(201)
            .body("message", containsString("created"))
            .body("documentId", notNullValue());

        articleDocId = response.extract().path("documentId");
        assertNotNull(articleDocId);
    }

    @Test
    @Order(23)
    void testGetArticle() {
        assertNotNull(articleDocId, "articleDocId should be set by create test");

        given()
            .when().get("/demo/articles/" + articleDocId)
            .then()
            .statusCode(200)
            .body("documentId", is(articleDocId))
            .body("contentType", is("api::article.article"))
            .body("status", is("draft"))
            .body("data.title", is("Integration Test Article"));
    }

    @Test
    @Order(24)
    void testUpdateArticle() {
        assertNotNull(articleDocId);

        Map<String, Object> update = Map.of(
            "title", "Updated Integration Test Article",
            "excerpt", "Updated excerpt"
        );

        given()
            .contentType("application/json")
            .body(Map.of("data", update))
            .when().put("/demo/articles/" + articleDocId)
            .then()
            .statusCode(200)
            .body("message", containsString("updated"))
            .body("entry.data.title", is("Updated Integration Test Article"))
            .body("entry.data.excerpt", is("Updated excerpt"));
    }

    // ---------------------------------------------------------------
    // Draft/Publish Lifecycle
    // ---------------------------------------------------------------

    @Test
    @Order(30)
    void testPublishArticle() {
        assertNotNull(articleDocId);

        given()
            .contentType("application/json")
            .when().post("/demo/articles/" + articleDocId + "/publish")
            .then()
            .statusCode(200)
            .body("message", containsString("published"))
            .body("versionNumber", greaterThan(0))
            .body("publishedAt", notNullValue());
    }

    @Test
    @Order(31)
    void testGetPublishedArticle() {
        assertNotNull(articleDocId);

        // After publish, the entry should have status=published
        // (getEntry returns draft if exists, else published)
        given()
            .when().get("/demo/articles/" + articleDocId)
            .then()
            .statusCode(200)
            .body("status", anyOf(is("published"), is("draft")));
    }

    @Test
    @Order(32)
    void testGetVersions() {
        assertNotNull(articleDocId);

        given()
            .when().get("/demo/articles/" + articleDocId + "/versions")
            .then()
            .statusCode(200)
            .body("documentId", is(articleDocId))
            .body("versions", hasSize(greaterThan(0)));
    }

    @Test
    @Order(33)
    void testUnpublishArticle() {
        assertNotNull(articleDocId);

        given()
            .contentType("application/json")
            .when().post("/demo/articles/" + articleDocId + "/unpublish")
            .then()
            .statusCode(200)
            .body("message", containsString("unpublished"));
    }

    @Test
    @Order(34)
    void testDiscardDraft() {
        assertNotNull(articleDocId);

        given()
            .contentType("application/json")
            .when().post("/demo/articles/" + articleDocId + "/discard-draft")
            .then()
            .statusCode(200)
            .body("message", containsString("discarded"));
    }

    // ---------------------------------------------------------------
    // Single Types
    // ---------------------------------------------------------------

    @Test
    @Order(40)
    void testGetHomepage() {
        given()
            .when().get("/demo/homepage")
            .then()
            .statusCode(200)
            .body("contentType", is("api::homepage.homepage"))
            .body("data.heroTitle", notNullValue());
    }

    @Test
    @Order(41)
    void testGetSettings() {
        given()
            .when().get("/demo/settings")
            .then()
            .statusCode(200)
            .body("contentType", is("api::global.global"))
            .body("data.siteName", notNullValue());
    }

    // ---------------------------------------------------------------
    // Relations
    // ---------------------------------------------------------------

    @Test
    @Order(50)
    void testAttachAndReadRelations() {
        assertNotNull(articleDocId);

        // Get first author
        var authors = given()
            .when().get("/demo/authors")
            .then().statusCode(200)
            .extract().path("data");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> authorList = (List<Map<String, Object>>) authors;
        if (!authorList.isEmpty()) {
            authorDocId = (String) authorList.get(0).get("documentId");

            // Attach author relation
            given()
                .contentType("application/json")
                .body(Map.of(
                    "fieldName", "author",
                    "targetDocumentId", authorDocId,
                    "targetType", "api::author.author"
                ))
                .when().post("/demo/articles/" + articleDocId + "/relations")
                .then()
                .statusCode(200)
                .body("message", containsString("attached"));

            // Read relations
            given()
                .when().get("/demo/articles/" + articleDocId + "/relations")
                .then()
                .statusCode(200)
                .body("author", notNullValue())
                .body("categories", notNullValue());
        }
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    @Test
    @Order(60)
    void testSearch() {
        given()
            .queryParam("q", "Quarkus")
            .when().get("/demo/search")
            .then()
            .statusCode(200)
            .body("query", is("Quarkus"))
            .body("resultCount", greaterThan(0))
            .body("results", hasSize(greaterThan(0)));
    }

    @Test
    @Order(61)
    void testSearchEmptyQuery() {
        given()
            .when().get("/demo/search")
            .then()
            .statusCode(200)
            .body("results", hasSize(0));
    }

    // ---------------------------------------------------------------
    // Delete Article (final cleanup)
    // ---------------------------------------------------------------

    @Test
    @Order(100)
    void testDeleteArticle() {
        assertNotNull(articleDocId);

        given()
            .when().delete("/demo/articles/" + articleDocId)
            .then()
            .statusCode(200)
            .body("message", containsString("deleted"));

        // Verify it's gone
        given()
            .when().get("/demo/articles/" + articleDocId)
            .then()
            .statusCode(404);
    }

    @Test
    @Order(101)
    void testArticleNotFound() {
        given()
            .when().get("/demo/articles/nonexistent-id-12345")
            .then()
            .statusCode(404);
    }
}
