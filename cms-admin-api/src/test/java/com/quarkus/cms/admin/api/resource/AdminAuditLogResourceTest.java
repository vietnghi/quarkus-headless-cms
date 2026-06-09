package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.audit.AuditService;
import com.quarkus.cms.audit.CmsAuditLog;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Admin Audit Log REST resource and AuditService filtered queries.
 * <p>
 * Seeds audit log entries directly via Panache to avoid the @PermissionCheck
 * interceptor then tests the resource layer via CDI injection.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminAuditLogResourceTest {

    @Inject
    AuditService auditService;

    @Inject
    AdminAuditLogResource adminAuditLogResource;

    @BeforeAll
    @Transactional
    void seedData() {
        CmsAuditLog.deleteAll();

        // Seed entries with various actions, users, and timestamps
        Instant now = Instant.now();
        String doc1 = "doc-audit-001";
        String doc2 = "doc-audit-002";

        createLog(doc1, "api::article.article", "en", "CREATE", 1L, "Created article", now.minus(10, ChronoUnit.DAYS));
        createLog(doc1, "api::article.article", "en", "UPDATE", 1L, "Updated title", now.minus(5, ChronoUnit.DAYS));
        createLog(doc1, "api::article.article", "en", "PUBLISH", 2L, "Published article", now.minus(2, ChronoUnit.DAYS));
        createLog(doc2, "api::page.page", "en", "CREATE", 2L, "Created page", now.minus(1, ChronoUnit.DAYS));
        createLog(doc2, "api::page.page", "en", "UPDATE", 1L, "Updated body", now.minus(6, ChronoUnit.HOURS));
        createLog(doc2, "api::page.page", "fr", "UPDATE", 3L, "French update", now.minus(1, ChronoUnit.HOURS));
    }

    private void createLog(String docId, String contentType, String locale,
                            String action, Long userId, String summary,
                            Instant createdAt) {
        CmsAuditLog log = new CmsAuditLog();
        log.documentId = docId;
        log.contentType = contentType;
        log.locale = locale;
        log.action = action;
        log.userId = userId;
        log.summary = summary;
        log.createdAt = createdAt;
        log.changes = Map.of("field", Map.of("old", "oldVal", "new", "newVal"));
        log.persist();
    }

    @AfterAll
    @Transactional
    void cleanup() {
        CmsAuditLog.deleteAll();
    }

    // ---- AuditService filtered queries ----

    @Test
    @Order(1)
    void testCountFilteredAll() {
        long total = auditService.countFiltered(null, null, null, null, null);
        assertEquals(6, total, "Should count all 6 seeded entries");
    }

    @Test
    @Order(2)
    void testCountFilteredByAction() {
        long count = auditService.countFiltered("UPDATE", null, null, null, null);
        assertEquals(3, count, "Should count 3 UPDATE actions");
    }

    @Test
    @Order(3)
    void testCountFilteredByUser() {
        long count = auditService.countFiltered(null, 1L, null, null, null);
        assertEquals(3, count, "User 1 should have 3 entries");
    }

    @Test
    @Order(4)
    void testCountFilteredByContentType() {
        long count = auditService.countFiltered(null, null, "api::article.article", null, null);
        assertEquals(3, count, "Article content type should have 3 entries");
    }

    @Test
    @Order(5)
    void testCountFilteredByDateRange() {
        Instant now = Instant.now();
        String start = now.minus(3, ChronoUnit.DAYS).toString();
        String end = now.plus(1, ChronoUnit.DAYS).toString();
        long count = auditService.countFiltered(null, null, null, start, end);
        assertEquals(4, count, "Should count 4 entries in last 3 days");
    }

    @Test
    @Order(6)
    void testFindFilteredWithPagination() {
        List<CmsAuditLog> logs = auditService.findFiltered(null, null, null, null, null, 0, 2);
        assertEquals(2, logs.size(), "Page 0 should have 2 entries");
        assertTrue(logs.get(0).createdAt.isAfter(logs.get(1).createdAt) ||
            logs.get(0).createdAt.equals(logs.get(1).createdAt),
            "Entries should be ordered by createdAt desc");
    }

    @Test
    @Order(7)
    void testFindFilteredSecondPage() {
        List<CmsAuditLog> logs = auditService.findFiltered(null, null, null, null, null, 1, 2);
        assertEquals(2, logs.size(), "Page 1 should have 2 entries");
    }

    @Test
    @Order(8)
    void testFindFilteredByAction() {
        List<CmsAuditLog> logs = auditService.findFiltered("PUBLISH", null, null, null, null, 0, 10);
        assertEquals(1, logs.size(), "Should find 1 PUBLISH entry");
        assertEquals("Published article", logs.get(0).summary);
    }

    @Test
    @Order(9)
    void testCountFilteredByMultiple() {
        long count = auditService.countFiltered("UPDATE", 1L, null, null, null);
        assertEquals(2, count, "User 1 should have 2 UPDATE entries");
    }

    @Test
    @Order(10)
    void testFindFilteredWithAllFilters() {
        Instant now = Instant.now();
        String start = now.minus(7, ChronoUnit.DAYS).toString();
        String end = now.plus(1, ChronoUnit.DAYS).toString();
        List<CmsAuditLog> logs = auditService.findFiltered(
            "UPDATE", null, "api::page.page", start, end, 0, 10);
        assertEquals(2, logs.size(), "Should find 2 PAGE UPDATE entries in range");
    }

    // ---- DELETE operations ----

    @Test
    @Order(11)
    @Transactional
    void testDeleteOlderThan() {
        long beforeDelete = auditService.countFiltered(null, null, null, null, null);
        assertEquals(6, beforeDelete);

        long deleted = auditService.deleteOlderThan(7);
        assertEquals(1, deleted, "Should delete 1 entry older than 7 days");

        long remaining = auditService.countFiltered(null, null, null, null, null);
        assertEquals(5, remaining);
    }

    @Test
    @Order(12)
    @Transactional
    void testDeleteFiltered() {
        // Should still have 5 entries from testDeleteOlderThan
        long deleted = auditService.deleteFiltered("PUBLISH", null, null, 1);
        assertEquals(1, deleted, "Should delete 1 PUBLISH entry older than 1 day");

        long remaining = auditService.countFiltered(null, null, null, null, null);
        assertEquals(4, remaining);
    }
}
