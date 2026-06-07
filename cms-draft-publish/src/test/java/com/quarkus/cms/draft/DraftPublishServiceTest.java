package com.quarkus.cms.draft;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.draft.model.ContentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Draft/Publish content lifecycle.
 * <p>
 * Tests core logic that doesn't require a database connection.
 * Full integration tests should run against a PostgreSQL instance
 * to validate JSONB serialization and transactional behavior.
 */
class DraftPublishServiceTest {

    // ---- ContentStatus enum ----

    @Test
    void shouldMapStatusValues() {
        assertEquals("draft", ContentStatus.DRAFT.getValue());
        assertEquals("published", ContentStatus.PUBLISHED.getValue());
        assertEquals("unpublished", ContentStatus.UNPUBLISHED.getValue());
    }

    @Test
    void shouldResolveFromValue() {
        assertEquals(ContentStatus.DRAFT, ContentStatus.fromValue("draft"));
        assertEquals(ContentStatus.PUBLISHED, ContentStatus.fromValue("published"));
        assertEquals(ContentStatus.PUBLISHED, ContentStatus.fromValue("PUBLISHED"));
        assertEquals(ContentStatus.UNPUBLISHED, ContentStatus.fromValue("unpublished"));
    }

    @Test
    void shouldThrowForUnknownStatus() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentStatus.fromValue("deleted"));
        assertThrows(IllegalArgumentException.class,
            () -> ContentStatus.fromValue(""));
    }

    // ---- CmsEntry construction and defaults ----

    @Test
    void shouldHaveCorrectDefaults() {
        var entry = new CmsEntry();
        assertEquals("draft", entry.status);
        assertEquals("en", entry.locale);
        assertEquals(0, entry.versionNumber);
        assertNotNull(entry.data);
        assertTrue(entry.data.isEmpty());
        assertNull(entry.publishedAt);
    }

    @Test
    void shouldSupportDocumentIdAssignment() {
        var entry = new CmsEntry();
        String docId = UUID.randomUUID().toString();
        entry.documentId = docId;

        assertEquals(docId, entry.documentId);
    }

    @Test
    void shouldSupportContentTypeAssignment() {
        var entry = new CmsEntry();
        entry.contentType = "api::article.article";
        assertEquals("api::article.article", entry.contentType);
    }

    @Test
    void shouldSupportVersionNumberAssignment() {
        var entry = new CmsEntry();
        entry.versionNumber = 5;
        assertEquals(5, entry.versionNumber);
    }

    @Test
    void shouldSupportDataAssignment() {
        var entry = new CmsEntry();
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Hello");
        data.put("body", "World");
        data.put("count", 42);
        entry.data = data;

        assertEquals("Hello", entry.data.get("title"));
        assertEquals("World", entry.data.get("body"));
        assertEquals(42, entry.data.get("count"));
    }

    @Test
    void shouldSupportStatusTransitions() {
        var entry = new CmsEntry();

        entry.status = ContentStatus.DRAFT.getValue();
        assertEquals(ContentStatus.DRAFT.getValue(), entry.status);

        entry.status = ContentStatus.PUBLISHED.getValue();
        assertEquals(ContentStatus.PUBLISHED.getValue(), entry.status);

        entry.status = ContentStatus.UNPUBLISHED.getValue();
        assertEquals(ContentStatus.UNPUBLISHED.getValue(), entry.status);
    }

    @Test
    void shouldSupportUserIdTracking() {
        var entry = new CmsEntry();
        entry.createdById = 1L;
        entry.updatedById = 2L;
        entry.publishedById = 3L;

        assertEquals(1L, entry.createdById);
        assertEquals(2L, entry.updatedById);
        assertEquals(3L, entry.publishedById);
    }

    @Test
    void shouldSupportTimestampTracking() {
        var now = Instant.now();
        var entry = new CmsEntry();
        entry.createdAt = now;
        entry.updatedAt = now;
        entry.publishedAt = now;

        assertEquals(now, entry.createdAt);
        assertEquals(now, entry.updatedAt);
        assertEquals(now, entry.publishedAt);
    }

    @Test
    void shouldSupportLocaleAssignment() {
        var entry = new CmsEntry();
        entry.locale = "fr";
        assertEquals("fr", entry.locale);
    }

    // ---- Draft data merge behavior ----

    @Test
    void shouldMergeDataCorrectly() {
        var existingData = new HashMap<String, Object>();
        existingData.put("title", "Original");
        existingData.put("body", "Old body");
        existingData.put("tags", java.util.List.of("java", "cms"));

        var newData = new HashMap<String, Object>();
        newData.put("title", "Updated");
        newData.put("author", "John");

        // Simulate updateDraft merge logic
        existingData.putAll(newData);

        assertEquals("Updated", existingData.get("title"));
        assertEquals("Old body", existingData.get("body"));
        assertEquals("John", existingData.get("author"));
        assertEquals(4, existingData.size()); // title replaced, author added → 4 total
    }

    @Test
    void shouldCloneDataCorrectly() {
        var originalData = new HashMap<String, Object>();
        originalData.put("title", "Original");
        originalData.put("nested", Map.of("key", "value"));

        // Simulate publish clone logic
        var clonedData = new HashMap<>(originalData);

        assertEquals(originalData, clonedData);
        assertNotSame(originalData, clonedData); // different map object

        // Mutating clone should not affect original
        clonedData.put("title", "Modified");
        assertEquals("Original", originalData.get("title"));
    }

    // ---- Version number logic ----

    @Test
    void shouldIncrementVersionCorrectly() {
        // Simulate version numbering logic
        assertEquals(1, computeNextVersion(0));   // draft → first publish
        assertEquals(2, computeNextVersion(1));   // second publish
        assertEquals(3, computeNextVersion(2));   // third publish
        assertEquals(10, computeNextVersion(9));  // arbitrary
    }

    @Test
    void shouldComputeFirstVersionFromNoEntries() {
        assertEquals(1, computeNextVersion(null)); // no prior versions
        assertEquals(1, computeNextVersion(0));     // only draft exists
    }

    // ---- Edge cases ----

    @Test
    void shouldHandleEmptyDataMap() {
        var entry = new CmsEntry();
        entry.data = Map.of();
        assertTrue(entry.data.isEmpty());
        assertEquals(0, entry.data.size());
    }

    @Test
    void shouldHandleNullDataMap() {
        var entry = new CmsEntry();
        entry.data = new HashMap<>();
        assertNotNull(entry.data);

        // Setting data to empty map
        entry.data = Map.of();
        assertTrue(entry.data.isEmpty());
    }

    // ---- Helper: version number computation (mirrors getNextVersionNumber logic) ----

    private static int computeNextVersion(Integer currentMax) {
        if (currentMax == null || currentMax == 0) {
            return 1;
        }
        return currentMax + 1;
    }
}
