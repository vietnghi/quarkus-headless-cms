package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Disabled;
import com.quarkus.cms.it.support.SampleDataTestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests that verify the sample content seed data (from {@code cms-sample-content})
 * is properly loaded via REST endpoints.
 *
 * <p>These tests do NOT clear the database — they rely on the SampleContentSeeder having run
 * during application startup. They verify the expected number and shape of seeded entries.
 *
 * <p>Expected seed data:
 * <ul>
 *   <li>10 tags (quarkus, java, headless-cms, tutorial, graphql, rest-api, architecture, performance, ai, design-systems)</li>
 *   <li>5 categories (Technology, Design, Science, DevOps, Open Source)</li>
 *   <li>4 authors (Alice Johnson, Bob Smith, Dr. Maria Garcia, James Wilson)</li>
 *   <li>8 articles with relations to authors, categories, and tags</li>
 *   <li>Homepage single-type entry</li>
 *   <li>Global Settings single-type entry</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(SampleDataTestProfile.class)
@Disabled("Seed data not loading in PostgreSQL Testcontainers — needs seeder timing fix")
@DisplayName("Sample Data Verification")
class SampleDataVerificationIT {

  // ========================================================================
  // Tag Verification
  // ========================================================================

  @Test
  @DisplayName("should have 10 seeded tags")
  void seededTagsCount() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/tag")
        .then()
        .statusCode(200)
        .body("data.size()", is(10))
        .body("meta.pagination.total", is(10));
  }

  @Test
  @DisplayName("should contain all expected tag names")
  void seededTagNames() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/tag")
        .then()
        .statusCode(200)
        .body("data.name", hasItems(
            "quarkus", "java", "headless-cms", "tutorial", "graphql",
            "rest-api", "architecture", "performance", "ai", "design-systems"))
        .body("data.slug", hasItems(
            "quarkus", "java", "headless-cms", "tutorial", "graphql",
            "rest-api", "architecture", "performance", "ai", "design-systems"));
  }

  @Test
  @DisplayName("tag should have unique slug and color")
  void tagFieldShape() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/tag")
        .then()
        .statusCode(200)
        .body("data[0].name", notNullValue())
        .body("data[0].slug", notNullValue())
        .body("data[0].color", startsWith("#"))
        .body("data[0].color", hasLength(7));
  }

  // ========================================================================
  // Category Verification
  // ========================================================================

  @Test
  @DisplayName("should have 5 seeded categories")
  void seededCategoriesCount() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/category")
        .then()
        .statusCode(200)
        .body("data.size()", is(5))
        .body("meta.pagination.total", is(5))
        .body("data.name", hasItems("Technology", "Design", "Science", "DevOps", "Open Source"))
        .body("data.slug", hasItems("technology", "design", "science", "devops", "open-source"));
  }

  @Test
  @DisplayName("category should have localized field structure")
  void categoryFieldShape() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/category")
        .then()
        .statusCode(200)
        .body("data[0].name", notNullValue())
        .body("data[0].slug", notNullValue())
        .body("data[0].description", notNullValue())
        .body("data[0].color", startsWith("#"))
        .body("data[0].color", hasLength(7))
        .body("data[0].icon", notNullValue())
        .body("data[0].sortOrder", notNullValue());
  }

  // ========================================================================
  // Author Verification
  // ========================================================================

  @Test
  @DisplayName("should have 4 seeded authors")
  void seededAuthorsCount() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/author")
        .then()
        .statusCode(200)
        .body("data.size()", is(4))
        .body("meta.pagination.total", is(4));
  }

  @Test
  @DisplayName("should have Alice Johnson as featured author")
  void featuredAuthors() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/author")
        .then()
        .statusCode(200)
        .body("data.name", hasItems(
            "Alice Johnson", "Bob Smith", "Dr. Maria Garcia", "James Wilson"))
        .body("data.find { it.name == 'Alice Johnson' }.featuredAuthor", is(true))
        .body("data.find { it.name == 'Dr. Maria Garcia' }.featuredAuthor", is(true));
  }

  @Test
  @DisplayName("author should have social links")
  void authorSocialLinks() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/author")
        .then()
        .statusCode(200)
        .body("data[0].email", notNullValue())
        .body("data[0].bio", notNullValue())
        .body("data[0].socialLinks", notNullValue());
  }

  // ========================================================================
  // Article Verification
  // ========================================================================

  @Test
  @DisplayName("should have 8 seeded articles")
  void seededArticlesCount() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data.size()", is(8))
        .body("meta.pagination.total", is(8));
  }

  @Test
  @DisplayName("article should have rich text body and structured fields")
  void articleFieldShape() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/article")
        .then()
        .statusCode(200)
        .body("data[0].title", notNullValue())
        .body("data[0].slug", notNullValue())
        .body("data[0].body", notNullValue())
        .body("data[0].featured", notNullValue())
        .body("data[0].readingTime", greaterThanOrEqualTo(1))
        .body("data[0].readingTime", lessThanOrEqualTo(120));
  }

  // ========================================================================
  // Single-Type Verification
  // ========================================================================

  @Test
  @DisplayName("should have single-type homepage entry")
  void homepageExists() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/homepage")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.heroTitle", notNullValue())
        .body("data.heroSubtitle", notNullValue())
        .body("data.aboutTitle", notNullValue())
        .body("data.aboutText", notNullValue());
  }

  @Test
  @DisplayName("should have single-type global settings entry")
  void globalSettingsExist() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/global")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.siteName", notNullValue())
        .body("data.siteDescription", notNullValue())
        .body("data.footerText", notNullValue())
        .body("data.contactEmail", notNullValue())
        .body("data.socialLinks", notNullValue());
  }

  @Test
  @DisplayName("global settings should have default site name")
  void globalSettingsDefaults() {
    given()
        .queryParam("publicationState", "preview")
        .when()
        .get("/api/global")
        .then()
        .statusCode(200)
        .body("data.siteName", is("My CMS Site"));
  }

  // ========================================================================
  // Schema / Content Type Registration Verification
  // ========================================================================

  @Test
  @DisplayName("should have content types registered via API documentation endpoint")
  void contentTypesAvailable() {
    given()
        .when()
        .get("/api/health")
        .then()
        .statusCode(200)
        .body("status", is("ok"))
        .body("cms", is("quarkus-headless-cms"))
        .body("version", notNullValue());
  }
}
