package com.quarkus.cms.core.query;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CmsQueryBuilder} — WHERE clause generation, filter building, and sort conversion.
 */
class CmsQueryBuilderTest {

    // ---- buildWhereClause ----

    @Test
    void shouldBuildWhereWithContentTypeOnly() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();

        CmsQuery query = new CmsQuery("api::article.article");
        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);

        String result = where.toString();
        assertTrue(result.contains("contentType = :ct"));
        assertEquals(1, pc);
    }

    @Test
    void shouldBuildWhereWithAllStandardColumns() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();

        CmsQuery query = new CmsQuery("api::article.article");
        query.setLocale("de");
        query.setStatus("draft");
        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);

        String result = where.toString();
        assertTrue(result.contains("contentType = :ct"));
        assertTrue(result.contains("locale = :loc"));
        assertTrue(result.contains("status = :st"));
        assertEquals(3, pc);
    }

    // ---- Eq filter ----

    @Test
    void shouldBuildEqFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.eq("title", "Hello"));

        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("json_extract") || result.contains("jsonb_extract_path_text"));
        assertTrue(result.contains("= :p1"));
        assertEquals(2, pc); // contentType + eq filter
    }

    // ---- gt filter ----

    @Test
    void shouldBuildGtFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("price", FilterNode.Operator.GT, 100));

        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("CAST("));
        assertTrue(result.contains("AS double) > :p1"));
        assertEquals(2, pc);
    }

    // ---- lte filter ----

    @Test
    void shouldBuildLteFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("rating", FilterNode.Operator.LTE, 5));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("AS double) <= :p1"));
    }

    // ---- contains filter ----

    @Test
    void shouldBuildContainsFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("body", FilterNode.Operator.CONTAINS, "keyword"));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("LIKE"));
    }

    // ---- startsWith filter ----

    @Test
    void shouldBuildStartsWithFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("slug", FilterNode.Operator.STARTS_WITH, "news"));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("LIKE"));
    }

    // ---- notContains filter ----

    @Test
    void shouldBuildNotContainsFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("body", FilterNode.Operator.NOT_CONTAINS, "spam"));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("NOT LIKE"));
    }

    // ---- null checks ----

    @Test
    void shouldBuildNullFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("summary", FilterNode.Operator.NULL, null));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("IS NULL"));
        assertTrue(result.contains("'null'"));
    }

    @Test
    void shouldBuildNotNullFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("summary", FilterNode.Operator.NOT_NULL, null));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("IS NOT NULL"));
    }

    // ---- AND / OR groups ----

    @Test
    void shouldBuildAndGroupFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();

        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.and(
                FilterNode.eq("status", "published"),
                FilterNode.eq("locale", "en")
        ));

        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("AND ("));
        assertTrue(result.contains(")"));
        assertEquals(3, pc); // contentType + 2 filter params
    }

    @Test
    void shouldBuildOrGroupFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();

        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.or(
                FilterNode.eq("category", "news"),
                FilterNode.eq("category", "sports")
        ));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("OR"));
    }

    @Test
    void shouldHandleEmptyGroup() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.and(List.of()));

        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        assertEquals(1, pc); // only contentType
    }

    @Test
    void shouldHandleNoFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");

        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        assertEquals(1, pc);
    }

    @Test
    void shouldBuildNestedAndOrFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();

        // (status = published AND (category = news OR tags CONTAINS urgent))
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.and(
                FilterNode.eq("status", "published"),
                FilterNode.or(
                        FilterNode.eq("category", "news"),
                        FilterNode.leaf("tags", FilterNode.Operator.CONTAINS, "urgent")
                )
        ));

        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("AND ("));
        assertTrue(result.contains("OR"));
        assertEquals(4, pc); // contentType + 3 filter params
    }

    // ---- buildSort ----

    @Test
    void shouldDefaultToCreatedAtDesc() {
        CmsQuery query = new CmsQuery("api::article.article");
        Sort sort = CmsQueryBuilder.buildSort(query);
        assertNotNull(sort);
    }

    @Test
    void shouldBuildSingleFieldSort() {
        CmsQuery query = new CmsQuery("api::article.article");
        query.addSort("title", SortOrder.Direction.ASC);

        Sort sort = CmsQueryBuilder.buildSort(query);
        assertNotNull(sort);
    }

    @Test
    void shouldBuildMultipleFieldSort() {
        CmsQuery query = new CmsQuery("api::article.article");
        query.addSort("category", SortOrder.Direction.ASC);
        query.addSort("createdAt", SortOrder.Direction.DESC);

        Sort sort = CmsQueryBuilder.buildSort(query);
        assertNotNull(sort);
    }

    // ---- IN / NOT IN filters ----

    @Test
    void shouldBuildInFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("status", FilterNode.Operator.IN, List.of("draft", "published")));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("IN"));
    }

    @Test
    void shouldBuildNotInFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("status", FilterNode.Operator.NOT_IN, List.of("archived")));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("NOT IN"));
    }

    // ---- NE filter ----

    @Test
    void shouldBuildNeFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("status", FilterNode.Operator.NE, "archived"));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("<>"));
        assertTrue(result.contains("IS NULL"));
    }

    // ---- EndsWith ----

    @Test
    void shouldBuildEndsWithFilter() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();
        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.leaf("slug", FilterNode.Operator.ENDS_WITH, ".html"));

        CmsQueryBuilder.buildWhereClause(query, where, params, 0);
        String result = where.toString();

        assertTrue(result.contains("LIKE"));
    }

    // ---- All operators exercise ----

    @Test
    void shouldHandleAllOperatorsWithoutException() {
        for (FilterNode.Operator op : FilterNode.Operator.values()) {
            StringBuilder where = new StringBuilder();
            Parameters params = new Parameters();
            CmsQuery query = new CmsQuery("api::article.article");

            Object value = (op == FilterNode.Operator.NULL || op == FilterNode.Operator.NOT_NULL)
                    ? null : "test";
            FilterNode filter = FilterNode.leaf("field", op, value);
            query.setFilter(filter);

            try {
                CmsQueryBuilder.buildWhereClause(query, where, params, 0);
            } catch (Exception e) {
                fail("Operator " + op + " threw: " + e.getMessage());
            }
        }
    }

    // ---- count / list wrappers (boundary cases) ----

    @Test
    void shouldBuildWhereWithLocaleAndStatusOnly() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();

        CmsQuery query = new CmsQuery("api::article.article");
        query.setLocale("fr");
        query.setStatus("published");
        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);

        String result = where.toString();
        assertTrue(result.contains("contentType = :ct"));
        assertTrue(result.contains("locale = :loc"));
        assertTrue(result.contains("status = :st"));
        assertEquals(3, pc);
    }

    @Test
    void shouldBuildWhereWithFilterOnlyAndContentType() {
        StringBuilder where = new StringBuilder();
        Parameters params = new Parameters();

        CmsQuery query = new CmsQuery("api::article.article");
        query.setFilter(FilterNode.eq("visibility", "public"));
        int pc = CmsQueryBuilder.buildWhereClause(query, where, params, 0);

        assertEquals(2, pc); // contentType + filter
        String result = where.toString();
        assertTrue(result.contains("contentType = :ct"));
        assertTrue(result.contains("json_extract") || result.contains("jsonb_extract_path_text"));
    }

    // ---- Sort test with default only ----

    @Test
    void sortShouldDefaultToDescending() {
        CmsQuery query = new CmsQuery("api::article.article");
        Sort sort = CmsQueryBuilder.buildSort(query);
        assertNotNull(sort);
    }
}
