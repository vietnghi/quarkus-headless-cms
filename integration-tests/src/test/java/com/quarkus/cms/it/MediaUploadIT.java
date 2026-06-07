package com.quarkus.cms.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for the Media Library REST API.
 *
 * <p>Covers file upload, listing, retrieval, search, deletion, replacement,
 * and folder CRUD operations.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Media Upload API")
class MediaUploadIT {

  private static final Map<String, Object> FOLDER_BODY =
      Map.of("name", "test-images");

  @BeforeEach
  void setUp() {
    // No DB cleanup needed — MediaResource manages its own data
  }

  @AfterAll
  static void cleanup() {
    cleanupTestFiles();
  }

  // ========================================================================
  // Folder CRUD
  // ========================================================================

  @Test
  @Order(1)
  @DisplayName("should create a folder")
  void createFolder() {
    given()
        .contentType("application/json")
        .body(FOLDER_BODY)
        .when()
        .post("/api/upload/folders")
        .then()
        .statusCode(201)
        .body("name", is("test-images"))
        .body("path", is("/test-images"))
        .body("fileCount", is(0));
  }

  @Test
  @Order(2)
  @DisplayName("should list folders")
  void listFolders() {
    given()
        .when()
        .get("/api/upload/folders")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("data.size()", greaterThanOrEqualTo(1));
  }

  @Test
  @Order(3)
  @DisplayName("should reject folder creation with empty name")
  void createFolderEmptyName() {
    given()
        .contentType("application/json")
        .body(Map.of("name", ""))
        .when()
        .post("/api/upload/folders")
        .then()
        .statusCode(400);
  }

  @Test
  @Order(4)
  @DisplayName("should reject duplicate folder path")
  void createDuplicateFolder() {
    given()
        .contentType("application/json")
        .body(FOLDER_BODY)
        .when()
        .post("/api/upload/folders")
        .then()
        .statusCode(409);
  }

  // ========================================================================
  // File Upload
  // ========================================================================

  @Test
  @Order(5)
  @DisplayName("should upload a text file")
  void uploadTextFile() throws IOException {
    Path tempFile = createTempFile("hello.txt", "Hello, CMS Media Library!");

    given()
        .multiPart("files", tempFile.toFile(), "text/plain")
        .when()
        .post("/api/upload")
        .then()
        .statusCode(200)
        .body("name", is("hello.txt"))
        .body("mimeType", is("text/plain"))
        .body("size", is("Hello, CMS Media Library!".length()))
        .body("hash", notNullValue())
        .body("url", notNullValue());
  }

  @Test
  @Order(6)
  @DisplayName("should upload multiple files")
  void uploadMultipleFiles() throws IOException {
    Path file1 = createTempFile("multi1.txt", "Content 1");
    Path file2 = createTempFile("multi2.txt", "Content 2");

    given()
        .multiPart("files", file1.toFile(), "text/plain")
        .multiPart("files", file2.toFile(), "text/plain")
        .when()
        .post("/api/upload")
        .then()
        .statusCode(200)
        .body("size()", is(2))
        .body("[0].name", is("multi1.txt"))
        .body("[1].name", is("multi2.txt"))
        .body("meta.total", is(2))
        .body("meta.succeeded", is(2));
  }

  @Test
  @Order(7)
  @DisplayName("should return 400 when no files are uploaded")
  void uploadNoFiles() {
    given()
        .contentType("multipart/form-data")
        .when()
        .post("/api/upload")
        .then()
        .statusCode(400)
        .body("error", is("No files uploaded"));
  }

  // ========================================================================
  // File Listing & Retrieval
  // ========================================================================

  @Test
  @Order(8)
  @DisplayName("should list all uploaded files with pagination")
  void listFiles() {
    given()
        .when()
        .get("/api/upload/files?page=0&pageSize=10")
        .then()
        .statusCode(200)
        .body("data", notNullValue())
        .body("total", greaterThanOrEqualTo(1))
        .body("page", is(0))
        .body("pageSize", is(10));
  }

  @Test
  @Order(9)
  @DisplayName("should get a single file by ID")
  void getFileById() throws IOException {
    Path tempFile = createTempFile("get-me.txt", "Get me!");
    int fileId = uploadAndGetId(tempFile);

    given()
        .when()
        .get("/api/upload/files/" + fileId)
        .then()
        .statusCode(200)
        .body("id", is(fileId))
        .body("name", is("get-me.txt"));
  }

  @Test
  @Order(10)
  @DisplayName("should return 404 for non-existent file")
  void getFileNotFound() {
    given()
        .when()
        .get("/api/upload/files/99999")
        .then()
        .statusCode(404)
        .body("error", is("File not found: 99999"));
  }

  // ========================================================================
  // File Search
  // ========================================================================

  @Test
  @Order(11)
  @DisplayName("should search files by name")
  void searchFiles() throws IOException {
    Path tempFile = createTempFile("unique-search-term.txt", "Searchable");
    uploadAndGetId(tempFile);

    given()
        .queryParam("q", "unique-search-term")
        .when()
        .get("/api/upload/files/search")
        .then()
        .statusCode(200)
        .body("data", hasSize(greaterThanOrEqualTo(1)))
        .body("data[0].name", is("unique-search-term.txt"));
  }

  @Test
  @Order(12)
  @DisplayName("should return 400 for empty search query")
  void searchEmptyQuery() {
    given()
        .queryParam("q", "")
        .when()
        .get("/api/upload/files/search")
        .then()
        .statusCode(400);
  }

  // ========================================================================
  // File Deletion
  // ========================================================================

  @Test
  @Order(13)
  @DisplayName("should delete a file")
  void deleteFile() throws IOException {
    Path tempFile = createTempFile("delete-me.txt", "Delete me!");
    int fileId = uploadAndGetId(tempFile);

    given()
        .when()
        .delete("/api/upload/files/" + fileId)
        .then()
        .statusCode(200)
        .body("id", is(fileId))
        .body("deleted", is(true));

    // Verify it's gone
    given()
        .when()
        .get("/api/upload/files/" + fileId)
        .then()
        .statusCode(404);
  }

  @Test
  @Order(14)
  @DisplayName("should return 404 when deleting non-existent file")
  void deleteNonExistentFile() {
    given()
        .when()
        .delete("/api/upload/files/99999")
        .then()
        .statusCode(404);
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private static Path createTempFile(String name, String content) throws IOException {
    Path path = Path.of(System.getProperty("java.io.tmpdir"), "cms-it-" + name);
    Files.writeString(path, content);
    return path;
  }

  private int uploadAndGetId(Path path) {
    return given()
        .multiPart("files", path.toFile())
        .when()
        .post("/api/upload")
        .then()
        .statusCode(200)
        .extract()
        .path("id");
  }

  private static void cleanupTestFiles() {
    try {
      Files.list(Path.of(System.getProperty("java.io.tmpdir")))
          .filter(p -> p.getFileName().toString().startsWith("cms-it-"))
          .forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
          });
    } catch (IOException ignored) {}
  }
}
