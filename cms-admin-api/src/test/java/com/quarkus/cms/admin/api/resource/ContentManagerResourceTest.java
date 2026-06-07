package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.*;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.draft.model.ContentStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Content Manager admin service layer.
 * <p>
 * Uses CDI injection to test the service directly without REST endpoints.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContentManagerResourceTest {

    @Inject
    ContentManagerService contentManager;

    @Inject
    SchemaStorageService schemaService;

    private static final String CT_UID = "api::test.test";
    private String documentId;
    private static final String LOCALE = "en";

    @BeforeAll
    @Transactional
    void setupContentType() {
        // Register a test content type
        ContentTypeDefinition ct = ContentTypeDefinition.builder(CT_UID, ContentTypeKind.COLLECTION_TYPE)
            .singularName("test")
            .pluralName("tests")
            .displayName("Test")
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).required(true).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()
            ))
            .relations(List.of(
                RelationDefinition.builder("relatedTest", RelationType.MANY_TO_MANY, "*").build(),
                RelationDefinition.builder("links", RelationType.MANY_TO_MANY, "*").build()
            ))
            .draftAndPublish(true)
            .build();

        schemaService.registerContentType(ct, "Test setup", "test-user");
    }

    // ---- Create ----

    @Test
    @Order(1)
    void shouldCreateEntry() {
        CmsEntry entry = contentManager.createEntry(
            CT_UID,
            Map.of("title", "Hello World", "body", "Test body"),
            LOCALE, 1L);

        assertNotNull(entry);
        assertNotNull(entry.documentId);
        assertEquals(CT_UID, entry.contentType);
        assertEquals(ContentStatus.DRAFT.getValue(), entry.status);
        assertEquals("Hello World", entry.data.get("title"));

        documentId = entry.documentId;
    }

    @Test
    @Order(2)
    void shouldRejectCreateForUnknownContentType() {
        assertThrows(IllegalArgumentException.class, () ->
            contentManager.createEntry("api::unknown.unknown",
                Map.of("title", "Bad"), LOCALE, 1L));
    }

    // ---- List ----

    @Test
    @Order(3)
    void shouldListEntries() {
        List<CmsEntry> entries = contentManager.listEntries(CT_UID, null, LOCALE, 0, 20);
        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().anyMatch(e -> e.documentId.equals(documentId)));
    }

    @Test
    @Order(4)
    void shouldListEntriesWithStatusFilter() {
        List<CmsEntry> entries = contentManager.listEntries(CT_UID, "draft", LOCALE, 0, 20);
        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().allMatch(e -> "draft".equals(e.status)));
    }

    @Test
    @Order(5)
    void shouldCountEntries() {
        long count = contentManager.countEntries(CT_UID, null, LOCALE);
        assertTrue(count >= 1);
    }

    // ---- Get single entry ----

    @Test
    @Order(6)
    void shouldGetEntry() {
        CmsEntry entry = contentManager.getEntry(documentId, LOCALE);
        assertNotNull(entry);
        assertEquals(documentId, entry.documentId);
        assertEquals(CT_UID, entry.contentType);
        assertEquals(ContentStatus.DRAFT.getValue(), entry.status);
    }

    @Test
    @Order(7)
    void shouldReturnNullForNonExistentEntry() {
        CmsEntry entry = contentManager.getEntry("nonexistent-id", LOCALE);
        assertNull(entry);
    }

    // ---- Update ----

    @Test
    @Order(8)
    void shouldUpdateEntry() {
        CmsEntry entry = contentManager.updateEntry(
            documentId,
            Map.of("title", "Updated Title", "body", "Updated body"),
            LOCALE, 1L);

        assertNotNull(entry);
        assertEquals("Updated Title", entry.data.get("title"));
        assertEquals("Updated body", entry.data.get("body"));

        // Verify persisted
        CmsEntry fetched = contentManager.getEntry(documentId, LOCALE);
        assertEquals("Updated Title", fetched.data.get("title"));
    }

    // ---- Version history ----

    @Test
    @Order(9)
    void shouldGetVersions() {
        List<CmsEntry> versions = contentManager.getVersions(documentId, LOCALE);
        assertFalse(versions.isEmpty());
    }

    // ---- Publish ----

    @Test
    @Order(10)
    void shouldPublishEntry() {
        CmsEntry published = contentManager.publishEntry(documentId, LOCALE, 1L);

        assertNotNull(published);
        assertEquals(ContentStatus.PUBLISHED.getValue(), published.status);
        assertTrue(published.versionNumber > 0);
        assertEquals("Updated Title", published.data.get("title"));
    }

    @Test
    @Order(11)
    void shouldRejectPublishWithoutDraft() {
        // Create a separate entry for this test to avoid breaking other tests
        CmsEntry entry = contentManager.createEntry(CT_UID,
            Map.of("title", "Publish test"), LOCALE, 1L);
        contentManager.publishEntry(entry.documentId, LOCALE, 1L);
        contentManager.discardDraft(entry.documentId, LOCALE);

        // Now there's no draft — publish should fail
        assertThrows(IllegalStateException.class, () ->
            contentManager.publishEntry(entry.documentId, LOCALE, 1L));

        // Clean up
        contentManager.deleteDocument(entry.documentId);
    }

    // ---- Unpublish ----

    @Test
    @Order(12)
    void shouldUnpublishEntry() {
        contentManager.unpublishEntry(documentId, LOCALE);
    }

    // ---- Discard Draft ----

    @Test
    @Order(13)
    void shouldDiscardDraft() {
        // Create a new draft first
        CmsEntry draft = contentManager.updateEntry(
            documentId,
            Map.of("title", "Draft to discard"),
            LOCALE, 1L);
        assertNotNull(draft);

        // Discard it
        contentManager.discardDraft(documentId, LOCALE);

        // After discard, getEntry returns published (since draft is gone)
        CmsEntry published = contentManager.getEntry(documentId, LOCALE);
        assertNotNull(published);
        assertNotEquals("draft", published.status);
    }

    // ---- Relations ----

    @Test
    @Order(14)
    void shouldManageRelations() {
        // Attach a relation
        var rel = contentManager.attachRelation(
            documentId, CT_UID,
            "target-doc-123", "api::article.article",
            "relatedTest", 0);

        assertNotNull(rel);
        assertNotNull(rel.id);
        assertEquals("relatedTest", rel.fieldName);

        // Find relations
        var relations = contentManager.findRelations(documentId, "relatedTest");
        assertEquals(1, relations.size());
        assertEquals("target-doc-123", relations.get(0).targetDocumentId);

        // Detach
        contentManager.detachRelation(documentId, "relatedTest", "target-doc-123");

        var afterDelete = contentManager.findRelations(documentId, "relatedTest");
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    @Order(15)
    void shouldReorderRelations() {
        contentManager.attachRelation(documentId, CT_UID, "target-a", "api::article.article", "links", 0);
        contentManager.attachRelation(documentId, CT_UID, "target-b", "api::article.article", "links", 1);

        contentManager.reorderRelations(documentId, "links", List.of("target-b", "target-a"));

        var relations = contentManager.findRelations(documentId, "links");
        assertEquals(2, relations.size());
        assertEquals("target-b", relations.get(0).targetDocumentId);
        assertEquals(0, relations.get(0).orderIndex);
    }

    // ---- Single Type Tests ----

    @Test
    @Order(20)
    void shouldHandleSingleTypeLifecycle() {
        String singleUid = "api::homepage.homepage";

        ContentTypeDefinition ct = ContentTypeDefinition.builder(singleUid, ContentTypeKind.SINGLE_TYPE)
            .singularName("homepage")
            .pluralName("homepages")
            .displayName("Homepage")
            .fields(List.of(
                FieldDefinition.builder("heroTitle", FieldType.STRING).build()
            ))
            .build();

        schemaService.registerContentType(ct, "Test setup", "test-user");

        // Initially null
        assertNull(contentManager.getSingleTypeEntry(singleUid, LOCALE));

        // Create via upsert
        CmsEntry created = contentManager.upsertSingleType(singleUid,
            Map.of("heroTitle", "Welcome!"), LOCALE, 1L);
        assertNotNull(created);
        assertEquals("Welcome!", created.data.get("heroTitle"));

        // Update via upsert
        CmsEntry updated = contentManager.upsertSingleType(singleUid,
            Map.of("heroTitle", "Updated Welcome!"), LOCALE, 1L);
        assertEquals("Updated Welcome!", updated.data.get("heroTitle"));

        // Second create should fail
        assertThrows(IllegalStateException.class, () ->
            contentManager.createEntry(singleUid, Map.of("heroTitle", "Duplicate"), LOCALE, 1L));
    }

    // ---- Delete ----

    @Test
    @Order(30)
    void shouldDeleteDocument() {
        long deleted = contentManager.deleteDocument(documentId);
        assertTrue(deleted > 0);

        CmsEntry entry = contentManager.getEntry(documentId, LOCALE);
        assertNull(entry);
    }
}
