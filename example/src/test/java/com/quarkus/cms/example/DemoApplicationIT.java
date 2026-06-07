package com.quarkus.cms.example;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.builder.ContentTypeBuilder;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

/**
 * Integration tests for the demo application — boots the full Quarkus stack,
 * verifies seed data from {@link DemoDataSeeder}, and exercises a basic
 * create-content-type → add-entry → read-back data flow using injected
 * services rather than REST/GraphQL endpoints.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Demo Application Integration Tests")
class DemoApplicationIT {

    @Inject
    SchemaStorageService schemaStorage;

    @Inject
    ContentManagerService contentManager;

    private static final String LOCALE = "en";
    private static final long USER_ID = 1L;

    // ==================================================================
    // 1. Boot / Health — verify services are available
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("SchemaStorageService and ContentManagerService are injected")
    void servicesAreAvailable() {
        assertNotNull(schemaStorage, "SchemaStorageService should be injected");
        assertNotNull(contentManager, "ContentManagerService should be injected");
    }

    @Test
    @Order(2)
    @DisplayName("DemoDataSeeder has registered all demo content types")
    void demoContentTypesRegistered() {
        List<ContentTypeDefinition> types = schemaStorage.getAllContentTypes();
        assertFalse(types.isEmpty(), "At least one content type should be registered");

        List<String> uids = types.stream()
            .map(ContentTypeDefinition::getUid)
            .toList();

        assertAll("Expected demo content types",
            () -> assertTrue(uids.contains("api::article.article"),
                "Article content type should exist"),
            () -> assertTrue(uids.contains("api::author.author"),
                "Author content type should exist"),
            () -> assertTrue(uids.contains("api::category.category"),
                "Category content type should exist"),
            () -> assertTrue(uids.contains("api::homepage.homepage"),
                "Homepage content type should exist"),
            () -> assertTrue(uids.contains("api::global.global"),
                "Global Settings content type should exist")
        );
    }

    @Test
    @Order(3)
    @DisplayName("Components (SEO, Media) are registered")
    void demoComponentsRegistered() {
        var components = schemaStorage.getAllComponents();
        assertFalse(components.isEmpty(), "At least one component should be registered");

        var uids = components.stream()
            .map(c -> c.getUid())
            .toList();

        assertAll("Expected demo components",
            () -> assertTrue(uids.contains("basic.seo")),
            () -> assertTrue(uids.contains("basic.media"))
        );
    }

    // ==================================================================
    // 2. Seed Data Verification — entries created by DemoDataSeeder
    // ==================================================================

    @Test
    @Order(10)
    @DisplayName("Seed data: 2 authors exist")
    void seededAuthorsExist() {
        List<CmsEntry> authors = contentManager.listAllEntries("api::author.author", LOCALE);
        assertTrue(authors.size() >= 2,
            "Expected at least 2 seeded authors, got " + authors.size());

        // Verify Alice Johnson exists
        boolean aliceFound = authors.stream()
            .anyMatch(a -> "Alice Johnson".equals(a.data.get("name")));
        assertTrue(aliceFound, "Alice Johnson should be among seeded authors");

        // Verify Bob Smith exists
        boolean bobFound = authors.stream()
            .anyMatch(a -> "Bob Smith".equals(a.data.get("name")));
        assertTrue(bobFound, "Bob Smith should be among seeded authors");
    }

    @Test
    @Order(11)
    @DisplayName("Seed data: 3 categories exist")
    void seededCategoriesExist() {
        List<CmsEntry> categories = contentManager.listAllEntries("api::category.category", LOCALE);
        assertTrue(categories.size() >= 3,
            "Expected at least 3 seeded categories, got " + categories.size());

        var names = categories.stream().map(c -> c.data.get("name")).toList();
        assertAll("Expected category names",
            () -> assertTrue(names.contains("Technology")),
            () -> assertTrue(names.contains("Design")),
            () -> assertTrue(names.contains("Science"))
        );
    }

    @Test
    @Order(12)
    @DisplayName("Seed data: 4 articles exist (mix of published + draft)")
    void seededArticlesExist() {
        // Count total articles across all statuses
        List<CmsEntry> allArticles = contentManager.listAllEntries("api::article.article", LOCALE);
        assertTrue(allArticles.size() >= 4,
            "Expected at least 4 seeded articles, got " + allArticles.size());

        // Verify at least one published article exists
        String title1 = "Getting Started with Quarkus Headless CMS";
        boolean hasPublished = allArticles.stream()
            .anyMatch(a -> title1.equals(a.data.get("title")));
        assertTrue(hasPublished,
            "Should have published article: '" + title1 + "'");

        // Verify at least one draft-only article exists
        String draftTitle = "The Future of Quantum Computing in Content Management";
        boolean hasDraft = allArticles.stream()
            .anyMatch(a -> draftTitle.equals(a.data.get("title")));
        assertTrue(hasDraft,
            "Should have draft article: '" + draftTitle + "'");
    }

    @Test
    @Order(13)
    @DisplayName("Seed data: articles have relations (author/categories)")
    void seededArticleRelationsExist() {
        List<CmsEntry> articles = contentManager.listAllEntries("api::article.article", LOCALE);
        assertFalse(articles.isEmpty(), "Articles must exist");

        // Check that at least one article has an author relation
        boolean anyHasAuthor = articles.stream()
            .anyMatch(a -> {
                var rels = contentManager.findRelations(a.documentId, "author");
                return !rels.isEmpty();
            });
        assertTrue(anyHasAuthor, "At least one article should have an author relation");

        // Check that at least one article has a category relation
        boolean anyHasCategory = articles.stream()
            .anyMatch(a -> {
                var rels = contentManager.findRelations(a.documentId, "categories");
                return !rels.isEmpty();
            });
        assertTrue(anyHasCategory, "At least one article should have a category relation");
    }

    @Test
    @Order(14)
    @DisplayName("Seed data: homepage single type exists")
    void seededHomepageExists() {
        CmsEntry homepage = contentManager.getSingleTypeEntry("api::homepage.homepage", LOCALE);
        assertNotNull(homepage, "Homepage entry should exist");
        assertEquals("api::homepage.homepage", homepage.contentType,
            "Entry should be of homepage content type");
        assertNotNull(homepage.data.get("heroTitle"),
            "Homepage should have a heroTitle");
        assertTrue(homepage.data.get("heroTitle").toString().contains("CMS Demo"),
            "Homepage heroTitle should reference CMS demo");
    }

    @Test
    @Order(15)
    @DisplayName("Seed data: global settings single type exists")
    void seededGlobalSettingsExist() {
        CmsEntry settings = contentManager.getSingleTypeEntry("api::global.global", LOCALE);
        assertNotNull(settings, "Global settings entry should exist");
        assertEquals("api::global.global", settings.contentType,
            "Entry should be of global settings content type");
        assertEquals("Quarkus CMS Demo", settings.data.get("siteName"),
            "Site name should be 'Quarkus CMS Demo'");
        assertNotNull(settings.data.get("socialLinks"),
            "Global settings should have social links");
    }

    @Test
    @Order(16)
    @DisplayName("Seed data: article contentType definition has draftAndPublish enabled")
    void articleHasDraftAndPublish() {
        ContentTypeDefinition articleCt = schemaStorage.getContentType("api::article.article");
        assertNotNull(articleCt, "Article content type must exist");
        assertTrue(articleCt.isDraftAndPublish(),
            "Article should have draft/publish enabled");
        assertEquals(ContentTypeKind.COLLECTION_TYPE, articleCt.getKind(),
            "Article should be a collection type");
    }

    // ==================================================================
    // 3. Basic Data Flow — create content type, add entry, read back
    // ==================================================================

    @Test
    @Order(20)
    @Transactional
    @DisplayName("Register new content type, create entry, read it back")
    void createContentTypeAndEntry() {
        String testCtUid = "test::product.product";
        String entryTitle = "Test Product Entry";

        try {
            // --- Register a new content type ---
            ContentTypeDefinition productCt = ContentTypeBuilder
                .create(testCtUid, ContentTypeKind.COLLECTION_TYPE)
                .singularName("product")
                .pluralName("products")
                .displayName("Product")
                .description("Test product content type for integration test")
                .draftAndPublish(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING)
                    .required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("description", FieldType.TEXT).build())
                .addField(FieldDefinition.builder("price", FieldType.FLOAT).build())
                .addField(FieldDefinition.builder("available", FieldType.BOOLEAN)
                    .defaultValue("true").build())
                .build();

            schemaStorage.registerContentType(productCt, "Integration test", "test");

            // Verify content type was registered
            ContentTypeDefinition retrieved = schemaStorage.getContentType(testCtUid);
            assertNotNull(retrieved, "Content type should be retrievable after registration");
            assertEquals("Product", retrieved.getDisplayName());
            assertEquals(4, retrieved.getFields().size());
            assertFalse(retrieved.isDraftAndPublish());
            assertFalse(retrieved.isLocalized());

            // --- Create an entry ---
            Map<String, Object> entryData = Map.of(
                "name", entryTitle,
                "description", "A product created during integration test",
                "price", 29.99,
                "available", true
            );

            CmsEntry created = contentManager.createEntry(
                testCtUid, entryData, LOCALE, USER_ID);

            assertNotNull(created, "Created entry should not be null");
            assertNotNull(created.documentId, "Entry should have a documentId");
            assertEquals(testCtUid, created.contentType, "Entry contentType should match");
            assertEquals(entryTitle, created.data.get("name"), "Entry name should match");
            assertEquals("draft", created.status, "Entry should start as draft");

            // --- Read the entry back ---
            CmsEntry readBack = contentManager.getEntry(created.documentId, LOCALE);
            assertNotNull(readBack, "Should be able to read back the entry");
            assertEquals(created.documentId, readBack.documentId);
            assertEquals(entryTitle, readBack.data.get("name"));
            assertEquals(29.99, ((Number) readBack.data.get("price")).doubleValue(), 0.001);

            // --- Verify entry is listed in all entries ---
            List<CmsEntry> allProducts = contentManager.listAllEntries(testCtUid, LOCALE);
            boolean found = allProducts.stream()
                .anyMatch(e -> e.documentId.equals(created.documentId));
            assertTrue(found, "Created entry should appear in listAllEntries");

            // --- Cleanup: delete the entry ---
            contentManager.deleteDocument(created.documentId);

            // Verify deletion
            CmsEntry afterDelete = contentManager.getEntry(created.documentId, LOCALE);
            assertNull(afterDelete, "Entry should be null after deletion");

        } finally {
            // Always clean up the test content type
            try {
                schemaStorage.deleteContentType(testCtUid);
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
    }

    @Test
    @Order(21)
    @Transactional
    @DisplayName("Create entry in an existing demo content type (Category)")
    void createEntryInExistingContentType() {
        // Create a new category entry
        Map<String, Object> categoryData = Map.of(
            "name", "Integration Test Category",
            "slug", "integration-test-category",
            "description", "Temporary category created during integration test"
        );

        CmsEntry created = contentManager.createEntry(
            "api::category.category", categoryData, LOCALE, USER_ID);

        assertNotNull(created);
        assertNotNull(created.documentId);
        assertEquals("api::category.category", created.contentType);
        assertEquals("Integration Test Category", created.data.get("name"));
        assertEquals("draft", created.status,
            "Category (no draft/publish) should also default to draft status");

        // Read it back
        CmsEntry readBack = contentManager.getEntry(created.documentId, LOCALE);
        assertNotNull(readBack);
        assertEquals(created.documentId, readBack.documentId);
        assertEquals("Integration Test Category", readBack.data.get("name"));

        // Cleanup
        contentManager.deleteDocument(created.documentId);
        assertNull(contentManager.getEntry(created.documentId, LOCALE),
            "Entry should be deleted");
    }

    @Test
    @Order(22)
    @Transactional
    @DisplayName("Single type upsert works (update existing settings)")
    void upsertSingleType() {
        // The global settings already exist from seed data.
        // Update them via upsert.
        Map<String, Object> update = Map.of(
            "siteName", "Updated Integration Test Site"
        );

        CmsEntry updated = contentManager.upsertSingleType(
            "api::global.global", update, LOCALE, USER_ID);

        assertNotNull(updated);
        assertEquals("api::global.global", updated.contentType);
        assertEquals("Updated Integration Test Site", updated.data.get("siteName"));

        // Read back to verify persistence
        CmsEntry readBack = contentManager.getSingleTypeEntry("api::global.global", LOCALE);
        assertNotNull(readBack);
        assertEquals("Updated Integration Test Site", readBack.data.get("siteName"));

        // Restore original value to avoid side effects for other tests
        contentManager.upsertSingleType("api::global.global",
            Map.of("siteName", "Quarkus CMS Demo"), LOCALE, USER_ID);
    }

    @Test
    @Order(30)
    @DisplayName("Content type with draft/publish lifecycle works")
    @Transactional
    void draftPublishLifecycle() {
        // Create a new article
        Map<String, Object> articleData = Map.of(
            "title", "Lifecycle Test Article",
            "slug", "lifecycle-test-article",
            "excerpt", "Testing draft/publish lifecycle",
            "body", "## Lifecycle Test\n\nThis article tests the draft/publish lifecycle.",
            "featured", false
        );

        CmsEntry draft = contentManager.createEntry(
            "api::article.article", articleData, LOCALE, USER_ID);
        assertNotNull(draft);
        assertNotNull(draft.documentId);
        assertEquals("draft", draft.status, "New entry should be a draft");
        assertEquals(0, draft.versionNumber.intValue(),
            "Initial version should be 0");

        // Publish it
        CmsEntry published = contentManager.publishEntry(
            draft.documentId, LOCALE, USER_ID);
        assertNotNull(published);
        assertEquals("published", published.status, "After publish, status should be published");
        assertTrue(published.versionNumber >= 1,
            "Published version should be >= 1");
        assertNotNull(published.publishedAt, "Published date should be set");

        // Unpublish it
        contentManager.unpublishEntry(draft.documentId, LOCALE);

        // After unpublish the published version is removed, but a draft may remain.
        // getEntry returns draft first, then published, then other archived versions.
        CmsEntry afterUnpublish = contentManager.getEntry(draft.documentId, LOCALE);
        if (afterUnpublish != null) {
            // The entry still exists but with status "unpublished"
            assertEquals("unpublished", afterUnpublish.status,
                "After unpublish, entry status should be unpublished");
        }

        // Cleanup
        try {
            contentManager.deleteDocument(draft.documentId);
        } catch (Exception ignored) {
            // Best-effort cleanup (may already be cascaded)
        }
    }
}
