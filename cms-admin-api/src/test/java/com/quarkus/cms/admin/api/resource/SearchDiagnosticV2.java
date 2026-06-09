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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchDiagnosticV2 {

    @Inject
    SearchService searchService;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    DraftPublishService draftPublishService;

    @Inject
    EntityManager entityManager;

    private static final String CT = "api::diag2.article";
    private static final String LOCALE = "en";

    @BeforeAll
    @Transactional
    void setup() {
        ContentTypeDefinition ct = ContentTypeDefinition.builder(CT, ContentTypeKind.COLLECTION_TYPE)
            .singularName("article")
            .pluralName("articles")
            .displayName("Article")
            .fields(List.of(
                FieldDefinition.builder("title", FieldType.STRING).required(true).build(),
                FieldDefinition.builder("body", FieldType.RICHTEXT).build()
            ))
            .build();
        schemaStorageService.registerContentType(ct, "Test setup", "test-user");
    }

    @Test
    @Transactional
    void testDataLoadDiagnostic() throws Exception {
        CmsEntry entry = draftPublishService.createDraft(CT,
            Map.of("title", "Hello World", "body", "Body content"),
            LOCALE, 1L);
        entityManager.flush();

        StringBuilder sb = new StringBuilder();

        // Load via JPQL
        jakarta.persistence.Query q = entityManager.createQuery("SELECT e FROM CmsEntry e WHERE e.contentType = :ct", CmsEntry.class);
        q.setParameter("ct", CT);
        List<CmsEntry> loaded = q.getResultList();
        sb.append("JPQL loaded count: ").append(loaded.size()).append("\n");
        if (!loaded.isEmpty()) {
            CmsEntry first = loaded.get(0);
            sb.append("  entity.id=").append(first.id).append("\n");
            sb.append("  entity.data=").append(first.data).append("\n");
            sb.append("  entity.data class=").append(first.data != null ? first.data.getClass().getName() : "null").append("\n");
            sb.append("  entity.documentId=").append(first.documentId).append("\n");

            // Check dataContains manually
            Map<String, Object> data = first.data;
            if (data != null) {
                boolean found = false;
                for (Object val : data.values()) {
                    if (val instanceof String s && s.toLowerCase().contains("world")) {
                        found = true;
                        sb.append("  FOUND 'world' in value: ").append(s).append("\n");
                    }
                }
                sb.append("  dataContains result: ").append(found).append("\n");
            }
        }

        Files.writeString(Paths.get("/tmp/search_diag2.log"), sb.toString());
        System.out.println(sb.toString());
        assertFalse(loaded.isEmpty(), "Should load entries via JPQL");
    }
}
