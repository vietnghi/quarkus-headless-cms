package com.quarkus.cms.graphql;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.FilterNode;
import com.quarkus.cms.core.query.SortOrder;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.core.schema.model.*;
import com.quarkus.cms.core.schema.relation.RelationService;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.schema.zone.DynamicZoneService;
import com.quarkus.cms.draft.DraftPublishService;
import com.quarkus.cms.graphql.model.*;
import com.quarkus.cms.graphql.model.SchemaTypes.*;
import com.quarkus.cms.graphql.service.GraphQLContentService;
import com.quarkus.cms.i18n.service.I18nService;
import com.quarkus.cms.i18n.service.LocaleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GraphQL module's core components.
 *
 * <p>Tests the content service layer (filter/sort/pagination parsing), model types,
 * and collection response formatting.
 */
@DisplayName("GraphQL Module")
class CmsGraphQLModuleTest {

  // ================================================================
  // Model Type Tests
  // ================================================================

  @Nested
  @DisplayName("Entry model")
  class EntryModelTest {

    @Test
    @DisplayName("should create Entry from CmsEntry entity")
    void fromCmsEntry() {
      CmsEntry entity = new CmsEntry();
      entity.id = 1L;
      entity.documentId = "doc-123";
      entity.contentType = "api::article.article";
      entity.locale = "en";
      entity.status = "draft";
      entity.createdAt = Instant.parse("2026-01-01T00:00:00Z");
      entity.updatedAt = Instant.parse("2026-01-02T00:00:00Z");
      entity.publishedAt = Instant.parse("2026-01-03T00:00:00Z");
      entity.data = Map.of("title", "Hello World", "views", 42);

      Entry entry = new Entry(entity);

      assertEquals(1L, entry.getId());
      assertEquals("doc-123", entry.getDocumentId());
      assertEquals("api::article.article", entry.getContentType());
      assertEquals("en", entry.getLocale());
      assertEquals("draft", entry.getStatus());
      assertEquals("Hello World", entry.getData().get("title"));
      assertEquals(42, entry.getData().get("views"));
    }

    @Test
    @DisplayName("should handle null data map gracefully")
    void nullData() {
      CmsEntry entity = new CmsEntry();
      entity.data = null;
      Entry entry = new Entry(entity);
      assertNotNull(entry.getData());
      assertTrue(entry.getData().isEmpty());
    }
  }

  @Nested
  @DisplayName("EntryCollection model")
  class EntryCollectionTest {

    @Test
    @DisplayName("should compute pageCount correctly")
    void pageCount() {
      var entries = List.of(new Entry(new CmsEntry()));
      var coll = new EntryCollection(entries, 1, 10, 25);
      assertEquals(3, coll.getMeta().getPageCount());
      assertEquals(25, coll.getMeta().getTotal());
      assertEquals(1, coll.getMeta().getPage());
      assertEquals(10, coll.getMeta().getPageSize());
      assertEquals(1, coll.getData().size());
    }

    @Test
    @DisplayName("should handle empty collection")
    void emptyCollection() {
      var coll = new EntryCollection(List.of(), 1, 25, 0);
      assertEquals(0, coll.getMeta().getPageCount());
      assertTrue(coll.getData().isEmpty());
    }
  }

  @Nested
  @DisplayName("AuthPayload model")
  class AuthPayloadTest {

    @Test
    @DisplayName("should create auth payload with user info")
    void create() {
      var roles = Set.of("authenticated");
      var user = new AuthPayload.UserInfo(1L, "johndoe", "john@example.com", roles);
      var payload = new AuthPayload("jwt-token", user);

      assertEquals("jwt-token", payload.getJwt());
      assertEquals(1L, payload.getUser().getId());
      assertEquals("johndoe", payload.getUser().getUsername());
      assertEquals("john@example.com", payload.getUser().getEmail());
      assertTrue(payload.getUser().getRoles().contains("authenticated"));
    }
  }

  @Nested
  @DisplayName("PaginationInput model")
  class PaginationInputTest {

    @Test
    @DisplayName("should have sensible defaults")
    void defaults() {
      var input = new PaginationInput();
      assertEquals(1, input.getPage());
      assertEquals(25, input.getPageSize());
    }

    @Test
    @DisplayName("should allow custom values")
    void custom() {
      var input = new PaginationInput(3, 50);
      assertEquals(3, input.getPage());
      assertEquals(50, input.getPageSize());
    }
  }

  @Nested
  @DisplayName("SchemaTypes introspection")
  class SchemaTypesTest {

    @Test
    @DisplayName("should create ContentTypeSchema from definition")
    void contentTypeSchema() {
      var fields = List.of(
          FieldDefinition.builder("title", FieldType.STRING).required(true).build(),
          FieldDefinition.builder("views", FieldType.INTEGER).build());

      var ct = ContentTypeDefinition.builder("api::article.article", ContentTypeKind.COLLECTION_TYPE)
          .singularName("article")
          .pluralName("articles")
          .displayName("Article")
          .fields(fields)
          .draftAndPublish(true)
          .build();

      var schema = new ContentTypeSchema(ct);

      assertEquals("api::article.article", schema.getUid());
      assertEquals("COLLECTION_TYPE", schema.getKind());
      assertEquals("article", schema.getSingularName());
      assertEquals("articles", schema.getPluralName());
      assertEquals(2, schema.getFields().size());
      assertTrue(schema.isDraftAndPublish());
    }

    @Test
    @DisplayName("should create ComponentSchema from definition")
    void componentSchema() {
      var fields = List.of(
          FieldDefinition.builder("url", FieldType.STRING).build());

      var comp = ComponentDefinition.builder("default.seo")
          .category("default")
          .displayName("SEO")
          .fields(fields)
          .build();

      var schema = new ComponentSchema(comp);

      assertEquals("default.seo", schema.getUid());
      assertEquals("default", schema.getCategory());
      assertEquals("SEO", schema.getDisplayName());
      assertEquals(1, schema.getFields().size());
    }
  }

  // ================================================================
  // GraphQLContentService Tests
  // ================================================================

  @Nested
  @DisplayName("GraphQLContentService")
  @ExtendWith(MockitoExtension.class)
  class ContentServiceTest {

    @Mock CmsEntryRepository entryRepository;
    @Mock SchemaStorageService schemaService;
    @Mock RelationService relationService;
    @Mock DynamicZoneService dynamicZoneService;
    @Mock DraftPublishService draftPublishService;
    @Mock I18nService i18nService;
    @Mock LocaleService localeService;

    @InjectMocks GraphQLContentService contentService;

    private final CmsEntry sampleEntry = new CmsEntry();
    private final CmsEntry sampleEntry2 = new CmsEntry();

    @BeforeEach
    void setUp() {
      sampleEntry.id = 1L;
      sampleEntry.documentId = "doc-001";
      sampleEntry.contentType = "api::article.article";
      sampleEntry.locale = "en";
      sampleEntry.status = "published";
      sampleEntry.data = Map.of("title", "Article 1");

      sampleEntry2.id = 2L;
      sampleEntry2.documentId = "doc-002";
      sampleEntry2.contentType = "api::article.article";
      sampleEntry2.locale = "en";
      sampleEntry2.status = "published";
      sampleEntry2.data = Map.of("title", "Article 2");

      lenient().when(localeService.getDefaultLocale()).thenReturn("en");
    }

    @Test
    @DisplayName("findMany should return paginated results")
    void findMany() {
      when(entryRepository.list(any(CmsQuery.class)))
          .thenReturn(List.of(sampleEntry, sampleEntry2));
      when(entryRepository.count(any(CmsQuery.class)))
          .thenReturn(2L);

      EntryCollection result = contentService.findMany(
          "api::article.article", null, null, null, "en", "published");

      assertEquals(2, result.getData().size());
      assertEquals(2, result.getMeta().getTotal());
      assertEquals(1, result.getMeta().getPage());
    }

    @Test
    @DisplayName("findMany should respect pagination parameters")
    void findManyWithPagination() {
      when(entryRepository.list(any(CmsQuery.class)))
          .thenReturn(List.of(sampleEntry));
      when(entryRepository.count(any(CmsQuery.class)))
          .thenReturn(50L);

      PaginationInput pagination = new PaginationInput(2, 10);
      EntryCollection result = contentService.findMany(
          "api::article.article", null, null, pagination, "en", "published");

      assertEquals(2, result.getMeta().getPage());
      assertEquals(10, result.getMeta().getPageSize());
      assertEquals(5, result.getMeta().getPageCount());
      assertEquals(50, result.getMeta().getTotal());
    }

    @Test
    @DisplayName("findOne should return a single entry by documentId")
    void findOne() {
      when(entryRepository.findPublished("doc-001", "en")).thenReturn(sampleEntry);

      Entry result = contentService.findOne("api::article.article", "doc-001", "en", null);

      assertNotNull(result);
      assertEquals("doc-001", result.getDocumentId());
    }

    @Test
    @DisplayName("findOne should return null for missing entries")
    void findOneNotFound() {
      when(entryRepository.findPublished("doc-999", "en")).thenReturn(null);
      when(entryRepository.findDraft("doc-999", "en")).thenReturn(null);

      Entry result = contentService.findOne("api::article.article", "doc-999", "en", null);

      assertNull(result);
    }

    @Test
    @DisplayName("create should delegate to repository")
    void create() {
      when(entryRepository.createWithCreator(
          eq("api::article.article"), anyMap(), eq("en"), eq(1L)))
          .thenReturn(sampleEntry);

      Entry result = contentService.create(
          "api::article.article", Map.of("title", "New"), "en", 1L);

      assertNotNull(result);
      assertEquals("doc-001", result.getDocumentId());
    }

    @Test
    @DisplayName("delete should return true when entry exists")
    void deleteExists() {
      when(entryRepository.findByDocumentId("doc-001")).thenReturn(sampleEntry);

      boolean result = contentService.delete("doc-001");
      assertTrue(result);
      verify(entryRepository).delete("doc-001");
    }

    @Test
    @DisplayName("delete should return false when entry not found")
    void deleteNotFound() {
      when(entryRepository.findByDocumentId("doc-999")).thenReturn(null);

      boolean result = contentService.delete("doc-999");
      assertFalse(result);
      verify(entryRepository, never()).delete(anyString());
    }

    @Test
    @DisplayName("getContentTypes should return schema introspection types")
    void getContentTypes() {
      var ctDef = ContentTypeDefinition.builder("api::article.article", ContentTypeKind.COLLECTION_TYPE)
          .fields(List.of(
              FieldDefinition.builder("title", FieldType.STRING).build()))
          .build();

      when(schemaService.getAllContentTypes()).thenReturn(List.of(ctDef));

      List<ContentTypeSchema> schemas = contentService.getContentTypes();

      assertEquals(1, schemas.size());
      assertEquals("api::article.article", schemas.get(0).getUid());
    }

    @Test
    @DisplayName("getLocales should return locale info")
    void getLocales() {
      var dto = new com.quarkus.cms.i18n.dto.LocaleDto();
      dto.code = "en";
      dto.displayName = "English";
      dto.isDefault = true;
      dto.enabled = true;

      when(localeService.listLocales()).thenReturn(List.of(dto));

      var locales = contentService.getLocales();

      assertEquals(1, locales.size());
      assertEquals("en", locales.get(0).getCode());
      assertTrue(locales.get(0).isIsDefault());
    }
  }
}
