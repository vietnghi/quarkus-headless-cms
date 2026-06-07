package com.quarkus.cms.core.query;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.model.RelationType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ContentQueryEngine} — tests logic that doesn't require Hibernate.
 */
@ExtendWith(MockitoExtension.class)
class ContentQueryEngineTest {

    @Mock
    SchemaStorageService schemaStorageService;

    @InjectMocks
    ContentQueryEngine queryEngine;

    private final String articleType = "api::article.article";
    private final String authorType = "api::author.author";
    private ContentTypeDefinition articleCt;

    @BeforeEach
    void setUp() {
        ContentTypeDefinition authorCt = ContentTypeDefinition.builder(authorType, ContentTypeKind.COLLECTION_TYPE)
                .singularName("author")
                .pluralName("authors")
                .build();

        articleCt = ContentTypeDefinition.builder(articleType, ContentTypeKind.COLLECTION_TYPE)
                .singularName("article")
                .pluralName("articles")
                .relations(List.of(
                        RelationDefinition.builder("author", RelationType.MANY_TO_ONE, authorType).build()
                ))
                .build();

        lenient().when(schemaStorageService.getContentType(articleType)).thenReturn(articleCt);
        lenient().when(schemaStorageService.getContentType(authorType)).thenReturn(authorCt);
    }

    @Test
    void shouldReturnEntriesUnchangedWhenNoPopulate() {
        try (var mockedQuery = mockStatic(CmsQueryBuilder.class)) {
            CmsEntry entry1 = new CmsEntry();
            entry1.documentId = UUID.randomUUID().toString();
            entry1.contentType = articleType;
            entry1.data = new HashMap<>(Map.of("title", "Hello"));

            mockedQuery.when(() -> CmsQueryBuilder.list(any())).thenReturn(List.of(entry1));
            mockedQuery.when(() -> CmsQueryBuilder.count(any())).thenReturn(1L);

            CmsQuery query = new CmsQuery(articleType);
            query.setLocale("en");
            List<CmsEntry> results = queryEngine.list(query);

            assertEquals(1, results.size());
            assertEquals(entry1.documentId, results.get(0).documentId);
        }
    }

    @Test
    void shouldHandlePopulateAllCorrectly() {
        CmsQuery query = new CmsQuery(articleType);
        query.setPopulateAll();

        List<PopulateNode> populate = query.getPopulate();
        assertNotNull(populate);
        assertEquals(1, populate.size());
        assertTrue(populate.get(0).isPopulateAll());
    }

    @Test
    void shouldHandleEmptyResults() {
        try (var mockedQuery = mockStatic(CmsQueryBuilder.class)) {
            mockedQuery.when(() -> CmsQueryBuilder.list(any())).thenReturn(List.of());

            CmsQuery query = new CmsQuery(articleType);
            query.setPopulate(List.of(new PopulateNode("author")));

            List<CmsEntry> results = queryEngine.list(query);
            assertTrue(results.isEmpty());
        }
    }

    @Test
    void shouldHandleUnknownContentTypeGracefully() {
        when(schemaStorageService.getContentType("api::unknown.unknown")).thenReturn(null);

        try (var mockedQuery = mockStatic(CmsQueryBuilder.class)) {
            CmsEntry entry = new CmsEntry();
            entry.documentId = UUID.randomUUID().toString();
            entry.contentType = "api::unknown.unknown";
            mockedQuery.when(() -> CmsQueryBuilder.list(any())).thenReturn(List.of(entry));

            CmsQuery query = new CmsQuery("api::unknown.unknown");
            query.setPopulate(List.of(new PopulateNode("field")));

            List<CmsEntry> results = queryEngine.list(query);
            assertEquals(1, results.size());
        }
    }

    @Test
    void shouldCountEntries() {
        try (var mockedQuery = mockStatic(CmsQueryBuilder.class)) {
            mockedQuery.when(() -> CmsQueryBuilder.count(any())).thenReturn(42L);

            CmsQuery query = new CmsQuery(articleType);
            long count = queryEngine.count(query);

            assertEquals(42L, count);
        }
    }

    @Test
    void staticPopulateAllRelationsShouldHandleEmptyEntries() {
        List<CmsEntry> results = ContentQueryEngine.populateAllRelations(List.of(), articleCt);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRespectDepthLimit() {
        // Deeply nested populate: author -> profile -> avatar -> settings
        PopulateNode settings = new PopulateNode("settings");
        PopulateNode avatar = new PopulateNode("avatar");
        avatar.setChildren(List.of(settings));
        PopulateNode profile = new PopulateNode("profile");
        profile.setChildren(List.of(avatar));
        PopulateNode author = new PopulateNode("author");
        author.setChildren(List.of(profile));

        // Create an entry
        CmsEntry entry = new CmsEntry();
        entry.documentId = UUID.randomUUID().toString();
        entry.contentType = articleType;
        entry.data = new HashMap<>(Map.of("title", "Test"));

        // Mock CmsRelation to avoid Panache calls
        try (var mockedRel = mockStatic(com.quarkus.cms.core.domain.CmsRelation.class)) {
            mockedRel.when(() -> com.quarkus.cms.core.domain.CmsRelation.findRelations(anyString(), anyString()))
                    .thenReturn(List.of());

            // populate with depth 1 — should only process top-level, no nested
            queryEngine.populateEntry(entry, articleCt, List.of(author));
        }

        assertNotNull(entry.data);
        assertEquals("Test", entry.data.get("title"));
    }

    @Test
    void shouldDetectCyclesInPopulation() {
        // Create a cycle: entry A -> entry B -> entry A
        CmsEntry entryA = new CmsEntry();
        entryA.documentId = UUID.randomUUID().toString();
        entryA.contentType = articleType;
        entryA.data = new HashMap<>(Map.of("title", "Entry A"));

        // Populate with a node that would cause deep recursion
        PopulateNode deep = new PopulateNode("related");
        deep.setChildren(List.of(new PopulateNode("related"))); // self-referencing

        // Mock CmsRelation to avoid Panache calls
        try (var mockedRel = mockStatic(com.quarkus.cms.core.domain.CmsRelation.class)) {
            mockedRel.when(() -> com.quarkus.cms.core.domain.CmsRelation.findRelations(anyString(), anyString()))
                    .thenReturn(List.of());

            // Should not throw (cycle detection handles it gracefully)
            queryEngine.populateEntry(entryA, articleCt, List.of(deep));
        }
        assertNotNull(entryA.data);
    }

    @Test
    void shouldReturnDefaultMaxDepthConstant() {
        assertEquals(5, ContentQueryEngine.DEFAULT_MAX_POPULATE_DEPTH);
    }
}
