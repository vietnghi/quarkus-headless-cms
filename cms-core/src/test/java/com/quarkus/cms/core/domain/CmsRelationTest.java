package com.quarkus.cms.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CmsRelation} — POJO field behavior.
 */
class CmsRelationTest {

    @Test
    void shouldHaveDefaultOrderIndexZero() {
        CmsRelation rel = new CmsRelation();
        assertEquals(0, rel.orderIndex.intValue());
    }

    @Test
    void shouldHaveNullId() {
        CmsRelation rel = new CmsRelation();
        assertNull(rel.id);
    }

    @Test
    void shouldPopulateAllFields() {
        CmsRelation rel = new CmsRelation();
        rel.fieldName = "author";
        rel.sourceDocumentId = "src-doc-1";
        rel.sourceType = "api::article.article";
        rel.targetDocumentId = "tgt-doc-1";
        rel.targetType = "api::author.author";
        rel.orderIndex = 1;

        assertEquals("author", rel.fieldName);
        assertEquals("src-doc-1", rel.sourceDocumentId);
        assertEquals("api::article.article", rel.sourceType);
        assertEquals("tgt-doc-1", rel.targetDocumentId);
        assertEquals("api::author.author", rel.targetType);
        assertEquals(1, rel.orderIndex.intValue());
    }

    @Test
    void shouldAcceptNullOrderIndex() {
        CmsRelation rel = new CmsRelation();
        rel.orderIndex = null;
        assertNull(rel.orderIndex);
    }

    @Test
    void shouldSupportFieldNameChanges() {
        CmsRelation rel = new CmsRelation();
        rel.fieldName = "category";
        assertEquals("category", rel.fieldName);

        rel.fieldName = "tags";
        assertEquals("tags", rel.fieldName);
    }

    @Test
    void shouldSupportDocumentIds() {
        CmsRelation rel = new CmsRelation();
        rel.sourceDocumentId = "source-1";
        rel.targetDocumentId = "target-1";

        assertEquals("source-1", rel.sourceDocumentId);
        assertEquals("target-1", rel.targetDocumentId);
    }

    @Test
    void shouldSupportTypeInfo() {
        CmsRelation rel = new CmsRelation();
        rel.sourceType = "api::article.article";
        rel.targetType = "api::tag.tag";

        assertEquals("api::article.article", rel.sourceType);
        assertEquals("api::tag.tag", rel.targetType);
    }

    @Test
    void shouldSupportOrderIndexChanges() {
        CmsRelation rel = new CmsRelation();
        rel.orderIndex = 5;
        assertEquals(5, rel.orderIndex.intValue());

        rel.orderIndex = 10;
        assertEquals(10, rel.orderIndex.intValue());
    }
}
