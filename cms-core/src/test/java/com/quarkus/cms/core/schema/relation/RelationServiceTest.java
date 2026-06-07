package com.quarkus.cms.core.schema.relation;

import com.quarkus.cms.core.domain.CmsRelation;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RelationService} — validation, delegation, and error paths.
 * Full lifecycle tests (persisting CmsRelation) require {@code @QuarkusTest} integration tests.
 */
@ExtendWith(MockitoExtension.class)
class RelationServiceTest {

    @Mock
    SchemaStorageService schemaService;

    @InjectMocks
    RelationService relationService;

    private final String sourceType = "api::article.article";
    private final String targetType = "api::author.author";
    private ContentTypeDefinition sourceCt;

    @BeforeEach
    void setUp() {
        sourceCt = ContentTypeDefinition.builder(sourceType, ContentTypeKind.COLLECTION_TYPE)
                .singularName("article")
                .pluralName("articles")
                .relations(List.of(
                        RelationDefinition.builder("author", RelationType.MANY_TO_ONE, targetType)
                                .build(),
                        RelationDefinition.builder("tags", RelationType.MANY_TO_MANY, "api::tag.tag")
                                .build()
                ))
                .build();

        lenient().when(schemaService.getContentType(sourceType)).thenReturn(sourceCt);
    }

    // ---- Attach -----

    @Test
    void shouldAttachRelation() {
        try (var mockedRel = mockConstruction(CmsRelation.class)) {
            CmsRelation result = relationService.attach(
                    "src-1", sourceType, "tgt-1", targetType, "author", 0);

            assertNotNull(result);
            assertEquals("src-1", result.sourceDocumentId);
            assertEquals("tgt-1", result.targetDocumentId);
            assertEquals("author", result.fieldName);
            verify(schemaService, atLeast(1)).getContentType(sourceType);
        }
    }

    @Test
    void shouldThrowOnUnknownSourceType() {
        when(schemaService.getContentType(anyString())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> relationService.attach("src-1", "unknown", "tgt-1", targetType, "author", 0));
    }

    @Test
    void shouldThrowOnUnknownFieldName() {
        assertThrows(IllegalArgumentException.class,
                () -> relationService.attach("src-1", sourceType, "tgt-1", targetType, "nonexistent", 0));
    }

    @Test
    void shouldThrowOnWrongTargetType() {
        assertThrows(IllegalArgumentException.class,
                () -> relationService.attach("src-1", sourceType, "tgt-1", "api::wrong.wrong", "author", 0));
    }

    // ---- Detach (wrapped in mockStatic to avoid JPA) ----

    @Test
    void shouldDetachRelationWithoutError() {
        try (var mockedRel = mockStatic(io.quarkus.hibernate.orm.panache.PanacheEntityBase.class)) {
            assertDoesNotThrow(() -> relationService.detach("src-1", "author", "tgt-1"));
        }
    }

    @Test
    void shouldDetachAllWithoutError() {
        try (var mockedRel = mockStatic(io.quarkus.hibernate.orm.panache.PanacheEntityBase.class)) {
            assertDoesNotThrow(() -> relationService.detachAll("src-1", "author"));
        }
    }

    // ---- Find targets (custom static methods, easy to mock) ----

    @Test
    void shouldFindTargetIds() {
        try (var mockedRel = mockStatic(CmsRelation.class)) {
            CmsRelation rel1 = new CmsRelation();
            rel1.targetDocumentId = "tgt-1";
            CmsRelation rel2 = new CmsRelation();
            rel2.targetDocumentId = "tgt-2";

            mockedRel.when(() -> CmsRelation.findRelations(anyString(), anyString()))
                    .thenReturn(List.of(rel1, rel2));

            List<String> ids = relationService.findTargetIds("src-1", "author");
            assertEquals(2, ids.size());
            assertTrue(ids.contains("tgt-1"));
            assertTrue(ids.contains("tgt-2"));
        }
    }

    @Test
    void shouldFindRelationsWithFullMetadata() {
        try (var mockedRel = mockStatic(CmsRelation.class)) {
            CmsRelation rel = new CmsRelation();
            rel.targetDocumentId = "tgt-1";
            mockedRel.when(() -> CmsRelation.findRelations(anyString(), anyString()))
                    .thenReturn(List.of(rel));

            List<CmsRelation> results = relationService.findRelations("src-1", "author");
            assertEquals(1, results.size());
            assertEquals("tgt-1", results.get(0).targetDocumentId);
        }
    }

    @Test
    void shouldFindTargeting() {
        try (var mockedRel = mockStatic(CmsRelation.class)) {
            CmsRelation rel = new CmsRelation();
            rel.sourceDocumentId = "src-1";
            mockedRel.when(() -> CmsRelation.findTargeting(anyString()))
                    .thenReturn(List.of(rel));

            List<CmsRelation> results = relationService.findTargeting("tgt-1");
            assertEquals(1, results.size());
            assertEquals("src-1", results.get(0).sourceDocumentId);
        }
    }

    // ---- Remove all for document ----

    @Test
    void shouldRemoveAllForDocument() {
        try (var mockedRel = mockStatic(io.quarkus.hibernate.orm.panache.PanacheEntityBase.class)) {
            assertDoesNotThrow(() -> relationService.removeAllForDocument("doc-1"));
        }
    }

    // ---- Validation ----

    @Test
    void shouldValidateRelationExists() {
        assertTrue(relationService.isValidRelation(sourceType, "author"));
        assertFalse(relationService.isValidRelation(sourceType, "nonexistent"));
        assertFalse(relationService.isValidRelation("unknown", "author"));
    }

    // ---- Reorder ----

    @Test
    void shouldReorderRelations() {
        try (var mockedRel = mockStatic(CmsRelation.class)) {
            CmsRelation rel1 = new CmsRelation();
            rel1.targetDocumentId = "tgt-a";
            CmsRelation rel2 = new CmsRelation();
            rel2.targetDocumentId = "tgt-b";

            mockedRel.when(() -> CmsRelation.findRelations(anyString(), anyString()))
                    .thenReturn(List.of(rel1, rel2));

            relationService.reorder("src-1", "author", List.of("tgt-b", "tgt-a"));
            assertEquals(1, rel1.orderIndex.intValue());
            assertEquals(0, rel2.orderIndex.intValue());
        }
    }
}
