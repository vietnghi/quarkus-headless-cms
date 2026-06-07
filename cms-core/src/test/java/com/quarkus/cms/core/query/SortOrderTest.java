package com.quarkus.cms.core.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SortOrder}.
 */
class SortOrderTest {

    @Test
    void shouldCreateAscendingSort() {
        SortOrder order = new SortOrder("title", SortOrder.Direction.ASC);
        assertEquals("title", order.getField());
        assertEquals(SortOrder.Direction.ASC, order.getDirection());
    }

    @Test
    void shouldCreateDescendingSort() {
        SortOrder order = new SortOrder("createdAt", SortOrder.Direction.DESC);
        assertEquals("createdAt", order.getField());
        assertEquals(SortOrder.Direction.DESC, order.getDirection());
    }

    @Test
    void ascAndDescAreDistinct() {
        assertNotEquals(SortOrder.Direction.ASC, SortOrder.Direction.DESC);
    }
}
