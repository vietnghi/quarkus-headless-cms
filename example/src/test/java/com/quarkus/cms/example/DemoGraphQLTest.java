package com.quarkus.cms.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the demo application's GraphQL API (SmallRye GraphQL via /graphql).
 *
 * <p>Tests content queries against the seed data populated by {@link DemoDataSeeder}:
 * published and draft articles, authors, categories, single types, schema introspection,
 * and component discovery.
 *
 * <p>The {@link Entry} GraphQL type only exposes metadata fields (documentId, contentType,
 * locale, status, timestamps) — content-type-specific fields (title, body, name, etc.)
 * live in the {@code data} map which is {@code @Ignore}d from the schema. Therefore,
 * content queries only verify metadata and pagination, not specific field values.
 *
 * <p>Seed data status: Articles (draftAndPublish=true) are published; Authors, Categories,
 * Homepage, and Global settings (draftAndPublish=false or draft-only) remain as "draft",
 * so entry queries specify the appropriate status parameter.
 */
@QuarkusTest
@TestProfile(GraphQLTestProfile.class)
@DisplayName("Demo GraphQL API")
class DemoGraphQLTest {

  // ========================================================================
  // Content Queries — Published Articles
  // ========================================================================

  @Test
  @DisplayName("should query all published articles with pagination metadata")
  void queryAllPublishedArticles() {
    String query = """
        { entries(contentType: "api::article.article", status: "published") {
            data { documentId contentType status }
            meta { total page pageSize pageCount }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.meta.total", greaterThanOrEqualTo(3))
        .body("data.entries.data", hasSize(greaterThanOrEqualTo(3)))
        .body("data.entries.data[0].documentId", notNullValue())
        .body("data.entries.data[0].contentType", is("api::article.article"))
        .body("data.entries.data[0].status", is("published"));
  }

  @Test
  @DisplayName("should query draft articles")
  void queryDraftArticles() {
    String query = """
        { entries(contentType: "api::article.article", status: "draft") {
            data { documentId contentType status }
            meta { total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.meta.total", greaterThanOrEqualTo(0))
        .body("data.entries.data", notNullValue());
  }

  @Test
  @DisplayName("should query a single article by documentId")
  void querySingleArticle() {
    String lookupQuery = """
        { entries(contentType: "api::article.article", status: "published", pagination: {page: 1, pageSize: 1}) {
            data { documentId contentType }
            meta { total }
          }
        }
        """;

    String documentId = given()
        .contentType("application/json")
        .body(Map.of("query", lookupQuery))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .extract()
        .path("data.entries.data[0].documentId");

    if (documentId == null) return;

    String query = """
        { entry(contentType: "api::article.article", documentId: "%s") {
            documentId contentType status
          }
        }
        """.formatted(documentId);

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entry.documentId", is(documentId))
        .body("data.entry.contentType", is("api::article.article"))
        .body("data.entry.status", anyOf(is("published"), is("draft")));
  }

  @Test
  @DisplayName("should return null for non-existent article documentId")
  void queryNonExistentArticle() {
    String query = """
        { entry(contentType: "api::article.article", documentId: "nonexistent-doc-12345") {
            documentId contentType
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entry", nullValue());
  }

  // ========================================================================
  // Content Queries — Authors (non-draftAndPublish, populated as draft)
  // ========================================================================

  @Test
  @DisplayName("should query all authors (non-draftAndPublish, populated as draft)")
  void queryAllAuthors() {
    String query = """
        { entries(contentType: "api::author.author", status: "draft") {
            data { documentId contentType status }
            meta { total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.meta.total", greaterThanOrEqualTo(2))
        .body("data.entries.data[0].documentId", notNullValue())
        .body("data.entries.data[0].contentType", is("api::author.author"));
  }

  @Test
  @DisplayName("should query a single author by documentId")
  void querySingleAuthor() {
    String lookupQuery = """
        { entries(contentType: "api::author.author", status: "draft", pagination: {page: 1, pageSize: 1}) {
            data { documentId contentType }
            meta { total }
          }
        }
        """;

    String documentId = given()
        .contentType("application/json")
        .body(Map.of("query", lookupQuery))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .extract()
        .path("data.entries.data[0].documentId");

    if (documentId == null) return;

    String query = """
        { entry(contentType: "api::author.author", documentId: "%s") {
            documentId contentType status
          }
        }
        """.formatted(documentId);

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entry.documentId", is(documentId))
        .body("data.entry.contentType", is("api::author.author"))
        .body("data.entry.status", is("draft"));
  }

  // ========================================================================
  // Content Queries — Categories (non-draftAndPublish, populated as draft)
  // ========================================================================

  @Test
  @DisplayName("should query all categories (non-draftAndPublish, populated as draft)")
  void queryAllCategories() {
    String query = """
        { entries(contentType: "api::category.category", status: "draft") {
            data { documentId contentType status }
            meta { total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.meta.total", greaterThanOrEqualTo(3))
        .body("data.entries.data[0].documentId", notNullValue())
        .body("data.entries.data[0].contentType", is("api::category.category"));
  }

  // ========================================================================
  // Single Type Queries (populated as draft)
  // ========================================================================

  @Test
  @DisplayName("should query the homepage single type")
  void queryHomepage() {
    String query = """
        { entries(contentType: "api::homepage.homepage", status: "draft") {
            data { documentId contentType status }
            meta { total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.meta.total", greaterThan(0))
        .body("data.entries.data[0].contentType", is("api::homepage.homepage"))
        .body("data.entries.data[0].status", notNullValue());
  }

  @Test
  @DisplayName("should query the global settings single type")
  void queryGlobalSettings() {
    String query = """
        { entries(contentType: "api::global.global", status: "draft") {
            data { documentId contentType status }
            meta { total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.meta.total", greaterThan(0))
        .body("data.entries.data[0].contentType", is("api::global.global"));
  }

  // ========================================================================
  // Schema Introspection
  // ========================================================================

  @Test
  @DisplayName("should list all registered demo content type schemas")
  void queryContentTypes() {
    String query = """
        { contentTypes {
            uid kind singularName pluralName displayName draftAndPublish localized
            fields { name type required }
            relations { fieldName type target }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.contentTypes", hasSize(greaterThanOrEqualTo(5)))
        .body("data.contentTypes.uid", hasItems(
            "api::article.article",
            "api::author.author",
            "api::category.category",
            "api::homepage.homepage",
            "api::global.global"));
  }

  @Test
  @DisplayName("should query article content type schema in detail")
  void queryArticleContentType() {
    String query = """
        { contentType(uid: "api::article.article") {
            uid kind singularName pluralName displayName draftAndPublish localized
            fields { name type required maxLength }
            relations { fieldName type target targetAttribute }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.contentType.uid", is("api::article.article"))
        .body("data.contentType.kind", is("COLLECTION_TYPE"))
        .body("data.contentType.draftAndPublish", is(true))
        .body("data.contentType.fields.name", hasItems("title", "slug", "body", "featured"))
        .body("data.contentType.relations.fieldName", hasItems("author", "categories"))
        .body("data.contentType.relations.find { it.fieldName == 'author' }.type", is("MANY_TO_ONE"))
        .body("data.contentType.relations.find { it.fieldName == 'author' }.target", is("api::author.author"));
  }

  @Test
  @DisplayName("should query author content type schema")
  void queryAuthorContentType() {
    String query = """
        { contentType(uid: "api::author.author") {
            uid kind singularName pluralName displayName draftAndPublish
            fields { name type required }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.contentType.uid", is("api::author.author"))
        .body("data.contentType.kind", is("COLLECTION_TYPE"))
        .body("data.contentType.draftAndPublish", is(false))
        .body("data.contentType.fields.name", hasItems("name", "email", "bio"));
  }

  @Test
  @DisplayName("should query category content type schema")
  void queryCategoryContentType() {
    String query = """
        { contentType(uid: "api::category.category") {
            uid singularName pluralName draftAndPublish
            fields { name type required maxLength }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.contentType.uid", is("api::category.category"))
        .body("data.contentType.draftAndPublish", is(false))
        .body("data.contentType.fields.name", hasItems("name", "slug", "description"));
  }

  @Test
  @DisplayName("should query homepage single type schema")
  void queryHomepageContentType() {
    String query = """
        { contentType(uid: "api::homepage.homepage") {
            uid kind draftAndPublish localized
            fields { name type required }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.contentType.uid", is("api::homepage.homepage"))
        .body("data.contentType.kind", is("SINGLE_TYPE"))
        .body("data.contentType.draftAndPublish", is(true))
        .body("data.contentType.localized", is(false))
        .body("data.contentType.fields.name", hasItems("heroTitle", "heroSubtitle", "aboutText"));
  }

  @Test
  @DisplayName("should query global settings single type schema")
  void queryGlobalContentType() {
    String query = """
        { contentType(uid: "api::global.global") {
            uid kind draftAndPublish
            fields { name type required defaultValue }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.contentType.uid", is("api::global.global"))
        .body("data.contentType.kind", is("SINGLE_TYPE"))
        .body("data.contentType.draftAndPublish", is(false))
        .body("data.contentType.fields.name", hasItems("siteName", "siteDescription", "footerText"));
  }

  // ========================================================================
  // Component Introspection
  // ========================================================================

  @Test
  @DisplayName("should list all registered demo components")
  void queryComponents() {
    String query = """
        { components {
            uid category displayName description
            fields { name type required maxLength }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.components", hasSize(greaterThanOrEqualTo(2)))
        .body("data.components.uid", hasItems("basic.seo", "basic.media"));
  }

  @Test
  @DisplayName("should query SEO component schema")
  void querySeoComponent() {
    String query = """
        { component(uid: "basic.seo") {
            uid category displayName
            fields { name type maxLength }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.component.uid", is("basic.seo"))
        .body("data.component.category", is("basic"))
        .body("data.component.fields.name", hasItems("metaTitle", "metaDescription", "keywords"));
  }

  @Test
  @DisplayName("should query Media component schema")
  void queryMediaComponent() {
    String query = """
        { component(uid: "basic.media") {
            uid category displayName
            fields { name type required maxLength }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.component.uid", is("basic.media"))
        .body("data.component.category", is("basic"))
        .body("data.component.fields.name", hasItems("mediaUrl", "altText", "caption"));
  }

  // ========================================================================
  // Locales
  // ========================================================================

  @Test
  @DisplayName("should list configured locales")
  void queryLocales() {
    String query = """
        { locales {
            code displayName isDefault
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.locales", notNullValue());
  }

  // ========================================================================
  // Edge Cases & Error Handling
  // ========================================================================

  @Test
  @DisplayName("should return empty data for non-existent content type")
  void queryNonExistentContentType() {
    String query = """
        { entries(contentType: "api::nonexistent.void") { data { documentId } } }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.data", hasSize(0));
  }

  @Test
  @DisplayName("should return error for malformed GraphQL query")
  void malformedQuery() {
    given()
        .contentType("application/json")
        .body(Map.of("query", "{ this is bad syntax !!! }"))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue());
  }

  @Test
  @DisplayName("should return error with error response for non-JSON content type")
  void invalidContentType() {
    given()
        .contentType("text/plain")
        .body("not json")
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue());
  }

  @Test
  @DisplayName("should return error for empty GraphQL query")
  void emptyQuery() {
    given()
        .contentType("application/json")
        .body(Map.of("query", ""))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue());
  }

  // ========================================================================
  // Pagination
  // ========================================================================

  @Test
  @DisplayName("should paginate articles with custom page size")
  void paginateArticles() {
    String query = """
        { entries(contentType: "api::article.article", status: "published", pagination: {page: 1, pageSize: 2}) {
            data { documentId contentType }
            meta { page pageSize pageCount total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.data.size()", lessThanOrEqualTo(2))
        .body("data.entries.meta.page", is(1))
        .body("data.entries.meta.pageSize", is(2))
        .body("data.entries.meta.total", greaterThanOrEqualTo(3));
  }

  @Test
  @DisplayName("should return empty data for page beyond results")
  void paginateBeyondResults() {
    String query = """
        { entries(contentType: "api::article.article", status: "published", pagination: {page: 100, pageSize: 10}) {
            data { documentId contentType }
            meta { page pageSize pageCount total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.data", hasSize(0))
        .body("data.entries.meta.page", is(100));
  }

}
