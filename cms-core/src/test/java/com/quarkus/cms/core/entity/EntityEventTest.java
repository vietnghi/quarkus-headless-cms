package com.quarkus.cms.core.entity;

import com.quarkus.cms.core.domain.CmsEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EntityEvent} — CDI event payload for entity lifecycle hooks.
 */
class EntityEventTest {

    private final String contentType = "api::article.article";
    private final String documentId = UUID.randomUUID().toString();
    private final String locale = "en";
    private final Map<String, Object> data = Map.of("title", "Hello", "body", "World");
    private final Long userId = 42L;

    // ---- Factory method tests ----

    @Test
    void shouldCreateBeforeCreateEvent() {
        EntityEvent event = EntityEvent.beforeCreate(contentType, data, locale, userId);

        assertEquals(EntityEvent.EventType.CREATE, event.getEventType());
        assertEquals(EntityEvent.Phase.BEFORE, event.getPhase());
        assertEquals(contentType, event.getContentType());
        assertNull(event.getDocumentId());
        assertEquals(locale, event.getLocale());
        assertEquals(data, event.getData());
        assertEquals(userId, event.getUserId());
        assertNull(event.getEntry());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void shouldCreateAfterCreateEvent() {
        CmsEntry entry = createEntry("draft", 0);
        EntityEvent event = EntityEvent.afterCreate(contentType, entry, data, locale, userId);

        assertEquals(EntityEvent.EventType.CREATE, event.getEventType());
        assertEquals(EntityEvent.Phase.AFTER, event.getPhase());
        assertEquals(contentType, event.getContentType());
        assertEquals(entry.documentId, event.getDocumentId());
        assertEquals(entry, event.getEntry());
        assertEquals(data, event.getData());
        assertEquals(locale, event.getLocale());
        assertEquals(userId, event.getUserId());
    }

    @Test
    void shouldCreateBeforeUpdateEvent() {
        EntityEvent event = EntityEvent.beforeUpdate(contentType, documentId, data, locale, userId);

        assertEquals(EntityEvent.EventType.UPDATE, event.getEventType());
        assertEquals(EntityEvent.Phase.BEFORE, event.getPhase());
        assertEquals(contentType, event.getContentType());
        assertEquals(documentId, event.getDocumentId());
        assertEquals(data, event.getData());
    }

    @Test
    void shouldCreateAfterUpdateEvent() {
        CmsEntry entry = createEntry("draft", 0);
        EntityEvent event = EntityEvent.afterUpdate(contentType, entry, data, locale, userId);

        assertEquals(EntityEvent.EventType.UPDATE, event.getEventType());
        assertEquals(EntityEvent.Phase.AFTER, event.getPhase());
        assertEquals(entry, event.getEntry());
    }

    @Test
    void shouldCreateDeleteEvents() {
        EntityEvent before = EntityEvent.beforeDelete(contentType, documentId, locale, userId);
        assertEquals(EntityEvent.EventType.DELETE, before.getEventType());
        assertEquals(EntityEvent.Phase.BEFORE, before.getPhase());

        EntityEvent after = EntityEvent.afterDelete(contentType, documentId, locale, userId);
        assertEquals(EntityEvent.EventType.DELETE, after.getEventType());
        assertEquals(EntityEvent.Phase.AFTER, after.getPhase());
        assertEquals(documentId, after.getDocumentId());
    }

    @Test
    void shouldCreatePublishEvents() {
        CmsEntry entry = createEntry("published", 1);

        EntityEvent before = EntityEvent.beforePublish(contentType, documentId, locale, userId);
        assertEquals(EntityEvent.EventType.PUBLISH, before.getEventType());
        assertEquals(EntityEvent.Phase.BEFORE, before.getPhase());

        EntityEvent after = EntityEvent.afterPublish(contentType, entry, locale, userId);
        assertEquals(EntityEvent.EventType.PUBLISH, after.getEventType());
        assertEquals(EntityEvent.Phase.AFTER, after.getPhase());
        assertEquals(entry, after.getEntry());
        assertEquals(entry.data, after.getData());
    }

    @Test
    void shouldCreateUnpublishEvents() {
        EntityEvent before = EntityEvent.beforeUnpublish(contentType, documentId, locale, userId);
        assertEquals(EntityEvent.EventType.UNPUBLISH, before.getEventType());

        EntityEvent after = EntityEvent.afterUnpublish(contentType, documentId, locale, userId);
        assertEquals(EntityEvent.EventType.UNPUBLISH, after.getEventType());
    }

    @Test
    void shouldCreateFindEvents() {
        CmsEntry entry = createEntry("draft", 0);

        EntityEvent beforeOne = EntityEvent.beforeFindOne(contentType, documentId, locale);
        assertEquals(EntityEvent.EventType.FIND_ONE, beforeOne.getEventType());
        assertEquals(EntityEvent.Phase.BEFORE, beforeOne.getPhase());

        EntityEvent afterOne = EntityEvent.afterFindOne(contentType, documentId, data, locale, entry);
        assertEquals(EntityEvent.EventType.FIND_ONE, afterOne.getEventType());
        assertEquals(EntityEvent.Phase.AFTER, afterOne.getPhase());
        assertEquals(entry, afterOne.getEntry());

        EntityEvent beforeMany = EntityEvent.beforeFindMany(contentType);
        assertEquals(EntityEvent.EventType.FIND_MANY, beforeMany.getEventType());

        EntityEvent afterMany = EntityEvent.afterFindMany(contentType);
        assertEquals(EntityEvent.EventType.FIND_MANY, afterMany.getEventType());
    }

    // ---- Event key ----

    @Test
    void shouldGenerateCorrectEventKeys() {
        assertEquals("entry.create.before",
                EntityEvent.beforeCreate(contentType, data, locale, userId).toEventKey());
        assertEquals("entry.create.after",
                EntityEvent.afterCreate(contentType, createEntry("draft", 0), data, locale, userId).toEventKey());
        assertEquals("entry.update.before",
                EntityEvent.beforeUpdate(contentType, documentId, data, locale, userId).toEventKey());
        assertEquals("entry.delete.after",
                EntityEvent.afterDelete(contentType, documentId, locale, userId).toEventKey());
        assertEquals("entry.publish.before",
                EntityEvent.beforePublish(contentType, documentId, locale, userId).toEventKey());
        assertEquals("entry.unpublish.after",
                EntityEvent.afterUnpublish(contentType, documentId, locale, userId).toEventKey());
        assertEquals("entry.find_one.before",
                EntityEvent.beforeFindOne(contentType, documentId, locale).toEventKey());
    }

    // ---- Timestamps ----

    @Test
    void shouldSetTimestampOnCreation() {
        Instant before = Instant.now();
        EntityEvent event = EntityEvent.beforeCreate(contentType, data, locale, userId);
        Instant after = Instant.now();

        assertNotNull(event.getTimestamp());
        assertTrue(!event.getTimestamp().isBefore(before) && !event.getTimestamp().isAfter(after));
    }

    // ---- Edge cases ----

    @Test
    void shouldHandleNullData() {
        EntityEvent event = EntityEvent.beforeCreate(contentType, null, locale, userId);
        assertNull(event.getData());
    }

    @Test
    void shouldHandleNullUserId() {
        EntityEvent event = EntityEvent.beforeCreate(contentType, data, locale, null);
        assertNull(event.getUserId());
    }

    @Test
    void shouldHandleNullLocale() {
        EntityEvent event = EntityEvent.beforeCreate(contentType, data, null, userId);
        assertNull(event.getLocale());
    }

    // ---- Helper ----

    private CmsEntry createEntry(String status, int versionNumber) {
        CmsEntry entry = new CmsEntry();
        entry.documentId = UUID.randomUUID().toString();
        entry.contentType = contentType;
        entry.locale = locale;
        entry.status = status;
        entry.versionNumber = versionNumber;
        entry.data = new HashMap<>(data);
        entry.createdById = userId;
        return entry;
    }
}
