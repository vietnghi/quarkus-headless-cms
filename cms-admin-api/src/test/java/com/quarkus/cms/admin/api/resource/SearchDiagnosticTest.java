package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.admin.api.service.SearchService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.draft.DraftPublishService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchDiagnosticTest {

    @Inject
    SearchService searchService;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    DraftPublishService draftPublishService;

    @Inject
    EntityManager entityManager;

    private static final String ARTICLE_CT = "api::diag.article";
    private static final String LOCALE = "en";

    @BeforeAll
    @Transactional
    void setup() {
        ContentTypeDefinition articleCt = ContentTypeDefinition.builder(ARTICLE_CT, ContentTypeKind.COLLECTION_TYPE)
            .singularName("article")
            .pluralName("articles")
            .displayName("Article")
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).required(true).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()
            ))
            .build();
        schemaStorageService.registerContentType(articleCt, "Test setup", "test-user");
    }

    @Test
    @Transactional
    void testIdColumnInTable() {
        // Check the SQLite table schema to understand id column
        jakarta.persistence.Query pragmaQ = entityManager.createNativeQuery("PRAGMA table_info(cms_entries)");
        List<Object[]> schema = pragmaQ.getResultList();
        try {
            StringBuilder sb = new StringBuilder("TABLE SCHEMA:\n");
            for (Object[] row : schema) {
                sb.append("  cid=").append(row[0])
                  .append(" name=").append(row[1])
                  .append(" type=").append(row[2])
                  .append(" notnull=").append(row[3])
                  .append(" dflt=").append(row[4])
                  .append(" pk=").append(row[5]).append("\n");
            }
            java.nio.file.Files.writeString(
                java.nio.file.Paths.get("/data/.hermes/kanban/boards/quarkus-headless-cms/workspaces/t_35ba1072/search_diag.log"), sb.toString());
        } catch (Exception ignored) {}
    }

    @Test
    @Transactional
    void testCreateLoadSearch() {
        CmsEntry entry = draftPublishService.createDraft(ARTICLE_CT,
            Map.of("title", "Hello World", "body", "Body content"),
            LOCALE, 1L);
        entityManager.flush();

        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get("/tmp/search_diag.log"),
                "entry.id=" + entry.id + "\n" +
                "entry.data=" + entry.data + "\n"
            );
        } catch (Exception ignored) {}

        // Try JPQL for specific field
        jakarta.persistence.Query idQ = entityManager.createQuery("SELECT e.id FROM CmsEntry e");
        List<Long> ids = idQ.getResultList();
        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get("/tmp/search_diag.log"),
                "JPQL ids=" + ids + "\n", java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}

        // Try native query for count
        jakarta.persistence.Query countQ = entityManager.createNativeQuery("SELECT COUNT(*) FROM cms_entries");
        Object count = countQ.getSingleResult();
        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get("/tmp/search_diag.log"),
                "NATIVE COUNT=" + count + "\n", java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}

        // Try native query for all columns
        jakarta.persistence.Query nativeQ = entityManager.createNativeQuery(
            "SELECT id, document_id, content_type, locale, status, data, created_at, updated_at FROM cms_entries");
        List<Object[]> rows = nativeQ.getResultList();
        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get("/tmp/search_diag.log"),
                "NATIVE ROWS=" + rows.size() + "\n", java.nio.file.StandardOpenOption.APPEND);
            for (Object[] row : rows) {
                java.nio.file.Files.writeString(java.nio.file.Paths.get("/tmp/search_diag.log"),
                    "  row[0]=" + row[0] + " (" + (row[0] != null ? row[0].getClass().getName() : "null") + ")" +
                    " row[1]=" + row[1] +
                    " row[2]=" + row[2] +
                    " row[3]=" + row[3] +
                    " row[4]=" + row[4] +
                    " row[5]=" + row[5] +
                    " row[6]=" + row[6] +
                    " row[7]=" + row[7] + "\n",
                    java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {}

        // Search
        SearchResponse response = searchService.search("world", null, null, 0, 20);
        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get("/tmp/search_diag.log"),
                "SEARCH total=" + response.total + "\n", java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        assertTrue(response.total >= 1, "Should find at least 1 result for 'world'. Total=" + response.total);
    }
}
