package com.quarkus.cms.rest.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.FilterNode;
import com.quarkus.cms.core.query.PopulateNode;
import com.quarkus.cms.core.query.SortOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link QueryParamParser}. */
@DisplayName("QueryParamParser")
class QueryParamParserTest {

  @Nested
  @DisplayName("Simple parameters")
  class SimpleParams {

    @Test
    @DisplayName("should parse locale")
    void parseLocale() {
      CmsQuery query = parse("article", Map.of("locale", "fr"));
      assertEquals("article", query.getContentType());
      assertEquals("fr", query.getLocale());
    }

    @Test
    @DisplayName("should parse pagination")
    void parsePagination() {
      var params = new LinkedHashMap<String, String>();
      params.put("pagination[page]", "2");
      params.put("pagination[pageSize]", "10");

      CmsQuery query = parse("article", params);
      assertEquals(2, query.getPage());
      assertEquals(10, query.getPageSize());
    }

    @Test
    @DisplayName("should parse publicationState=live")
    void parsePublicationStateLive() {
      CmsQuery query = parse("article", Map.of("publicationState", "live"));
      assertEquals("published", query.getStatus());
    }

    @Test
    @DisplayName("should parse publicationState=preview (no status filter)")
    void parsePublicationStatePreview() {
      CmsQuery query = parse("article", Map.of("publicationState", "preview"));
      assertNull(query.getStatus());
    }
  }

  @Nested
  @DisplayName("Sorting")
  class Sorting {

    @Test
    @DisplayName("should parse single sort ascending")
    void parseSingleSortAsc() {
      CmsQuery query = parse("article", Map.of("sort", "title:asc"));
      List<SortOrder> sort = query.getSort();
      assertEquals(1, sort.size());
      assertEquals("title", sort.get(0).getField());
      assertEquals(SortOrder.Direction.ASC, sort.get(0).getDirection());
    }

    @Test
    @DisplayName("should parse single sort descending")
    void parseSingleSortDesc() {
      CmsQuery query = parse("article", Map.of("sort", "createdAt:desc"));
      List<SortOrder> sort = query.getSort();
      assertEquals(1, sort.size());
      assertEquals("createdAt", sort.get(0).getField());
      assertEquals(SortOrder.Direction.DESC, sort.get(0).getDirection());
    }

    @Test
    @DisplayName("should parse sort without direction (defaults to ASC)")
    void parseSortDefaultAsc() {
      CmsQuery query = parse("article", Map.of("sort", "title"));
      assertEquals(1, query.getSort().size());
      assertEquals(SortOrder.Direction.ASC, query.getSort().get(0).getDirection());
    }

    @Test
    @DisplayName("should parse multiple sort fields (bracket notation)")
    void parseMultipleSortBracket() {
      var params = new LinkedHashMap<String, String>();
      params.put("sort[0]", "title:asc");
      params.put("sort[1]", "createdAt:desc");

      CmsQuery query = parse("article", params);
      assertEquals(2, query.getSort().size());
      assertEquals("title", query.getSort().get(0).getField());
      assertEquals("createdAt", query.getSort().get(1).getField());
    }
  }

  @Nested
  @DisplayName("Filters")
  class Filters {

    @Test
    @DisplayName("should parse simple equality filter")
    void parseSimpleEqFilter() {
      var params = new LinkedHashMap<String, String>();
      params.put("filters[title][$eq]", "Hello");

      CmsQuery query = parse("article", params);
      assertNotNull(query.getFilter());
      assertTrue(query.getFilter() instanceof FilterNode.Leaf);
      FilterNode.Leaf leaf = (FilterNode.Leaf) query.getFilter();
      assertEquals("title", leaf.getField());
      assertEquals(FilterNode.Operator.EQ, leaf.getOperator());
      assertEquals("Hello", leaf.getValue());
    }

    @Test
    @DisplayName("should parse contains filter")
    void parseContainsFilter() {
      var params = new LinkedHashMap<String, String>();
      params.put("filters[title][$contains]", "world");

      CmsQuery query = parse("article", params);
      FilterNode.Leaf leaf = (FilterNode.Leaf) query.getFilter();
      assertEquals(FilterNode.Operator.CONTAINS, leaf.getOperator());
      assertEquals("world", leaf.getValue());
    }

    @Test
    @DisplayName("should parse multiple operators on same field (last wins)")
    void parseMultipleOperators() {
      var params = new LinkedHashMap<String, String>();
      params.put("filters[title][$eq]", "Hello");
      params.put("filters[title][$contains]", "lo");

      CmsQuery query = parse("article", params);
      assertTrue(query.getFilter() instanceof FilterNode.Group);
    }

    @Test
    @DisplayName("should parse $gt filter")
    void parseGtFilter() {
      var params = new LinkedHashMap<String, String>();
      params.put("filters[views][$gt]", "100");

      CmsQuery query = parse("article", params);
      FilterNode.Leaf leaf = (FilterNode.Leaf) query.getFilter();
      assertEquals("views", leaf.getField());
      assertEquals(FilterNode.Operator.GT, leaf.getOperator());
      assertEquals("100", leaf.getValue());
    }

    @Test
    @DisplayName("should parse $null and $notNull filters")
    void parseNullFilters() {
      var params = new LinkedHashMap<String, String>();
      params.put("filters[title][$null]", "true");

      CmsQuery query = parse("article", params);
      FilterNode.Leaf leaf = (FilterNode.Leaf) query.getFilter();
      assertEquals(FilterNode.Operator.NULL, leaf.getOperator());
    }
  }

  @Nested
  @DisplayName("Logical operators")
  class LogicalOperators {

    @Test
    @DisplayName("should parse $and filter group")
    void parseAndGroup() {
      var params = new LinkedHashMap<String, String>();
      params.put("filters[$and][0][title][$eq]", "Hello");
      params.put("filters[$and][1][status][$eq]", "draft");

      CmsQuery query = parse("article", params);
      assertNotNull(query.getFilter());
      assertTrue(query.getFilter() instanceof FilterNode.Group);
      FilterNode.Group group = (FilterNode.Group) query.getFilter();
      assertEquals(FilterNode.Logic.AND, group.getLogic());
      assertEquals(2, group.getChildren().size());
    }

    @Test
    @DisplayName("should parse $or filter group")
    void parseOrGroup() {
      var params = new LinkedHashMap<String, String>();
      params.put("filters[$or][0][title][$contains]", "foo");
      params.put("filters[$or][1][title][$contains]", "bar");

      CmsQuery query = parse("article", params);
      FilterNode.Group group = (FilterNode.Group) query.getFilter();
      assertEquals(FilterNode.Logic.OR, group.getLogic());
    }
  }

  @Test
  @DisplayName("should parse complex combined query")
  void parseComplexQuery() {
    var params = new LinkedHashMap<String, String>();
    params.put("sort[0]", "createdAt:desc");
    params.put("sort[1]", "title:asc");
    params.put("pagination[page]", "1");
    params.put("pagination[pageSize]", "10");
    params.put("locale", "en");
    params.put("publicationState", "live");
    params.put("filters[title][$contains]", "search");

    CmsQuery query = parse("product", params);
    assertEquals("product", query.getContentType());
    assertEquals("en", query.getLocale());
    assertEquals("published", query.getStatus());
    assertEquals(1, query.getPage());
    assertEquals(10, query.getPageSize());
    assertEquals(2, query.getSort().size());
    assertNotNull(query.getFilter());
  }

  @Nested
  @DisplayName("Populate")
  class Populate {

    @Test
    @DisplayName("should parse populate=*")
    void populateAll() {
      var result = QueryParamParser.parsePopulate("*");
      assertEquals(1, result.size());
      assertTrue(result.get(0).isPopulateAll());
    }

    @Test
    @DisplayName("should parse simple field name")
    void populateSimpleField() {
      var result = QueryParamParser.parsePopulate("author");
      assertEquals(1, result.size());
      assertEquals("author", result.get(0).getFieldName());
      assertFalse(result.get(0).isPopulateAll());
    }

    @Test
    @DisplayName("should parse multiple fields as list")
    void populateMultipleFields() {
      var result = QueryParamParser.parsePopulate(List.of("author", "category"));
      assertEquals(2, result.size());
      assertEquals("author", result.get(0).getFieldName());
      assertEquals("category", result.get(1).getFieldName());
    }

    @Test
    @DisplayName("should parse nested populate from map")
    void populateNested() {
      var nestedMap = Map.of("author", Map.of("populate", List.of("avatar")));
      var result = QueryParamParser.parsePopulate(nestedMap);
      assertEquals(1, result.size());
      assertEquals("author", result.get(0).getFieldName());
      assertNotNull(result.get(0).getChildren());
      assertEquals(1, result.get(0).getChildren().size());
      assertEquals("avatar", result.get(0).getChildren().get(0).getFieldName());
    }

    @Test
    @DisplayName("should return empty list for null populate")
    void populateNull() {
      var result = QueryParamParser.parsePopulate(null);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should parse populate from query string")
    void populateFromQuery() {
      var params = new LinkedHashMap<String, String>();
      params.put("populate", "author");

      CmsQuery query = parse("article", params);
      assertNotNull(query.getPopulate());
      assertEquals(1, query.getPopulate().size());
      assertEquals("author", query.getPopulate().get(0).getFieldName());
    }

    @Test
    @DisplayName("should parse populate=* from query string")
    void populateAllFromQuery() {
      var params = new LinkedHashMap<String, String>();
      params.put("populate", "*");

      CmsQuery query = parse("article", params);
      assertNotNull(query.getPopulate());
      assertEquals(1, query.getPopulate().size());
      assertTrue(query.getPopulate().get(0).isPopulateAll());
    }

    @Test
    @DisplayName("should parse populate with fields filter")
    void populateWithFields() {
      var nestedMap = Map.of("author", Map.of("fields", List.of("name", "email")));
      var result = QueryParamParser.parsePopulate(nestedMap);
      assertEquals(1, result.size());
      assertEquals("author", result.get(0).getFieldName());
      assertNotNull(result.get(0).getFields());
      assertEquals(2, result.get(0).getFields().size());
      assertTrue(result.get(0).getFields().contains("name"));
      assertTrue(result.get(0).getFields().contains("email"));
    }

    @Test
    @DisplayName("should parse populate with depth override")
    void populateWithDepth() {
      var nestedMap = Map.of("author", Map.of("depth", 3, "populate", List.of("avatar")));
      var result = QueryParamParser.parsePopulate(nestedMap);
      assertEquals(1, result.size());
      assertEquals("author", result.get(0).getFieldName());
      assertEquals(Integer.valueOf(3), result.get(0).getDepthOverride());
      assertNotNull(result.get(0).getChildren());
      assertEquals(1, result.get(0).getChildren().size());
      assertEquals("avatar", result.get(0).getChildren().get(0).getFieldName());
    }

    @Test
    @DisplayName("should parse populate with fields and depth via numeric array syntax")
    void populateWithFieldsAndDepthArray() {
      var nestedMap = Map.of(
          "0", Map.of(
              "field", "author",
              "fields", List.of("name"),
              "depth", 2
          )
      );
      var result = QueryParamParser.parsePopulate(nestedMap);
      assertEquals(1, result.size());
      assertEquals("author", result.get(0).getFieldName());
      assertEquals(Integer.valueOf(2), result.get(0).getDepthOverride());
      assertNotNull(result.get(0).getFields());
      assertTrue(result.get(0).getFields().contains("name"));
    }

  }

  @Nested
  @DisplayName("Fields selection")
  class FieldsSelection {

    @Test
    @DisplayName("should parse single fields value")
    void parseSingleField() {
      var result = QueryParamParser.parseFields("title");
      assertEquals(1, result.size());
      assertTrue(result.contains("title"));
    }

    @Test
    @DisplayName("should parse multiple fields from list")
    void parseMultipleFields() {
      var result = QueryParamParser.parseFields(List.of("title", "content", "category"));
      assertEquals(3, result.size());
      assertTrue(result.contains("title"));
      assertTrue(result.contains("content"));
      assertTrue(result.contains("category"));
    }

    @Test
    @DisplayName("should parse multiple fields from map with numeric keys")
    void parseMultipleFieldsFromMap() {
      var result = QueryParamParser.parseFields(Map.of("0", "title", "1", "content"));
      assertEquals(2, result.size());
      assertTrue(result.contains("title"));
      assertTrue(result.contains("content"));
    }

    @Test
    @DisplayName("should return empty set for null")
    void parseFieldsNull() {
      var result = QueryParamParser.parseFields(null);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should parse fields from query string")
    void fieldsFromQuery() {
      var params = new LinkedHashMap<String, String>();
      params.put("fields[0]", "title");
      params.put("fields[1]", "content");

      CmsQuery query = parse("article", params);
      assertNotNull(query.getFields());
      assertEquals(2, query.getFields().size());
      assertTrue(query.getFields().contains("title"));
      assertTrue(query.getFields().contains("content"));
    }

    @Test
    @DisplayName("should parse single fields from query string")
    void singleFieldFromQuery() {
      CmsQuery query = parse("article", Map.of("fields", "title"));
      assertNotNull(query.getFields());
      assertEquals(1, query.getFields().size());
      assertTrue(query.getFields().contains("title"));
    }
  }

  /** Helper: parse params and return CmsQuery. */
  private static CmsQuery parse(String contentType, Map<String, String> params) {
    return QueryParamParser.parse(contentType, params);
  }
}
