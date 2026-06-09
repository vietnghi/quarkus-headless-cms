package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.admin.api.service.SearchService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.draft.DraftPublishService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Global Search service layer.
 *
 * Creates test content types with entries and verifies that the search
 * endpoint finds the correct results, applies filters, and respects
 * pagination limits. Each DB-dependent test is self-contained (creates
 * its own data within the same transaction) to avoid test environment
 * transaction isolation issues.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchServiceTest {

    @Inject
    SearchService searchService;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    DraftPublishService draftPublishService;

    @Inject
    EntityManager entityManager;

    private static final String ARTICLE_CT = "api::searchtest.article";
    private static final String PRODUCT_CT = "api::searchtest.product";
    private static final String LOCALE = "en";

    @BeforeAll
    @Transactional
    void setupContentTypes() {
        // Register an "Article" content type with title, body, and summary fields
        ContentTypeDefinition articleCt = ContentTypeDefinition.builder(ARTICLE_CT, ContentTypeKind.COLLECTION_TYPE)
            .singularName("article")
            .pluralName("articles")
            .displayName("Article")
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).required(true).build(),
                FieldDefinition.builder("summary", FieldType.TEXT).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()
            ))
            .build();
        schemaStorageService.registerContentType(articleCt, "Test setup", "test-user");

        // Register a "Product" content type with name and description
        ContentTypeDefinition productCt = ContentTypeDefinition.builder(PRODUCT_CT, ContentTypeKind.COLLECTION_TYPE)
            .singularName("product")
            .pluralName("products")
            .displayName("Product")
            .fields(List.of(
                FieldDefinition.builder("name", FieldType.STRING).required(true).build(),
                FieldDefinition.builder("description", FieldType.TEXT).build()
            ))
            .build();
        schemaStorageService.registerContentType(productCt, "Test setup", "test-user");
    }

    // ---- Self-contained DB tests: each creates data and searches in one method ---- //

    @Test
    @Transactional
    void testGlobalSearch() {
        // Create test data within the same transaction
        CmsEntry article = draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Hello World", "summary", "A quick summary", "body", "Body content"),
            LOCALE, 1L);
        CmsEntry product = draftPublishService.createDraft(PRODUCT_CT,
            Map.of("name", "Super Gadget", "description", "The best gadget in the world"),
            LOCALE, 1L);

        // Flush to ensure data is visible to subsequent queries within this transaction
        entityManager.flush();

        // Search across all types
        SearchResponse response = searchService.search("world", null, null, 0, 20);
        assertNotNull(response);
        assertTrue(response.total >= 1, "Should find at least 1 result for 'world'");
        if (!response.results.isEmpty()) {
            SearchResultItem first = response.results.get(0);
            assertNotNull(first.contentType);
            assertNotNull(first.entryId);
            assertNotNull(first.url);
        }
    }

    @Test
    @Transactional
    void testSearchFilterByContentType() {
        draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Hello World", "body", "World news article"),
            LOCALE, 1L);
        draftPublishService.createDraft(PRODUCT_CT,
            Map.of("name", "World Beater", "description", "Best product"),
            LOCALE, 1L);
        entityManager.flush();

        // Filter by article content type
        SearchResponse response = searchService.search("world", ARTICLE_CT, null, 0, 20);
        assertNotNull(response);
        assertTrue(response.total >= 1, "Should find at least 1 article with 'world'");
        for (SearchResultItem item : response.results) {
            assertEquals(ARTICLE_CT, item.contentType,
                "All results should be of the filtered content type");
        }
    }

    @Test
    @Transactional
    void testSearchFilterByLocale() {
        draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Hello World"),
            LOCALE, 1L);
        entityManager.flush();

        SearchResponse response = searchService.search("world", null, LOCALE, 0, 20);
        assertNotNull(response);
        assertTrue(response.total >= 1, "Should find results in English locale");
    }

    @Test
    @Transactional
    void testNoResultsForUnmatchedQuery() {
        draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Hello World"),
            LOCALE, 1L);
        entityManager.flush();

        SearchResponse response = searchService.search("xyznonexistent", null, null, 0, 20);
        assertNotNull(response);
        assertEquals(0, response.total);
        assertTrue(response.results.isEmpty());
    }

    @Test
    void testEmptyQueryReturnsNoResults() {
        SearchResponse response = searchService.search("", null, null, 0, 10);
        assertNotNull(response);
        assertEquals(0, response.total);
        assertTrue(response.results.isEmpty());
    }

    @Test
    void testBlankQueryReturnsNoResults() {
        SearchResponse response = searchService.search("   ", null, null, 0, 10);
        assertNotNull(response);
        assertEquals(0, response.total);
    }

    @Test
    @Transactional
    void testPaginatedSearch() {
        draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Hello World"),
            LOCALE, 1L);
        draftPublishService.createDraft(PRODUCT_CT,
            Map.of("name", "World Beater", "description", "Best in world"),
            LOCALE, 1L);
        entityManager.flush();

        // First page
        SearchResponse page1 = searchService.search("world", null, null, 0, 1);
        assertNotNull(page1);
        assertTrue(page1.total >= 2, "Total should reflect all matching results");
        assertEquals(1, page1.results.size(), "Page 1 should have 1 result");

        // Second page
        SearchResponse page2 = searchService.search("world", null, null, 1, 1);
        assertNotNull(page2);
        assertEquals(1, page2.results.size(), "Page 2 should have 1 result");

        // Results should be different between pages
        if (!page1.results.isEmpty() && !page2.results.isEmpty()) {
            assertNotEquals(page1.results.get(0).documentId, page2.results.get(0).documentId,
                "Pages should return different results");
        }
    }

    @Test
    @Transactional
    void testMaxPageSizeIsEnforced() {
        draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Hello World"),
            LOCALE, 1L);
        draftPublishService.createDraft(PRODUCT_CT,
            Map.of("name", "World Beater"),
            LOCALE, 1L);
        entityManager.flush();

        SearchResponse response = searchService.search("world", null, null, 0, 100);
        assertNotNull(response);
        assertTrue(response.results.size() <= SearchService.MAX_PAGE_SIZE,
            "Results should be capped at " + SearchService.MAX_PAGE_SIZE);
    }

    @Test
    @Transactional
    void testSearchByBodyContent() {
        draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Test Article", "body", "This is the rich text body content"),
            LOCALE, 1L);
        entityManager.flush();

        SearchResponse response = searchService.search("rich text", null, null, 0, 20);
        assertNotNull(response);
        assertTrue(response.total >= 1, "Should find article with 'rich text' in body");
    }

    @Test
    @Transactional
    void testSearchResultContainsExcerpt() {
        draftPublishService.createDraft(PRODUCT_CT,
            Map.of("name", "Super Gadget", "description", "The best gadget in the world"),
            LOCALE, 1L);
        entityManager.flush();

        SearchResponse response = searchService.search("gadget", null, null, 0, 20);
        assertNotNull(response);
        assertTrue(response.total >= 1, "Should find product with 'gadget'");
        SearchResultItem item = response.results.get(0);
        assertNotNull(item.excerpt);
        assertFalse(item.excerpt.isBlank(), "Excerpt should not be blank");
    }

    // ---- Pure unit tests (no DB needed) ---- //

    @Test
    void testForEntryFactoryMethod() {
        SearchResultItem item = SearchResultItem.forEntry("api::test.article", 42L,
            "doc-123", "Test Title", "Test excerpt");
        assertEquals("api::test.article", item.contentType);
        assertEquals(42L, item.entryId);
        assertEquals("doc-123", item.documentId);
        assertEquals("Test Title", item.title);
        assertEquals("Test excerpt", item.excerpt);
        assertEquals("/admin/content-manager/collection-types/api::test.article/doc-123", item.url);
    }

    @Test
    void testForMediaFactoryMethod() {
        SearchResultItem item = SearchResultItem.forMedia(99L, "photo.jpg", "A nice photo");
        assertEquals("media", item.contentType);
        assertEquals(99L, item.entryId);
        assertNull(item.documentId);
        assertEquals("photo.jpg", item.title);
        assertEquals("A nice photo", item.excerpt);
        assertEquals("/admin/media/files/99", item.url);
    }

    // ---- extractTitle / extractExcerpt unit tests ---- //

    @Test
    void testExtractTitlePrefersKnownField() {
        Map<String, Object> data = Map.of(
            "title", "Main Title",
            "name", "Secondary Name",
            "body", "Body content"
        );
        String title = SearchService.extractTitle(data, null);
        assertEquals("Main Title", title);
    }

    @Test
    void testExtractTitleUsesNameFallback() {
        Map<String, Object> data = Map.of(
            "name", "Product Name",
            "description", "Description"
        );
        String title = SearchService.extractTitle(data, null);
        assertEquals("Product Name", title);
    }

    @Test
    void testExtractTitleUsesFirstStringField() {
        Map<String, Object> data = Map.of(
            "slug", "hello-world",
            "body", "Some content"
        );
        ContentTypeDefinition ct = ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(List.of(
                FieldDefinition.builder("slug", FieldType.UID).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()
            ))
            .build();

        String title = SearchService.extractTitle(data, ct);
        assertEquals("hello-world", title);
    }

    @Test
    void testExtractTitleReturnsUntitledForEmptyData() {
        assertEquals("(untitled)", SearchService.extractTitle(Map.of(), null));
        assertEquals("(untitled)", SearchService.extractTitle(null, null));
    }

    @Test
    void testExtractExcerptMatchesSearchTerm() {
        ContentTypeDefinition ct = ContentTypeDefinition.builder("api::test.test", ContentTypeKind.COLLECTION_TYPE)
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()
            ))
            .build();

        Map<String, Object> data = Map.of(
            "title", "My Article",
            "body", "This is a long body that contains the search term and more content after it"
        );

        String excerpt = SearchService.extractExcerpt(data, ct, "search");
        assertNotNull(excerpt);
        assertTrue(excerpt.toLowerCase().contains("search"), "Excerpt should contain the search term");
    }

    @Test
    void testExtractExcerptReturnsEmptyForNullData() {
        assertEquals("", SearchService.extractExcerpt(null, null, "test"));
    }
}
