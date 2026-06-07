package com.quarkus.cms.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CmsEntry} — default values, data model behavior, lifecycle hooks.
 */
class CmsEntryTest {

    @Test
    void shouldHaveDefaultValuesOnCreation() {
        CmsEntry entry = new CmsEntry();
        assertNull(entry.id);
        assertEquals("en", entry.locale);
        assertEquals("draft", entry.status);
        // Fields initialized inline: versionNumber=0, createdAt=Instant.now()
        assertEquals(0, entry.versionNumber.intValue());
        assertNotNull(entry.createdAt);
        assertNotNull(entry.updatedAt);
        assertNotNull(entry.data);
        assertTrue(entry.data.isEmpty());
    }

    @Test
    void shouldHaveNullOptionalFields() {
        CmsEntry entry = new CmsEntry();
        assertNull(entry.documentId);
        assertNull(entry.contentType);
        assertNull(entry.publishedAt);
        assertNull(entry.createdById);
        assertNull(entry.updatedById);
        assertNull(entry.publishedById);
    }

    @Test
    void shouldPopulateDataMap() {
        Map<String, Object> data = new HashMap<>(Map.of("title", "Hello", "views", 42));
        CmsEntry entry = new CmsEntry();
        entry.documentId = "doc-1";
        entry.contentType = "api::article.article";
        entry.data = data;

        assertEquals("doc-1", entry.documentId);
        assertEquals("api::article.article", entry.contentType);
        assertEquals("Hello", entry.data.get("title"));
        assertEquals(42, entry.data.get("views"));
    }

    @Test
    void onCreateShouldSetTimestamps() {
        CmsEntry entry = new CmsEntry();
        entry.createdAt = null;
        entry.updatedAt = null;
        entry.onCreate();

        assertNotNull(entry.createdAt);
        assertNotNull(entry.updatedAt);
        // Both should be within a few milliseconds of each other
        long diff = java.time.Duration.between(entry.createdAt, entry.updatedAt).abs().toMillis();
        assertTrue(diff < 100, "createdAt and updatedAt should be set close together");
    }

    @Test
    void shouldUpdateTimestampOnUpdate() {
        CmsEntry entry = new CmsEntry();
        entry.createdAt = Instant.now().minusSeconds(3600);
        entry.updatedAt = entry.createdAt;

        entry.onUpdate();

        assertNotNull(entry.updatedAt);
        assertTrue(entry.updatedAt.isAfter(entry.createdAt));
    }

    @Test
    void shouldSupportAllStatusValues() {
        CmsEntry entry = new CmsEntry();
        assertEquals("draft", entry.status);

        entry.status = "published";
        assertEquals("published", entry.status);

        entry.status = "unpublished";
        assertEquals("unpublished", entry.status);
    }

    @Test
    void shouldSupportLocaleChanges() {
        CmsEntry entry = new CmsEntry();
        assertEquals("en", entry.locale);

        entry.locale = "fr";
        assertEquals("fr", entry.locale);
    }

    @Test
    void shouldSupportVersionNumber() {
        CmsEntry entry = new CmsEntry();
        assertEquals(0, entry.versionNumber.intValue());

        entry.versionNumber = 3;
        assertEquals(3, entry.versionNumber.intValue());
    }

    @Test
    void shouldSupportPublishedAt() {
        CmsEntry entry = new CmsEntry();
        assertNull(entry.publishedAt);

        Instant now = Instant.now();
        entry.publishedAt = now;
        assertEquals(now, entry.publishedAt);
    }
}
