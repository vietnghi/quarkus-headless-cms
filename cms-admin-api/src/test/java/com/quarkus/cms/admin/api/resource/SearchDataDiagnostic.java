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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchDataDiagnostic {

    @Inject
    SearchService searchService;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    DraftPublishService draftPublishService;

    @Inject
    EntityManager entityManager;

    private static final String CT = "api::diag3.article";
    private static final String LOCALE = "en";

    @BeforeAll
    @Transactional
    void setup() {
        ContentTypeDefinition ct = ContentTypeDefinition.builder(CT, ContentTypeKind.COLLECTION_TYPE)
            .singularName("article3").pluralName("articles3").displayName("Article3")
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).required(true).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()
            ))
            .build();
        schemaStorageService.registerContentType(ct, "setup", "test-user");
    }

    void log(String msg) {
        try {
            Files.writeString(Paths.get("/tmp/search_data_diag.log"),
                msg + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) { /* ignore */ }
    }

    @BeforeEach
    void resetLog() {
        try {
            Files.deleteIfExists(Paths.get("/tmp/search_data_diag.log"));
        } catch (Exception e) { /* ignore */ }
    }

    @Test
    @Transactional
    void testDataDiagnostic() {
        CmsEntry entry = draftPublishService.createDraft(CT,
            Map.of("title", "Hello World", "body", "Body content"),
            LOCALE, 1L);
        log("AFTER_CREATE: id=" + entry.id + " data=" + entry.data);

        entityManager.flush();
        log("AFTER_FLUSH: id=" + entry.id + " data=" + entry.data);

        // Check via native query
        jakarta.persistence.Query nativeQ = entityManager.createNativeQuery(
            "SELECT id, document_id, content_type, data, locale, status FROM cms_entries");
        List<Object[]> rows = nativeQ.getResultList();
        log("NATIVE_ROWS=" + rows.size());
        for (Object[] row : rows) {
            log("  native: id=" + row[0] + " ct=" + row[2] + " data=" + row[3] + " locale=" + row[4]);
        }

        // Check via JPQL
        jakarta.persistence.Query jpqlQ = entityManager.createQuery(
            "SELECT e FROM CmsEntry e", CmsEntry.class);
        List<CmsEntry> results = jpqlQ.getResultList();
        log("JPQL_ENTITIES=" + results.size());
        for (CmsEntry e : results) {
            log("  jpql: id=" + e.id + " ct=" + e.contentType + " data=" + e.data
                + " dataNull=" + (e.data == null));
            if (e.data != null) {
                for (Map.Entry<String, Object> me : e.data.entrySet()) {
                    log("    data[" + me.getKey() + "]=" + me.getValue()
                        + " type=" + (me.getValue() != null ? me.getValue().getClass().getName() : "null"));
                }
            }
        }

        // Check via CmsEntry.list (Panache)
        List<CmsEntry> panacheResults = CmsEntry.listAll();
        log("PANACHE_ENTITIES=" + panacheResults.size());
        for (CmsEntry e : panacheResults) {
            log("  panache: id=" + e.id + " data=" + e.data + " dataNull=" + (e.data == null));
        }

        // Search
        SearchResponse response = searchService.search("world", null, null, 0, 20);
        log("SEARCH: total=" + response.total + " results=" + response.results.size());

        // Assert
        assertTrue(response.total >= 1, "Should find at least 1 result for 'world'. Total=" + response.total);
    }
}
