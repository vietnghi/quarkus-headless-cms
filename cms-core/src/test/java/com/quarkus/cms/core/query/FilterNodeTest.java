package com.quarkus.cms.core.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FilterNode} — factory methods, tree construction, and value objects.
 */
class FilterNodeTest {

    // ---- Leaf creation ----

    @Test
    void shouldCreateEqLeaf() {
        FilterNode.Leaf leaf = FilterNode.eq("title", "Hello");
        assertEquals("title", leaf.getField());
        assertEquals(FilterNode.Operator.EQ, leaf.getOperator());
        assertEquals("Hello", leaf.getValue());
    }

    @Test
    void shouldCreateLeafWithExplicitOperator() {
        FilterNode.Leaf leaf = FilterNode.leaf("price", FilterNode.Operator.GT, 100);
        assertEquals("price", leaf.getField());
        assertEquals(FilterNode.Operator.GT, leaf.getOperator());
        assertEquals(100, leaf.getValue());
    }

    @Test
    void shouldCreateLeafWithNotOperator() {
        FilterNode.Leaf leaf = FilterNode.leaf("status", FilterNode.Operator.NE, "archived");
        assertEquals(FilterNode.Operator.NE, leaf.getOperator());
    }

    @Test
    void shouldCreateLeafWithContainsOperator() {
        FilterNode.Leaf leaf = FilterNode.leaf("body", FilterNode.Operator.CONTAINS, "keyword");
        assertEquals(FilterNode.Operator.CONTAINS, leaf.getOperator());
    }

    @Test
    void shouldCreateNullCheckLeaf() {
        FilterNode.Leaf leaf = FilterNode.leaf("summary", FilterNode.Operator.NULL, null);
        assertEquals(FilterNode.Operator.NULL, leaf.getOperator());
        assertNull(leaf.getValue());
    }

    // ---- AND groups ----

    @Test
    void shouldCreateAndGroupFromList() {
        FilterNode.Group group = FilterNode.and(List.of(
                FilterNode.eq("title", "Hello"),
                FilterNode.eq("status", "published")
        ));
        assertEquals(FilterNode.Logic.AND, group.getLogic());
        assertEquals(2, group.getChildren().size());
    }

    @Test
    void shouldCreateAndGroupFromVarargs() {
        FilterNode.Group group = FilterNode.and(
                FilterNode.eq("a", 1),
                FilterNode.eq("b", 2),
                FilterNode.eq("c", 3)
        );
        assertEquals(FilterNode.Logic.AND, group.getLogic());
        assertEquals(3, group.getChildren().size());
    }

    @Test
    void shouldHandleEmptyAndGroup() {
        FilterNode.Group group = FilterNode.and(List.of());
        assertEquals(FilterNode.Logic.AND, group.getLogic());
        assertTrue(group.getChildren().isEmpty());
    }

    // ---- OR groups ----

    @Test
    void shouldCreateOrGroupFromList() {
        FilterNode.Group group = FilterNode.or(List.of(
                FilterNode.eq("status", "draft"),
                FilterNode.eq("status", "published")
        ));
        assertEquals(FilterNode.Logic.OR, group.getLogic());
        assertEquals(2, group.getChildren().size());
    }

    @Test
    void shouldCreateOrGroupFromVarargs() {
        FilterNode.Group group = FilterNode.or(
                FilterNode.eq("x", "1"),
                FilterNode.eq("y", "2")
        );
        assertEquals(FilterNode.Logic.OR, group.getLogic());
        assertEquals(2, group.getChildren().size());
    }

    // ---- Nested groups ----

    @Test
    void shouldBuildNestedFilterTree() {
        // (status = published AND (category = news OR tags CONTAINS urgent))
        FilterNode.Group tree = FilterNode.and(
                FilterNode.eq("status", "published"),
                FilterNode.or(
                        FilterNode.eq("category", "news"),
                        FilterNode.leaf("tags", FilterNode.Operator.CONTAINS, "urgent")
                )
        );

        assertEquals(FilterNode.Logic.AND, tree.getLogic());
        assertEquals(2, tree.getChildren().size());

        FilterNode first = tree.getChildren().get(0);
        assertInstanceOf(FilterNode.Leaf.class, first);
        assertEquals("status", ((FilterNode.Leaf) first).getField());

        FilterNode second = tree.getChildren().get(1);
        assertInstanceOf(FilterNode.Group.class, second);
        FilterNode.Group orGroup = (FilterNode.Group) second;
        assertEquals(FilterNode.Logic.OR, orGroup.getLogic());
        assertEquals(2, orGroup.getChildren().size());
    }

    // ---- Operator codes ----

    @Test
    void shouldResolveAllOperatorCodes() {
        assertEquals(FilterNode.Operator.EQ, FilterNode.Operator.fromCode("$eq"));
        assertEquals(FilterNode.Operator.NE, FilterNode.Operator.fromCode("$ne"));
        assertEquals(FilterNode.Operator.GT, FilterNode.Operator.fromCode("$gt"));
        assertEquals(FilterNode.Operator.GTE, FilterNode.Operator.fromCode("$gte"));
        assertEquals(FilterNode.Operator.LT, FilterNode.Operator.fromCode("$lt"));
        assertEquals(FilterNode.Operator.LTE, FilterNode.Operator.fromCode("$lte"));
        assertEquals(FilterNode.Operator.CONTAINS, FilterNode.Operator.fromCode("$contains"));
        assertEquals(FilterNode.Operator.NOT_CONTAINS, FilterNode.Operator.fromCode("$notContains"));
        assertEquals(FilterNode.Operator.IN, FilterNode.Operator.fromCode("$in"));
        assertEquals(FilterNode.Operator.NOT_IN, FilterNode.Operator.fromCode("$nin"));
        assertEquals(FilterNode.Operator.NULL, FilterNode.Operator.fromCode("$null"));
        assertEquals(FilterNode.Operator.NOT_NULL, FilterNode.Operator.fromCode("$notNull"));
        assertEquals(FilterNode.Operator.STARTS_WITH, FilterNode.Operator.fromCode("$startsWith"));
        assertEquals(FilterNode.Operator.ENDS_WITH, FilterNode.Operator.fromCode("$endsWith"));
    }

    @Test
    void shouldThrowOnUnknownOperatorCode() {
        assertThrows(IllegalArgumentException.class,
                () -> FilterNode.Operator.fromCode("$unknown"));
    }

    // ---- Immutability ----

    @Test
    void groupChildrenShouldBeUnmodifiable() {
        FilterNode.Group group = FilterNode.and(FilterNode.eq("a", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> group.getChildren().add(FilterNode.eq("b", 2)));
    }

    // ---- toString ----

    @Test
    void leafToStringShouldShowFieldOperatorValue() {
        FilterNode.Leaf leaf = FilterNode.eq("title", "Hello");
        String str = leaf.toString();
        assertTrue(str.contains("title"));
        assertTrue(str.contains("EQ"));
        assertTrue(str.contains("Hello"));
    }

    @Test
    void groupToStringShouldShowLogicAndChildren() {
        FilterNode.Group group = FilterNode.and(FilterNode.eq("a", 1), FilterNode.eq("b", 2));
        String str = group.toString();
        assertTrue(str.contains("AND"));
    }
}
