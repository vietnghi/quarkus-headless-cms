package com.quarkus.cms.core.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PopulateNode} — tree-structured population specification.
 */
class PopulateNodeTest {

    @Test
    void shouldCreateSimplePopulateNode() {
        PopulateNode node = new PopulateNode("author");

        assertEquals("author", node.getFieldName());
        assertNull(node.getChildren());
        assertFalse(node.isPopulateAll());
    }

    @Test
    void shouldSupportPopulateAll() {
        PopulateNode node = new PopulateNode();
        node.setPopulateAll(true);

        assertNull(node.getFieldName());
        assertTrue(node.isPopulateAll());
    }

    @Test
    void shouldSupportNestedPopulation() {
        PopulateNode category = new PopulateNode("category");
        PopulateNode subCategory = new PopulateNode("subCategory");
        category.setChildren(List.of(subCategory));

        assertEquals("category", category.getFieldName());
        assertNotNull(category.getChildren());
        assertEquals(1, category.getChildren().size());
        assertEquals("subCategory", category.getChildren().get(0).getFieldName());
    }

    @Test
    void shouldSupportDeepNesting() {
        // author -> profile -> avatar
        PopulateNode author = new PopulateNode("author");
        PopulateNode profile = new PopulateNode("profile");
        PopulateNode avatar = new PopulateNode("avatar");
        profile.setChildren(List.of(avatar));
        author.setChildren(List.of(profile));

        assertEquals("author", author.getFieldName());
        assertEquals("profile", author.getChildren().get(0).getFieldName());
        assertEquals("avatar", author.getChildren().get(0).getChildren().get(0).getFieldName());
    }

    @Test
    void shouldSupportEmptyConstructor() {
        PopulateNode node = new PopulateNode();
        assertNull(node.getFieldName());
        assertNull(node.getChildren());
        assertFalse(node.isPopulateAll());
    }

    @Test
    void shouldSupportMutableFields() {
        PopulateNode node = new PopulateNode("original");
        node.setFieldName("updated");
        node.setChildren(List.of(new PopulateNode("child")));
        node.setPopulateAll(true);

        assertEquals("updated", node.getFieldName());
        assertNotNull(node.getChildren());
        assertTrue(node.isPopulateAll());
    }
}
