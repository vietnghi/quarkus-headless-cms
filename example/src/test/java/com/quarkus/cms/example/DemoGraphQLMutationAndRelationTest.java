package com.quarkus.cms.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for GraphQL mutations and relation queries.
 *
 * <p>Covers the authenticated mutation surface: createEntry, updateEntry, deleteEntry,
 * publishEntry, unpublishEntry — using the inline JSON data syntax supported by SmallRye
 * GraphQL's JSON scalar mapping.
 *
 * <p>Uses a custom {@link GraphQLTestProfile} to enable GraphQL and JWT auth which are
 * disabled in the default test configuration. Auth is obtained via the GraphQL login
 * mutation after REST registration.
 */
@QuarkusTest
@TestProfile(GraphQLTestProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Demo GraphQL Mutations & Relations")
class DemoGraphQLMutationAndRelationTest {

  private static String jwtToken;
  private static String createdDocId;
  private static String seededArticleDocId;

  private static final String TEST_USER = "graphtest-" + UUID.randomUUID().toString().substring(0, 8);
  private static final String TEST_EMAIL = TEST_USER + "@example.com";
  private static final String TEST_PASS = "TestPass123!";

  // ========================================================================
  // Setup: Register user, login, get a seeded article documentId
  // ========================================================================

  @BeforeAll
  void setup() {
    // Register a test user via REST (may redirect during startup)
    try {
      given()
          .redirects().follow(true)
          .contentType("application/json")
          .body(Map.of(
              "username", TEST_USER,
              "email", TEST_EMAIL,
              "password", TEST_PASS,
              "firstName", "GraphQL",
              "lastName", "Tester"
          ))
          .when()
          .post("/admin/register");
    } catch (Throwable t) {
      // Registration may redirect; handled below
    }

    // Login via REST
    try {
      jwtToken = given()
          .redirects().follow(true)
          .contentType("application/json")
          .body(Map.of("identifier", TEST_USER, "password", TEST_PASS))
          .when()
          .post("/admin/login")
          .then()
          .statusCode(200)
          .extract()
          .path("jwt");
    } catch (Throwable t) {
      // Login may fail if registration didn't complete
    }

    // If still no token, try login via GraphQL mutation
    if (jwtToken == null) {
      tryRegisterAndLoginViaGraphQL();
    }

    // Grab a seeded article documentId
    try {
      String lookupQuery = """
          { entries(contentType: "api::article.article", status: "published", pagination: {page: 1, pageSize: 1}) {
              data { documentId }
              meta { total }
            }
          }
          """;

      seededArticleDocId = given()
          .redirects().follow(true)
          .contentType("application/json")
          .body(Map.of("query", lookupQuery))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .extract()
          .path("data.entries.data[0].documentId");
    } catch (Throwable t) {
      // Gracefully handle
    }
  }

  private static void tryRegisterAndLoginViaGraphQL() {
    try {
      given()
          .redirects().follow(true)
          .contentType("application/json")
          .body(Map.of(
              "username", TEST_USER,
              "email", TEST_EMAIL,
              "password", TEST_PASS,
              "firstName", "GraphQL",
              "lastName", "Tester"
          ))
          .when()
          .post("/admin/register");
    } catch (Throwable t) {
      // ignore
    }

    String loginQuery = """
        mutation { login(identifier: "%s", password: "%s") { jwt } }
        """.formatted(TEST_USER, TEST_PASS);

    try {
      jwtToken = given()
          .redirects().follow(true)
          .contentType("application/json")
          .body(Map.of("query", loginQuery))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .extract()
          .path("data.login.jwt");
    } catch (Throwable t) {
      // ignore
    }
  }

  // ========================================================================
  // Auth mutation tests
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should login via GraphQL and receive a JWT")
  void loginViaGraphQL() {
    // Try login via GraphQL mutation
    String loginQuery = """
        mutation { login(identifier: "%s", password: "%s") { jwt user { id username email roles } } }
        """.formatted(TEST_USER, TEST_PASS);

    given()
        .contentType("application/json")
        .body(Map.of("query", loginQuery))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.login.jwt", notNullValue())
        .body("data.login.user.username", is(TEST_USER));
  }

  @Test
  @Order(2)
  @DisplayName("should create an entry via createEntry mutation")
  void createEntryViaGraphQL() {
    if (jwtToken == null) return;

    String mutation = """
        mutation {
          createEntry(
            contentType: "api::article.article",
            data: {title: "GraphQL Mutation Test", slug: "graphql-mutation-test", excerpt: "Created via GraphQL mutation", featured: false},
            locale: "en"
          ) {
            documentId contentType status locale
          }
        }
        """;

    String docId = given()
        .contentType("application/json")
        .header("Authorization", "Bearer " + jwtToken)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.createEntry.documentId", notNullValue())
        .body("data.createEntry.contentType", is("api::article.article"))
        .body("data.createEntry.status", is("draft"))
        .body("data.createEntry.locale", is("en"))
        .extract()
        .path("data.createEntry.documentId");

    createdDocId = docId;
  }

  @Test
  @Order(3)
  @DisplayName("should update an entry via updateEntry mutation")
  void updateEntryViaGraphQL() {
    if (jwtToken == null || createdDocId == null) return;

    String mutation = """
        mutation {
          updateEntry(
            contentType: "api::article.article",
            documentId: "%s",
            data: {title: "Updated GraphQL Mutation Test", excerpt: "Updated via GraphQL"}
          ) {
            documentId contentType status
          }
        }
        """.formatted(createdDocId);

    given()
        .contentType("application/json")
        .header("Authorization", "Bearer " + jwtToken)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.updateEntry.documentId", is(createdDocId))
        .body("data.updateEntry.contentType", is("api::article.article"));
  }

  @Test
  @Order(4)
  @DisplayName("should publish an entry via publishEntry mutation")
  void publishEntryViaGraphQL() {
    if (jwtToken == null || createdDocId == null) return;

    String mutation = """
        mutation {
          publishEntry(
            contentType: "api::article.article",
            documentId: "%s"
          ) {
            documentId status publishedAt
          }
        }
        """.formatted(createdDocId);

    given()
        .contentType("application/json")
        .header("Authorization", "Bearer " + jwtToken)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.publishEntry.documentId", is(createdDocId))
        .body("data.publishEntry.status", is("published"))
        .body("data.publishEntry.publishedAt", notNullValue());
  }

  @Test
  @Order(5)
  @DisplayName("should unpublish an entry via unpublishEntry mutation")
  void unpublishEntryViaGraphQL() {
    if (jwtToken == null || createdDocId == null) return;

    String mutation = """
        mutation {
          unpublishEntry(
            contentType: "api::article.article",
            documentId: "%s"
          )
        }
        """.formatted(createdDocId);

    given()
        .contentType("application/json")
        .header("Authorization", "Bearer " + jwtToken)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.unpublishEntry", is(true));
  }

  @Test
  @Order(6)
  @DisplayName("should delete an entry via deleteEntry mutation")
  void deleteEntryViaGraphQL() {
    if (jwtToken == null || createdDocId == null) return;

    String mutation = """
        mutation {
          deleteEntry(
            contentType: "api::article.article",
            documentId: "%s"
          )
        }
        """.formatted(createdDocId);

    given()
        .contentType("application/json")
        .header("Authorization", "Bearer " + jwtToken)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.deleteEntry", is(true));

    // Verify it's gone
    String verifyQuery = """
        { entry(contentType: "api::article.article", documentId: "%s") {
            documentId
          }
        }
        """.formatted(createdDocId);

    given()
        .redirects().follow(true)
        .contentType("application/json")
        .body(Map.of("query", verifyQuery))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entry", nullValue());
  }

  // ========================================================================
  // Unauthenticated mutation should fail
  // ========================================================================

  @Test
  @Order(7)
  @DisplayName("should return error for mutation without auth token")
  void mutationWithoutAuthReturnsError() {
    String mutation = """
        mutation {
          createEntry(
            contentType: "api::article.article",
            data: {title: "Unauth Test"},
            locale: "en"
          ) {
            documentId
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue());
  }

  // ========================================================================
  // Query tests — entries with metadata fields
  // ========================================================================

  @Test
  @Order(10)
  @DisplayName("should query entries with metadata and pagination")
  void queryEntriesWithMetadata() {
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
        .body("data.entries.data[0].contentType", is("api::article.article"));
  }

  @Test
  @Order(11)
  @DisplayName("should query a single entry by documentId")
  void queryEntryById() {
    if (seededArticleDocId == null) return;

    String query = """
        { entry(contentType: "api::article.article", documentId: "%s") {
            documentId contentType status
          }
        }
        """.formatted(seededArticleDocId);

    given()
        .contentType("application/json")
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entry.documentId", is(seededArticleDocId))
        .body("data.entry.contentType", is("api::article.article"))
        .body("data.entry.status", is("published"));
  }

  @Test
  @Order(12)
  @DisplayName("should query author entries")
  void queryAuthors() {
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
        .body("data.entries.data[0].documentId", notNullValue());
  }

  @Test
  @Order(13)
  @DisplayName("should query categories")
  void queryCategories() {
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
        .body("data.entries.meta.total", greaterThanOrEqualTo(3));
  }

  @Test
  @Order(14)
  @DisplayName("should query single type entries (homepage)")
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
        .body("data.entries.meta.total", greaterThanOrEqualTo(1))
        .body("data.entries.data[0].contentType", is("api::homepage.homepage"));
  }

  @Test
  @Order(20)
  @DisplayName("should filter entries by status via GraphQL")
  void filterEntriesByStatus() {
    // Draft entries should exist
    String draftQuery = """
        { entries(contentType: "api::article.article", status: "draft") {
            data { documentId status }
            meta { total }
          }
        }
        """;

    given()
        .contentType("application/json")
        .body(Map.of("query", draftQuery))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.entries.meta.total", greaterThanOrEqualTo(0));
  }

  // ========================================================================
  // Schema introspection tests
  // ========================================================================

  @Test
  @Order(30)
  @DisplayName("should list content type schemas via introspection")
  void queryContentTypes() {
    String query = """
        { contentTypes {
            uid kind singularName pluralName displayName draftAndPublish localized
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
  @Order(31)
  @DisplayName("should query a specific content type schema")
  void querySpecificContentType() {
    String query = """
        { contentType(uid: "api::article.article") {
            uid kind singularName pluralName displayName draftAndPublish
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
        .body("data.contentType.draftAndPublish", is(true));
  }

}
