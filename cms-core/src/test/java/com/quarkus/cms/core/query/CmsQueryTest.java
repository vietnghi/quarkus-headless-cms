package com.quarkus.cms.core.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CmsQuery} — focusing on the populate and pagination features.
 */
class CmsQueryTest {

    @Test
    void shouldDefaultToPage1AndPageSize25() {
        CmsQuery query = new CmsQuery("api::article.article");

        assertEquals(1, query.getPage());
        assertEquals(25, query.getPageSize());
        assertEquals(0, query.getOffset());
        assertEquals(25, query.getLimit());
    }

    @Test
    void shouldSupportPaginationOptions() {
        CmsQuery query = new CmsQuery("api::article.article");
        query.setPage(3);
        query.setPageSize(10);

        assertEquals(3, query.getPage());
        assertEquals(10, query.getPageSize());
        assertEquals(20, query.getOffset());
        assertEquals(10, query.getLimit());
    }

    @Test
    void shouldSupportPopulateList() {
        CmsQuery query = new CmsQuery("api::article.article");
        assertNull(query.getPopulate());

        query.addPopulate("author");
        query.addPopulate("category");

        List<PopulateNode> populate = query.getPopulate();
        assertNotNull(populate);
        assertEquals(2, populate.size());
        assertEquals("author", populate.get(0).getFieldName());
        assertEquals("category", populate.get(1).getFieldName());
    }

    @Test
    void shouldSupportPopulateAll() {
        CmsQuery query = new CmsQuery("api::article.article");
        query.setPopulateAll();

        List<PopulateNode> populate = query.getPopulate();
        assertNotNull(populate);
        assertEquals(1, populate.size());
        assertTrue(populate.get(0).isPopulateAll());
    }

    @Test
    void shouldSupportReplacePopulate() {
        CmsQuery query = new CmsQuery("api::article.article");
        query.addPopulate("author");
        query.setPopulate(List.of(new PopulateNode("category")));

        List<PopulateNode> populate = query.getPopulate();
        assertEquals(1, populate.size());
        assertEquals("category", populate.get(0).getFieldName());
    }

    @Test
    void shouldSupportStatusAndLocale() {
        CmsQuery query = new CmsQuery("api::article.article");
        query.setStatus("published");
        query.setLocale("fr");

        assertEquals("published", query.getStatus());
        assertEquals("fr", query.getLocale());
    }

    @Test
    void shouldSupportSortOrders() {
        CmsQuery query = new CmsQuery("api::article.article");
        query.addSort("createdAt", SortOrder.Direction.DESC);
        query.addSort("title", SortOrder.Direction.ASC);

        List<SortOrder> sorts = query.getSort();
        assertNotNull(sorts);
        assertEquals(2, sorts.size());
        assertEquals("createdAt", sorts.get(0).getField());
        assertEquals(SortOrder.Direction.DESC, sorts.get(0).getDirection());
    }
}
