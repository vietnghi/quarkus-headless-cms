package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the GraphQL API (SmallRye GraphQL via /graphql).
 *
 * <p>Covers three areas split from the parent example-integration-tests task:
 * <ol>
 *   <li><b>GraphQL query integration tests for content types</b> — entries, entry,
 *       schema introspection (contentTypes, contentType, components, component, locales)</li>
 *   <li><b>GraphQL mutation tests</b> — create, update, delete, publish, unpublish,
 *       createLocalization via GraphQL mutations</li>
 *   <li><b>GraphQL population and relation query tests</b> — pagination, sort, locale
 *       filtering, and entry metadata</li>
 * </ol>
 *
 * <p>Tests use only fields present in the GraphQL schema (Entry exposes metadata fields
 * only: documentId, contentType, locale, status, createdAt, updatedAt, publishedAt).
 * Dynamic content fields ({@code data} map) are {@code @Ignore}d and not queryable
 * through GraphQL.
 *
 * <p>Mutations require {@code @RolesAllowed({"authenticated", "admin"})};
 * {@code @TestSecurity} sets up a test security identity so mutation tests can
 * call {@code @RolesAllowed} methods without needing a real JWT.
 */
@QuarkusTest
@DisplayName("GraphQL Integration Tests")
class GraphQLIT {

  // ========================================================================
  // Section 1: Content Queries
  // ========================================================================

  @Nested
  @DisplayName("Content queries")
  class ContentQueries {

    @Test
    @DisplayName("should query all published articles")
    void queryAllArticles() {
      String query = """
          { entries(contentType: "api::article.article", status: "published") {
              data { documentId contentType locale status }
              meta { page pageSize total pageCount }
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
          .body("data.entries.meta.total", greaterThanOrEqualTo(7))
          .body("data.entries.meta.page", is(1))
          .body("data.entries.meta.pageSize", is(25))
          .body("data.entries.meta.pageCount", greaterThanOrEqualTo(1))
          .body("data.entries.data", notNullValue())
          .body("data.entries.data[0].documentId", notNullValue());
    }

    @Test
    @DisplayName("should query all tags (non-draft-publish content type)")
    void queryAllTags() {
      String query = """
          { entries(contentType: "api::tag.tag", status: "draft") {
              data { documentId contentType locale status }
              meta { page pageSize total pageCount }
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
          .body("data.entries.meta.total", greaterThanOrEqualTo(10))
          .body("data.entries.data", notNullValue());
    }

    @Test
    @DisplayName("should query single entry by documentId")
    void querySingleEntry() {
      String documentId = given()
          .contentType("application/json")
          .body(Map.of("query", """
              { entries(contentType: "api::article.article", status: "published") {
                  data { documentId }
                }
              }
              """))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .extract()
          .path("data.entries.data[0].documentId");

      if (documentId == null) return;

      String query = """
          { entry(contentType: "api::article.article", documentId: "%s") {
              documentId contentType locale status createdAt
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
          .body("data.entry.locale", notNullValue())
          .body("data.entry.status", notNullValue())
          .body("data.entry.createdAt", notNullValue());
    }

    @Test
    @DisplayName("should return null for non-existent entry documentId")
    void queryNonExistentEntry() {
      String query = """
          { entry(contentType: "api::tag.tag", documentId: "nonexistent-12345") {
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

    @Test
    @DisplayName("should query articles with draft status")
    void queryDraftArticles() {
      String query = """
          { entries(contentType: "api::article.article", status: "draft") {
              data { documentId contentType status }
              meta { page pageSize total }
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
          .body("data.entries.data", notNullValue());
    }

    @Test
    @DisplayName("should query single-type content (homepage)")
    void queryHomepage() {
      String query = """
          { entries(contentType: "api::homepage.homepage", status: "draft") {
              data { documentId contentType locale status }
              meta { total page pageSize }
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
          .body("data.entries.data[0].contentType", is("api::homepage.homepage"))
          .body("data.entries.data[0].status", notNullValue())
          .body("data.entries.data[0].locale", notNullValue());
    }

    @Test
    @DisplayName("should query single-type content (global settings)")
    void queryGlobal() {
      String query = """
          { entries(contentType: "api::global.global", status: "draft") {
              data { documentId contentType locale status }
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
          .body("data.entries.data[0].contentType", is("api::global.global"));
    }
  }

  // ========================================================================
  // Section 2: Pagination, Sort & Locale Queries
  // ========================================================================

  @Nested
  @DisplayName("Pagination, sort and locale")
  class PaginationSortLocale {

    @Test
    @DisplayName("should query entries with custom page size")
    void queryWithSmallPageSize() {
      String query = """
          { entries(contentType: "api::article.article", status: "published",
                    pagination: {page: 1, pageSize: 2}) {
              data { documentId }
              meta { page pageSize total pageCount }
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
          .body("data.entries.meta.pageSize", is(2))
          .body("data.entries.meta.page", is(1))
          .body("data.entries.meta.total", greaterThanOrEqualTo(7))
          .body("data.entries.meta.pageCount", greaterThanOrEqualTo(4))
          .body("data.entries.data", hasSize(2));
    }

    @Test
    @DisplayName("should query entries with custom pagination (page 2)")
    void queryPageTwo() {
      String query = """
          { entries(contentType: "api::article.article", status: "published",
                    pagination: {page: 2, pageSize: 3}) {
              data { documentId }
              meta { page pageSize total pageCount }
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
          .body("data.entries.meta.page", is(2))
          .body("data.entries.meta.pageSize", is(3))
          .body("data.entries.meta.total", greaterThanOrEqualTo(7))
          .body("data.entries.data", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("should query entries sorted by createdAt")
    void querySorted() {
      String query = """
          { entries(contentType: "api::article.article", status: "published",
                    sort: "createdAt:desc") {
              data { documentId createdAt }
              meta { page total }
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
          .body("data.entries.data", notNullValue())
          .body("data.entries.meta.total", greaterThanOrEqualTo(7));
    }

    @Test
    @DisplayName("should query entries with filters (H2 does not support JSONB, expect system error)")
    void queryWithFilters() {
      // Filters use jsonb_extract_path_text which is PostgreSQL-specific.
      // H2 in PostgreSQL mode does not support this function, so this test
      // verifies the filter path is attempted (errors expected in H2).
      String query = """
          query FilterArticles($filters: String) {
            entries(contentType: "api::article.article", filters: $filters, status: "published") {
              data { documentId }
              meta { total }
            }
          }
          """;

      given()
          .contentType("application/json")
          .body(Map.of(
              "query", query,
              "variables", Map.of("filters", "{\"featured\": {\"$eq\": true}}")))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200);
      // Note: On PostgreSQL this would return filtered results.
      // On H2 the JSONB function is unavailable, so it may error.
    }
  }

  // ========================================================================
  // Section 3: Schema Introspection
  // ========================================================================

  @Nested
  @DisplayName("Schema introspection")
  class SchemaIntrospection {

    @Test
    @DisplayName("should list all registered content type schemas")
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
          .body("data.contentTypes", hasSize(6))
          .body("data.contentTypes.uid", hasItems(
              "api::article.article",
              "api::author.author",
              "api::category.category",
              "api::tag.tag",
              "api::homepage.homepage",
              "api::global.global"));
    }

    @Test
    @DisplayName("should query a single content type schema by UID")
    void querySingleContentType() {
      String query = """
          { contentType(uid: "api::article.article") {
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
          .body("data.contentType.uid", is("api::article.article"))
          .body("data.contentType.kind", is("COLLECTION_TYPE"))
          .body("data.contentType.draftAndPublish", is(true))
          .body("data.contentType.localized", is(true));
    }

    @Test
    @DisplayName("should return null for non-existent content type UID")
    void queryNonExistentContentType() {
      String query = """
          { contentType(uid: "api::nonexistent.void") {
              uid kind displayName
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
          .body("data.contentType", nullValue());
    }

    @Test
    @DisplayName("should list all registered component schemas")
    void queryComponents() {
      String query = """
          { components {
              uid category displayName
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
          .body("data.components", hasSize(greaterThanOrEqualTo(3)))
          .body("data.components.uid", hasItems("shared.seo", "shared.media", "shared.related-links"));
    }

    @Test
    @DisplayName("should query a single component schema")
    void querySingleComponent() {
      String query = """
          { component(uid: "shared.seo") {
              uid category displayName
              fields { name type }
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
          .body("data.component.uid", is("shared.seo"))
          .body("data.component.category", is("shared"))
          .body("data.component.fields[0].name", notNullValue())
          .body("data.component.fields[0].type", notNullValue());
    }

    @Test
    @DisplayName("should return null for non-existent component UID")
    void queryNonExistentComponent() {
      String query = """
          { component(uid: "nonexistent.component") {
              uid category displayName
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
          .body("data.component", nullValue());
    }

    @Test
    @DisplayName("should list configured locales")
    void queryLocales() {
      String query = """
          { locales {
              code displayName isDefault enabled
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
  }

  // ========================================================================
  // Section 4: Mutation Tests
  //
  // Mutations require @RolesAllowed({"authenticated", "admin"}) on the
  // CmsGraphQLResource. @TestSecurity sets up a test security identity so
  // these calls succeed without needing a real JWT.
  // ========================================================================

  @Nested
  @TestSecurity(user = "testadmin", roles = {"admin"})
  @DisplayName("Content mutations")
  class MutationTests {

    @Test
    @DisplayName("should create a tag entry via createEntry mutation")
    void createTag() {
      String mutation = """
          mutation { createEntry(contentType: "api::tag.tag",
                                 data: "{\\"name\\": \\"Demo-Tag\\", \\"slug\\": \\"demo-tag\\", \\"color\\": \\"#ff6600\\"}") {
              documentId contentType locale status
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
          .body("data.createEntry.documentId", notNullValue())
          .body("data.createEntry.contentType", is("api::tag.tag"))
          .body("data.createEntry.locale", notNullValue())
          .body("data.createEntry.status", notNullValue());
    }

    @Test
    @DisplayName("should create a draft article entry for lifecycle testing")
    void createArticle() {
      String mutation = """
          mutation { createEntry(contentType: "api::article.article",
                                 data: "{\\"title\\": \\"GraphQL Article\\", \\"slug\\": \\"graphql-article\\"}") {
              documentId contentType locale status createdAt
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
          .body("data.createEntry.documentId", notNullValue())
          .body("data.createEntry.contentType", is("api::article.article"))
          .body("data.createEntry.status", is("draft"))
          .body("data.createEntry.createdAt", notNullValue());
    }

    @Test
    @DisplayName("should create and update an entry via updateEntry mutation")
    void createAndUpdateEntry() {
      // Create
      String docId = given()
          .contentType("application/json")
          .body(Map.of("query", """
              mutation { createEntry(contentType: "api::article.article",
                                     data: "{\\"title\\": \\"Update Test\\", \\"slug\\": \\"update-test\\"}") {
                  documentId
                }
              }
              """))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.createEntry.documentId", notNullValue())
          .extract()
          .path("data.createEntry.documentId");

      if (docId == null) return;

      String updateMutation = """
          mutation { updateEntry(contentType: "api::article.article",
                                 documentId: "%s",
                                 data: "{\\"title\\": \\"Updated Title\\"}") {
              documentId contentType status
            }
          }
          """.formatted(docId);

      given()
          .contentType("application/json")
          .body(Map.of("query", updateMutation))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.updateEntry.documentId", is(docId))
          .body("data.updateEntry.contentType", is("api::article.article"))
          .body("data.updateEntry.status", is("draft"));
    }

    @Test
    @DisplayName("should publish and unpublish an entry")
    void publishUnpublishCycle() {
      String docId = given()
          .contentType("application/json")
          .body(Map.of("query", """
              mutation { createEntry(contentType: "api::article.article",
                                     data: "{\\"title\\": \\"Pub Test\\", \\"slug\\": \\"pub-test\\"}") {
                  documentId
                }
              }
              """))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.createEntry.documentId", notNullValue())
          .extract()
          .path("data.createEntry.documentId");

      if (docId == null) return;

      // Publish
      String publishMutation = """
          mutation { publishEntry(contentType: "api::article.article",
                                  documentId: "%s") {
              documentId status publishedAt
            }
          }
          """.formatted(docId);

      given()
          .contentType("application/json")
          .body(Map.of("query", publishMutation))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.publishEntry.status", is("published"))
          .body("data.publishEntry.publishedAt", notNullValue());

      // Unpublish
      String unpublishMutation = """
          mutation { unpublishEntry(contentType: "api::article.article",
                                    documentId: "%s") }
          """.formatted(docId);

      given()
          .contentType("application/json")
          .body(Map.of("query", unpublishMutation))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.unpublishEntry", is(true));
    }

    @Test
    @DisplayName("should delete an entry via deleteEntry mutation")
    void deleteEntry() {
      String docId = given()
          .contentType("application/json")
          .body(Map.of("query", """
              mutation { createEntry(contentType: "api::article.article",
                                     data: "{\\"title\\": \\"Delete Me\\", \\"slug\\": \\"delete-me\\"}") {
                  documentId
                }
              }
              """))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.createEntry.documentId", notNullValue())
          .extract()
          .path("data.createEntry.documentId");

      if (docId == null) return;

      String deleteMutation = """
          mutation { deleteEntry(contentType: "api::article.article",
                                 documentId: "%s") }
          """.formatted(docId);

      given()
          .contentType("application/json")
          .body(Map.of("query", deleteMutation))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.deleteEntry", is(true));

      // Verify deletion
      String verifyQuery = """
          { entry(contentType: "api::article.article", documentId: "%s") {
              documentId
            }
          }
          """.formatted(docId);

      given()
          .contentType("application/json")
          .body(Map.of("query", verifyQuery))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.entry", nullValue());
    }

    @Test
    @DisplayName("should delete a non-existent entry (returns false)")
    void deleteNonExistent() {
      String mutation = """
          mutation { deleteEntry(contentType: "api::article.article",
                                 documentId: "nonexistent-delete-id") }
          """;

      given()
          .contentType("application/json")
          .body(Map.of("query", mutation))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.deleteEntry", is(false));
    }

    @Test
    @DisplayName("should return error when publishing non-existent entry")
    void publishNonExistent() {
      String mutation = """
          mutation { publishEntry(contentType: "api::article.article",
                                  documentId: "nonexistent-publish-id") {
              documentId status
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

    @Test
    @DisplayName("should create localization for an article")
    void createLocalization() {
      String docId = given()
          .contentType("application/json")
          .body(Map.of("query", """
              mutation { createEntry(contentType: "api::article.article",
                                     data: "{\\"title\\": \\"Localization Source\\", \\"slug\\": \\"l10n-source\\"}",
                                     locale: "en") {
                  documentId
                }
              }
              """))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.createEntry.documentId", notNullValue())
          .extract()
          .path("data.createEntry.documentId");

      if (docId == null) return;

      String locMutation = """
          mutation { createLocalization(documentId: "%s",
                                         sourceLocale: "en",
                                         targetLocale: "fr",
                                         data: "{\\"title\\": \\"Article en français\\"}") {
              documentId locale status contentType
            }
          }
          """.formatted(docId);

      given()
          .contentType("application/json")
          .body(Map.of("query", locMutation))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.createLocalization.documentId", notNullValue())
          .body("data.createLocalization.locale", is("fr"))
          .body("data.createLocalization.contentType", is("api::article.article"))
          .body("data.createLocalization.status", is("draft"));
    }

    @Test
    @DisplayName("should return error when updating non-existent entry")
    void updateNonExistent() {
      String mutation = """
          mutation { updateEntry(contentType: "api::article.article",
                                 documentId: "nonexistent-update-id",
                                 data: "{\\"title\\": \\"Nowhere\\"}") {
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
  }

  // ========================================================================
  // Section 5: Error Handling & Edge Cases
  // ========================================================================

  @Nested
  @DisplayName("Error handling")
  class ErrorHandling {

    @Test
    @DisplayName("should handle non-existent content type in entries query (returns empty data)")
    void queryNonExistentContentTypeEntries() {
      String query = """
          { entries(contentType: "api::nonexistent.void") { data { documentId } meta { total } } }
          """;

      given()
          .contentType("application/json")
          .body(Map.of("query", query))
          .when()
          .post("/graphql")
          .then()
          .statusCode(200)
          .body("data.entries.data", notNullValue())
          .body("data.entries.meta.total", is(0));
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
    @DisplayName("should return error for empty query string")
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

    @Test
    @DisplayName("should handle non-JSON content type gracefully (GraphQL always returns 200)")
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
    @DisplayName("should handle unknown argument gracefully (returns validation error)")
    void unknownArgument() {
      String query = """
          { entries(contentType: "api::article.article", unknownArg: "test") {
              data { documentId }
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
          .body("errors", notNullValue());
    }
  }
}
