package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Advanced integration tests for the Relations API covering additional relation types
 * beyond the basic MANY_TO_ONE tested in {@link RelationsIT}.
 *
 * <p>Self-contained — creates its own content types and cleans up after itself.
 * Covers MANY_TO_MANY and ONE_TO_ONE relation schema definitions, field listing,
 * empty resolution, and error cases.
 *
 * <p>NOTE: Relation persistence (attaching targets via PUT) is not tested here because
 * the REST API's update endpoint stores relation references as JSON blob data without
 * creating {@code CmsRelation} adjacency records.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Relations API — Advanced")
class RelationsAdvancedIT {

  // ── Content type UIDs for MANY_TO_MANY tests ──────────────────────────
  private static final String COURSE_UID = "api::relations-course.course";
  private static final String COURSE_SINGULAR = "relations-course";
  private static final String COURSE_PLURAL = "relations-courses";

  private static final String STUDENT_UID = "api::relations-student.student";
  private static final String STUDENT_SINGULAR = "relations-student";
  private static final String STUDENT_PLURAL = "relations-students";

  // ── Content type UIDs for ONE_TO_ONE tests ────────────────────────────
  private static final String LICENSE_UID = "api::relations-license.license";
  private static final String LICENSE_SINGULAR = "relations-license";
  private static final String LICENSE_PLURAL = "relations-licenses";

  private static final String DRIVER_UID = "api::relations-driver.driver";
  private static final String DRIVER_SINGULAR = "relations-driver";
  private static final String DRIVER_PLURAL = "relations-drivers";

  // ── Shared state ──────────────────────────────────────────────────────
  private static String courseDocId;
  private static String student1DocId;
  private static String student2DocId;
  private static String driverDocId;
  private static String licenseDocId;

  // ======================================================================
  // MANY_TO_MANY — Schema Setup
  // ======================================================================

  @Test
  @Order(1)
  @DisplayName("should create course content type")
  void createCourseContentType() {
    Map<String, Object> body = Map.of(
        "uid", COURSE_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", COURSE_SINGULAR,
        "pluralName", COURSE_PLURAL,
        "displayName", "Relations Course",
        "description", "Course content type for MANY_TO_MANY testing",
        "draftAndPublish", false,
        "fields", List.of(
            Map.of("name", "title", "type", "STRING", "required", true, "maxLength", 255)));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201)
        .body("data.uid", is(COURSE_UID));
  }

  @Test
  @Order(2)
  @DisplayName("should create student content type with MANY_TO_MANY to course")
  void createStudentContentType() {
    Map<String, Object> body = Map.of(
        "uid", STUDENT_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", STUDENT_SINGULAR,
        "pluralName", STUDENT_PLURAL,
        "displayName", "Relations Student",
        "description", "Student content type with MANY_TO_MANY relation to course",
        "draftAndPublish", false,
        "fields", List.of(
            Map.of("name", "name", "type", "STRING", "required", true, "maxLength", 255)),
        "relations", List.of(
            Map.of(
                "fieldName", "courses",
                "type", "MANY_TO_MANY",
                "target", COURSE_UID,
                "targetAttribute", "students")));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201)
        .body("data.uid", is(STUDENT_UID))
        .body("data.relations.size()", is(1))
        .body("data.relations[0].type", is("MANY_TO_MANY"));
  }

  // ======================================================================
  // MANY_TO_MANY — Entry Creation
  // ======================================================================

  @Test
  @Order(10)
  @DisplayName("should create a course entry")
  void createCourseEntry() {
    courseDocId = given()
        .contentType("application/json")
        .body(Map.of("title", "Advanced Mathematics"))
        .when()
        .post("/api/" + COURSE_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.title", is("Advanced Mathematics"))
        .extract()
        .path("data.documentId");
  }

  @Test
  @Order(11)
  @DisplayName("should create first student entry")
  void createStudent1Entry() {
    student1DocId = given()
        .contentType("application/json")
        .body(Map.of("name", "Alice"))
        .when()
        .post("/api/" + STUDENT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.name", is("Alice"))
        .extract()
        .path("data.documentId");
  }

  @Test
  @Order(12)
  @DisplayName("should create second student entry")
  void createStudent2Entry() {
    student2DocId = given()
        .contentType("application/json")
        .body(Map.of("name", "Bob"))
        .when()
        .post("/api/" + STUDENT_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.name", is("Bob"))
        .extract()
        .path("data.documentId");
  }

  // ======================================================================
  // MANY_TO_MANY — List / Resolve Relation Fields
  // ======================================================================

  @Test
  @Order(20)
  @DisplayName("should list MANY_TO_MANY relation field on student")
  void listStudentRelations() {
    given()
        .when()
        .get("/api/" + STUDENT_SINGULAR + "/" + student1DocId + "/relations")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.size()", is(1))
        .body("data[0].fieldName", is("courses"))
        .body("data[0].type", is("MANY_TO_MANY"))
        .body("data[0].target", is(COURSE_UID))
        .body("data[0].bidirectional", is(true));
  }

  @Test
  @Order(21)
  @DisplayName("should return empty data for unresolved MANY_TO_MANY relation")
  void resolveStudentCoursesEmpty() {
    given()
        .when()
        .get("/api/" + STUDENT_SINGULAR + "/" + student1DocId + "/relations/courses")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.size()", is(0));
  }

  // ======================================================================
  // ONE_TO_ONE — Schema Setup
  // ======================================================================

  @Test
  @Order(40)
  @DisplayName("should create driver license content type (target of ONE_TO_ONE)")
  void createLicenseContentType() {
    Map<String, Object> body = Map.of(
        "uid", LICENSE_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", LICENSE_SINGULAR,
        "pluralName", LICENSE_PLURAL,
        "displayName", "Relations License",
        "description", "License content type for ONE_TO_ONE testing",
        "draftAndPublish", false,
        "fields", List.of(
            Map.of("name", "licenseNumber", "type", "STRING", "required", true, "maxLength", 50)));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201)
        .body("data.uid", is(LICENSE_UID));
  }

  @Test
  @Order(41)
  @DisplayName("should create driver content type with ONE_TO_ONE to license")
  void createDriverContentType() {
    Map<String, Object> body = Map.of(
        "uid", DRIVER_UID,
        "kind", "COLLECTION_TYPE",
        "singularName", DRIVER_SINGULAR,
        "pluralName", DRIVER_PLURAL,
        "displayName", "Relations Driver",
        "description", "Driver content type with ONE_TO_ONE relation to license",
        "draftAndPublish", false,
        "fields", List.of(
            Map.of("name", "fullName", "type", "STRING", "required", true, "maxLength", 255)),
        "relations", List.of(
            Map.of(
                "fieldName", "license",
                "type", "ONE_TO_ONE",
                "target", LICENSE_UID,
                "targetAttribute", "driver")));

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/admin/content-type-builder/content-types")
        .then()
        .statusCode(201)
        .body("data.uid", is(DRIVER_UID))
        .body("data.relations[0].type", is("ONE_TO_ONE"));
  }

  // ======================================================================
  // ONE_TO_ONE — Entry Creation
  // ======================================================================

  @Test
  @Order(50)
  @DisplayName("should create a license entry")
  void createLicenseEntry() {
    licenseDocId = given()
        .contentType("application/json")
        .body(Map.of("licenseNumber", "DL-12345"))
        .when()
        .post("/api/" + LICENSE_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.licenseNumber", is("DL-12345"))
        .extract()
        .path("data.documentId");
  }

  @Test
  @Order(51)
  @DisplayName("should create a driver entry")
  void createDriverEntry() {
    driverDocId = given()
        .contentType("application/json")
        .body(Map.of("fullName", "John Doe"))
        .when()
        .post("/api/" + DRIVER_SINGULAR)
        .then()
        .statusCode(201)
        .body("data.documentId", notNullValue())
        .body("data.fullName", is("John Doe"))
        .extract()
        .path("data.documentId");
  }

  // ======================================================================
  // ONE_TO_ONE — List / Resolve Relation Fields
  // ======================================================================

  @Test
  @Order(52)
  @DisplayName("should list ONE_TO_ONE relation on driver")
  void listDriverRelations() {
    given()
        .when()
        .get("/api/" + DRIVER_SINGULAR + "/" + driverDocId + "/relations")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.size()", is(1))
        .body("data[0].fieldName", is("license"))
        .body("data[0].type", is("ONE_TO_ONE"))
        .body("data[0].target", is(LICENSE_UID))
        .body("data[0].bidirectional", is(true));
  }

  @Test
  @Order(53)
  @DisplayName("should return null for unresolved ONE_TO_ONE relation")
  void resolveDriverLicenseEmpty() {
    given()
        .when()
        .get("/api/" + DRIVER_SINGULAR + "/" + driverDocId + "/relations/license")
        .then()
        .statusCode(200)
        .body("data", nullValue());
  }

  // ======================================================================
  // Query parameters
  // ======================================================================

  @Test
  @Order(60)
  @DisplayName("should accept locale query param on MANY_TO_MANY list")
  void listManyToManyWithLocale() {
    given()
        .queryParam("locale", "en")
        .when()
        .get("/api/" + STUDENT_SINGULAR + "/" + student1DocId + "/relations")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  @Order(61)
  @DisplayName("should accept locale query param on ONE_TO_ONE resolve")
  void resolveOneToOneWithLocale() {
    given()
        .queryParam("locale", "en")
        .when()
        .get("/api/" + DRIVER_SINGULAR + "/" + driverDocId + "/relations/license")
        .then()
        .statusCode(200);
  }

  // ======================================================================
  // MANY_TO_MANY — Schema Cleanup
  // ======================================================================

  @Test
  @Order(80)
  @DisplayName("should delete student content type")
  void deleteStudentContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + STUDENT_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true));

    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + STUDENT_UID)
        .then()
        .statusCode(404);
  }

  @Test
  @Order(81)
  @DisplayName("should delete course content type")
  void deleteCourseContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + COURSE_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true));

    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + COURSE_UID)
        .then()
        .statusCode(404);
  }

  // ======================================================================
  // ONE_TO_ONE — Schema Cleanup
  // ======================================================================

  @Test
  @Order(82)
  @DisplayName("should delete driver content type")
  void deleteDriverContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + DRIVER_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true));

    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + DRIVER_UID)
        .then()
        .statusCode(404);
  }

  @Test
  @Order(83)
  @DisplayName("should delete license content type")
  void deleteLicenseContentType() {
    given()
        .when()
        .delete("/admin/content-type-builder/content-types/" + LICENSE_UID)
        .then()
        .statusCode(200)
        .body("deleted", is(true));

    given()
        .when()
        .get("/admin/content-type-builder/content-types/" + LICENSE_UID)
        .then()
        .statusCode(404);
  }
}
