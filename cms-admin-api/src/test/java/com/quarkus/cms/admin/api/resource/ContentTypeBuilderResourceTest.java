package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.core.schema.model.*;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.schema.storage.SchemaValidationException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Content-Type Builder service layer (SchemaStorageService).
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContentTypeBuilderResourceTest {

    @Inject
    SchemaStorageService schemaService;

    private static final String CT_UID = "api::article.article";
    private static final String COMP_UID = "shared.seo";
    private static final String AUTHOR_UID = "api::author.author";

    @BeforeAll
    @Transactional
    void setupAuthorType() {
        // Register a simple author content type so relation validation passes
        // Note: @Transactional not used here; the SchemaStorageService handles its own transactions
        ContentTypeDefinition author = ContentTypeDefinition.builder(AUTHOR_UID, ContentTypeKind.COLLECTION_TYPE)
            .singularName("author")
            .pluralName("authors")
            .displayName("Author")
            .fields(List.of(
                FieldDefinition.builder("name", FieldType.STRING).required(true).build()
            ))
            .build();

        if (schemaService.getContentType(AUTHOR_UID) == null) {
            schemaService.registerContentType(author, "Setup", "test-user");
        }
    }

    // ---- Content Type Tests ----

    @Test
    @Order(1)
    void shouldListContentTypesNotNull() {
        List<ContentTypeDefinition> types = schemaService.getAllContentTypes();
        assertNotNull(types);
    }

    @Test
    @Order(2)
    void shouldCreateContentType() {
        ContentTypeDefinition ct = ContentTypeDefinition.builder(CT_UID, ContentTypeKind.COLLECTION_TYPE)
            .singularName("article")
            .pluralName("articles")
            .displayName("Article")
            .description("A blog article content type")
            .draftAndPublish(true)
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).required(true).maxLength(255).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build(),
                FieldDefinition.builder("publishedDate", FieldType.DATETIME).build()
            ))
            .relations(List.of(
                RelationDefinition.builder("author", RelationType.MANY_TO_ONE, AUTHOR_UID).build()
            ))
            .build();

        ContentTypeDefinition saved = schemaService.registerContentType(ct, "Initial creation", "test-user");
        assertNotNull(saved);
        assertEquals(CT_UID, saved.getUid());
        assertEquals(ContentTypeKind.COLLECTION_TYPE, saved.getKind());
        assertEquals("article", saved.getSingularName());
        assertEquals(3, saved.getFields().size());
        assertEquals(1, saved.getRelations().size());
    }

    @Test
    @Order(3)
    void shouldGetContentType() {
        ContentTypeDefinition ct = schemaService.getContentType(CT_UID);
        assertNotNull(ct);
        assertEquals(CT_UID, ct.getUid());
    }

    @Test
    @Order(4)
    void shouldReturnNullForUnknownContentType() {
        ContentTypeDefinition ct = schemaService.getContentType("unknown::type.type");
        assertNull(ct);
    }

    @Test
    @Order(5)
    void shouldUpdateContentType() {
        ContentTypeDefinition existing = schemaService.getContentType(CT_UID);
        assertNotNull(existing);

        ContentTypeDefinition updated = ContentTypeDefinition.builder(CT_UID, existing.getKind())
            .singularName(existing.getSingularName())
            .pluralName(existing.getPluralName())
            .displayName("Updated Article")
            .description(existing.getDescription())
            .draftAndPublish(existing.isDraftAndPublish())
            .fields(existing.getFields())
            .relations(existing.getRelations())
            .build();

        ContentTypeDefinition saved = schemaService.registerContentType(updated, "Updated display name", "test-user");
        assertEquals("Updated Article", saved.getDisplayName());
    }

    @Test
    @Order(6)
    void shouldGetVersionHistory() {
        List<SchemaVersion> versions = schemaService.getVersionHistory(CT_UID);
        assertNotNull(versions);
        assertTrue(versions.size() >= 2); // create + update
    }

    @Test
    @Order(7)
    void shouldRejectInvalidContentType() {
        // A content type with a relation targeting a non-existent type
        ContentTypeDefinition invalidCt = ContentTypeDefinition.builder("api::bad.bad", ContentTypeKind.COLLECTION_TYPE)
            .singularName("bad")
            .pluralName("bads")
            .displayName("Bad")
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).build()
            ))
            .relations(List.of(
                RelationDefinition.builder("related", RelationType.MANY_TO_ONE, "api::nonexistent.nonexistent").build()
            ))
            .build();

        assertThrows(SchemaValidationException.class, () ->
            schemaService.registerContentType(invalidCt, "Invalid test", "test-user"));
    }

    // ---- Component Tests ----

    @Test
    @Order(10)
    void shouldCreateComponent() {
        ComponentDefinition comp = ComponentDefinition.builder(COMP_UID)
            .category("shared")
            .displayName("SEO")
            .description("SEO metadata component")
            .fields(List.of(
                FieldDefinition.builder("metaTitle", FieldType.STRING).build(),
                FieldDefinition.builder("metaDescription", FieldType.TEXT).maxLength(160).build()
            ))
            .build();

        ComponentDefinition saved = schemaService.registerComponent(comp, "Initial component creation", "test-user");
        assertNotNull(saved);
        assertEquals(COMP_UID, saved.getUid());
        assertEquals("shared", saved.getCategory());
        assertEquals(2, saved.getFields().size());
    }

    @Test
    @Order(11)
    void shouldGetComponent() {
        ComponentDefinition comp = schemaService.getComponent(COMP_UID);
        assertNotNull(comp);
        assertEquals(COMP_UID, comp.getUid());
    }

    @Test
    @Order(12)
    void shouldListComponentsNotEmpty() {
        List<ComponentDefinition> components = schemaService.getAllComponents();
        assertNotNull(components);
        assertFalse(components.isEmpty());
    }

    @Test
    @Order(13)
    void shouldUpdateComponent() {
        ComponentDefinition existing = schemaService.getComponent(COMP_UID);
        assertNotNull(existing);

        ComponentDefinition updated = ComponentDefinition.builder(COMP_UID)
            .category(existing.getCategory())
            .displayName("Updated SEO")
            .description(existing.getDescription())
            .fields(existing.getFields())
            .build();

        ComponentDefinition saved = schemaService.registerComponent(updated, "Updated display name", "test-user");
        assertEquals("Updated SEO", saved.getDisplayName());
    }

    @Test
    @Order(14)
    void shouldGetComponentVersionHistory() {
        List<SchemaVersion> versions = schemaService.getVersionHistory(COMP_UID);
        assertNotNull(versions);
        assertTrue(versions.size() >= 2); // create + update
    }

    // ---- Rollback ----

    @Test
    @Order(15)
    void shouldRollbackContentType() {
        ContentTypeDefinition ct = schemaService.rollbackContentType(CT_UID, "Revert to original", "test-user");
        assertNotNull(ct);
        // After rollback, displayName should be back to "Article"
        assertEquals("Article", ct.getDisplayName());
    }

    @Test
    @Order(16)
    void shouldRejectRollbackWithNoPrevious() {
        // Delete and recreate with only one version
        schemaService.deleteContentType(CT_UID);

        ContentTypeDefinition ct = ContentTypeDefinition.builder(CT_UID, ContentTypeKind.COLLECTION_TYPE)
            .singularName("article")
            .pluralName("articles")
            .displayName("Article")
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).required(true).build()
            ))
            .build();

        schemaService.registerContentType(ct, "Recreated", "test-user");

        // Now try rollback — should fail (no previous version)
        assertThrows(IllegalStateException.class, () ->
            schemaService.rollbackContentType(CT_UID, "test", "test-user"));
    }

    // ---- Delete ----

    @Test
    @Order(20)
    void shouldDeleteComponent() {
        schemaService.deleteComponent(COMP_UID);
        assertNull(schemaService.getComponent(COMP_UID));
    }

    @Test
    @Order(21)
    void shouldDeleteContentType() {
        schemaService.deleteContentType(CT_UID);
        assertNull(schemaService.getContentType(CT_UID));
    }
}
