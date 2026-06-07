package com.quarkus.cms.core.repository;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.CmsQueryBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CmsEntryRepository} — focuses on delegation and creation logic.
 * Full CRUD lifecycle tests with JPA require {@code @QuarkusTest} integration tests.
 */
@ExtendWith(MockitoExtension.class)
class CmsEntryRepositoryTest {

    private final CmsEntryRepository repository = new CmsEntryRepository();

    // ---- Create (construction-mocked CmsEntry avoids JPA persist failure) ----

    @Test
    void shouldCreateEntryWithGeneratedDocumentId() {
        try (var mocked = mockConstruction(CmsEntry.class)) {
            Map<String, Object> data = Map.of("title", "Test");
            CmsEntry result = repository.create("api::article.article", data, "en");

            assertNotNull(result);
            assertNotNull(result.documentId);
            assertEquals(36, result.documentId.length());
            assertTrue(result.documentId.contains("-"));
            assertEquals("api::article.article", result.contentType);
            assertEquals("en", result.locale);
            assertEquals("draft", result.status);
            assertEquals(data, result.data);
        }
    }

    @Test
    void shouldCreateEntryWithDefaultLocale() {
        try (var mocked = mockConstruction(CmsEntry.class)) {
            CmsEntry result = repository.create("api::article.article", Map.of(), null);
            assertNotNull(result);
            assertEquals("en", result.locale);
        }
    }

    @Test
    void shouldCreateEntryWithCreator() {
        try (var mocked = mockConstruction(CmsEntry.class,
                (mock, ctx) -> {
                    // The repository sets createdById after construction
                    // We need to make it mutable
                })) {
            CmsEntry result = repository.createWithCreator("api::article.article", Map.of(), "en", 42L);
            assertNotNull(result);
            assertEquals("api::article.article", result.contentType);
        }
    }

    // ---- List / Count (delegation to CmsQueryBuilder) ----

    @Test
    void shouldListWithQuery() {
        try (var mocked = mockStatic(CmsQueryBuilder.class)) {
            mocked.when(() -> CmsQueryBuilder.list(any()))
                    .thenReturn(List.of(new CmsEntry()));

            CmsQuery query = new CmsQuery("api::article.article");
            List<CmsEntry> results = repository.list(query);
            assertEquals(1, results.size());
        }
    }

    @Test
    void shouldCountWithQuery() {
        try (var mocked = mockStatic(CmsQueryBuilder.class)) {
            mocked.when(() -> CmsQueryBuilder.count(any())).thenReturn(7L);

            CmsQuery query = new CmsQuery("api::article.article");
            assertEquals(7L, repository.count(query));
        }
    }

    // ---- Update error path ----

    @Test
    void shouldThrowOnUpdateNonExistentDraft() {
        try (var mocked = mockStatic(CmsEntry.class)) {
            mocked.when(() -> CmsEntry.findByDocumentId(anyString(), anyString(), anyString()))
                    .thenReturn(null);

            assertThrows(IllegalArgumentException.class,
                    () -> repository.update("doc-1", Map.of(), 1L, "en"));
        }
    }

    // ---- Publish error path ----

    @Test
    void shouldThrowOnPublishWithoutDraft() {
        try (var mocked = mockStatic(CmsEntry.class)) {
            mocked.when(() -> CmsEntry.findByDocumentId(anyString(), anyString(), anyString()))
                    .thenReturn(null);

            assertThrows(IllegalArgumentException.class,
                    () -> repository.publish("doc-1", 1L, "en"));
        }
    }

    // ---- Find versions (delegation) ----

    @Test
    void shouldFindDocumentVersions() {
        try (var mocked = mockStatic(CmsEntry.class)) {
            mocked.when(() -> CmsEntry.findDocumentVersions(anyString()))
                    .thenReturn(List.of());

            List<CmsEntry> versions = repository.findDocumentVersions("doc-1");
            assertNotNull(versions);
            assertTrue(versions.isEmpty());
        }
    }

    // ---- Unpublish (no-op when no published entry) ----

    @Test
    void shouldHandleUnpublishWhenNoPublishedEntry() {
        try (var mocked = mockStatic(CmsEntry.class)) {
            mocked.when(() -> CmsEntry.findByDocumentId(anyString(), anyString(), anyString()))
                    .thenReturn(null);

            // Should not throw even with no published entry
            repository.unpublish("doc-1", "en");
        }
    }

    // ---- Create with locale ----

    @Test
    void shouldCreateWithSpecifiedLocale() {
        try (var mocked = mockConstruction(CmsEntry.class)) {
            CmsEntry result = repository.create("api::article.article", Map.of(), "fr");
            assertNotNull(result);
            assertEquals("fr", result.locale);
        }
    }
}
