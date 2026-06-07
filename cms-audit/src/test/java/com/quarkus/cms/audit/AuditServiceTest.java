package com.quarkus.cms.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Audit Log module.
 * <p>
 * Tests CmsAuditLog entity defaults, AuditService diff computation,
 * and query logic that doesn't require a database connection.
 */
class AuditServiceTest {

    // ---- CmsAuditLog entity defaults ----

    @Test
    void shouldHaveDefaultLocale() {
        var log = new CmsAuditLog();
        assertEquals("en", log.locale);
        assertNotNull(log.createdAt);
    }

    @Test
    void shouldSupportAssignment() {
        var log = new CmsAuditLog();
        String docId = UUID.randomUUID().toString();
        log.documentId = docId;
        log.entryId = 42L;
        log.contentType = "api::article.article";
        log.locale = "fr";
        log.action = "UPDATE";
        log.userId = 5L;
        log.createdAt = Instant.now();

        assertEquals(docId, log.documentId);
        assertEquals(42L, log.entryId);
        assertEquals("api::article.article", log.contentType);
        assertEquals("fr", log.locale);
        assertEquals("UPDATE", log.action);
        assertEquals(5L, log.userId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldSupportChangesAssignment() {
        var log = new CmsAuditLog();
        Map<String, Object> changes = new HashMap<>();
        Map<String, Object> titleChange = new HashMap<>();
        titleChange.put("old", "Old Title");
        titleChange.put("new", "New Title");
        changes.put("title", titleChange);

        log.changes = changes;
        assertNotNull(log.changes);
        assertEquals("Old Title", ((Map<String, Object>) log.changes.get("title")).get("old"));
        assertEquals("New Title", ((Map<String, Object>) log.changes.get("title")).get("new"));
    }

    @Test
    void shouldSupportSummaryAssignment() {
        var log = new CmsAuditLog();
        log.summary = "Updated title and body";
        assertEquals("Updated title and body", log.summary);
    }

    // ---- Diff computation ----

    @Test
    void shouldComputeEmptyDiffForIdenticalData() {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("title", "Hello");
        oldData.put("body", "World");

        Map<String, Object> newData = new HashMap<>();
        newData.put("title", "Hello");
        newData.put("body", "World");

        var diff = AuditService.computeDiff(oldData, newData);
        assertTrue(diff.isEmpty());
    }

    @Test
    void shouldDetectUpdatedFields() {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("title", "Old Title");
        oldData.put("body", "Same body");
        oldData.put("count", 1);

        Map<String, Object> newData = new HashMap<>();
        newData.put("title", "New Title");
        newData.put("body", "Same body");
        newData.put("count", 2);

        var diff = AuditService.computeDiff(oldData, newData);
        assertEquals(2, diff.size());
        assertTrue(diff.containsKey("title"));
        assertTrue(diff.containsKey("count"));

        @SuppressWarnings("unchecked")
        var titleChange = (Map<String, Object>) diff.get("title");
        assertEquals("Old Title", titleChange.get("old"));
        assertEquals("New Title", titleChange.get("new"));

        @SuppressWarnings("unchecked")
        var countChange = (Map<String, Object>) diff.get("count");
        assertEquals(1, countChange.get("old"));
        assertEquals(2, countChange.get("new"));
    }

    @Test
    void shouldDetectAddedFields() {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("title", "Hello");

        Map<String, Object> newData = new HashMap<>();
        newData.put("title", "Hello");
        newData.put("body", "New body");

        var diff = AuditService.computeDiff(oldData, newData);
        assertEquals(1, diff.size());
        assertTrue(diff.containsKey("body"));

        @SuppressWarnings("unchecked")
        var bodyChange = (Map<String, Object>) diff.get("body");
        assertNull(bodyChange.get("old"));
        assertEquals("New body", bodyChange.get("new"));
    }

    @Test
    void shouldDetectRemovedFields() {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("title", "Hello");
        oldData.put("body", "World");

        Map<String, Object> newData = new HashMap<>();
        newData.put("title", "Hello");

        var diff = AuditService.computeDiff(oldData, newData);
        assertEquals(1, diff.size());
        assertTrue(diff.containsKey("body"));

        @SuppressWarnings("unchecked")
        var bodyChange = (Map<String, Object>) diff.get("body");
        assertEquals("World", bodyChange.get("old"));
        assertNull(bodyChange.get("new"));
    }

    @Test
    void shouldHandleNullOldData() {
        Map<String, Object> newData = new HashMap<>();
        newData.put("title", "New");
        var diff = AuditService.computeDiff(null, newData);
        assertEquals(1, diff.size());
        @SuppressWarnings("unchecked")
        var change = (Map<String, Object>) diff.get("title");
        assertNull(change.get("old"));
    }

    @Test
    void shouldHandleNullNewData() {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("title", "Old");
        var diff = AuditService.computeDiff(oldData, null);
        assertEquals(1, diff.size());
        @SuppressWarnings("unchecked")
        var change = (Map<String, Object>) diff.get("title");
        assertNull(change.get("new"));
    }

    @Test
    void shouldHandleBothNull() {
        var diff = AuditService.computeDiff(null, null);
        assertTrue(diff.isEmpty());
    }

    @Test
    void shouldDetectNestedObjectChanges() {
        Map<String, Object> oldNested = new HashMap<>();
        oldNested.put("nested", Map.of("key1", "val1"));

        Map<String, Object> newNested = new HashMap<>();
        newNested.put("nested", Map.of("key1", "val2"));

        var diff = AuditService.computeDiff(oldNested, newNested);
        assertEquals(1, diff.size());
        assertTrue(diff.containsKey("nested"));
    }

    // ---- Diff edge cases ----

    @Test
    void shouldHandleEmptyMaps() {
        assertTrue(AuditService.computeDiff(new HashMap<>(), new HashMap<>()).isEmpty());
        assertTrue(AuditService.computeDiff(null, new HashMap<>()).isEmpty());
        assertTrue(AuditService.computeDiff(new HashMap<>(), null).isEmpty());
    }

    @Test
    void shouldHandleDifferentTypesAsChange() {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("count", 42);

        Map<String, Object> newData = new HashMap<>();
        newData.put("count", "42");

        var diff = AuditService.computeDiff(oldData, newData);
        assertEquals(1, diff.size(), "Type changes should be detected as changes");
    }
}
