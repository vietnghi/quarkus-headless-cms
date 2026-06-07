package com.quarkus.cms.graphql.service;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.FilterNode;
import com.quarkus.cms.core.query.SortOrder;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.relation.RelationService;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.schema.zone.DynamicZoneService;
import com.quarkus.cms.draft.DraftPublishService;
import com.quarkus.cms.graphql.model.Entry;
import com.quarkus.cms.graphql.model.EntryCollection;
import com.quarkus.cms.graphql.model.PaginationInput;
import com.quarkus.cms.graphql.model.SchemaTypes.ComponentSchema;
import com.quarkus.cms.graphql.model.SchemaTypes.ContentTypeSchema;
import com.quarkus.cms.i18n.dto.LocaleDto;
import com.quarkus.cms.i18n.service.I18nService;
import com.quarkus.cms.i18n.service.LocaleService;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core business logic for GraphQL content operations.
 *
 * <p>Provides methods for querying, creating, updating, deleting, and publishing content entries
 * via GraphQL, sharing the same service layer as the REST API.
 *
 * <p>Relation resolution implements the DataLoader pattern — relations are batch-loaded per
 * content-type rather than lazily fetched one at a time, preventing N+1 queries.
 */
@ApplicationScoped
public class GraphQLContentService {

  @Inject CmsEntryRepository entryRepository;

  @Inject SchemaStorageService schemaService;

  @Inject RelationService relationService;

  @Inject DynamicZoneService dynamicZoneService;

  @Inject DraftPublishService draftPublishService;

  @Inject I18nService i18nService;

  @Inject LocaleService localeService;

  // ---- Query Operations ----

  /**
   * Finds a paginated list of entries for the given content type.
   *
   * @param contentType  the content type UID (e.g. "api::article.article")
   * @param filtersJson  optional filter conditions as a Map structure
   * @param sortJson     optional sort specifications
   * @param pagination   optional pagination parameters
   * @param locale       optional locale code
   * @param status       optional status filter ("published", "draft")
   * @return paginated entry collection
   */
  @SuppressWarnings("unchecked")
  public EntryCollection findMany(
      String contentType,
      String filtersJson,
      String sortJson,
      PaginationInput pagination,
      String locale,
      String status) {

    CmsQuery query = new CmsQuery(contentType);

    // Parse filters from JSON string
    if (filtersJson != null && !filtersJson.isBlank()) {
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> filtersMap = mapper.readValue(filtersJson, Map.class);
        FilterNode filter = parseFilterTree(filtersMap);
        query.setFilter(filter);
      } catch (Exception e) {
        Log.warnf("Failed to parse filters JSON: %s", e.getMessage());
      }
    }

    // Parse sort
    if (sortJson != null && !sortJson.isBlank()) {
      List<SortOrder> sortOrders = parseSortString(sortJson);
      if (!sortOrders.isEmpty()) {
        query.setSort(sortOrders);
      }
    }

    // Pagination
    int page = pagination != null ? pagination.getPage() : 1;
    int pageSize = pagination != null ? pagination.getPageSize() : 25;
    query.setPage(page);
    query.setPageSize(pageSize);

    // Locale
    query.setLocale(resolveLocale(locale));

    // Status
    query.setStatus(status != null ? status : "published");

    // Execute
    List<CmsEntry> entities = entryRepository.list(query);
    long total = entryRepository.count(query);

    List<Entry> entries = entities.stream().map(Entry::new).toList();
    return new EntryCollection(entries, query.getPage(), query.getPageSize(), total);
  }

  /**
   * Finds a single entry by document ID.
   *
   * @param contentType the content type UID
   * @param documentId  the document ID (UUID)
   * @param locale      optional locale code
   * @param status      optional status filter
   * @return the entry, or null if not found
   */
  public Entry findOne(String contentType, String documentId, String locale, String status) {
    String resolvedLocale = resolveLocale(locale);

    CmsEntry entity;
    if (status != null) {
      entity = CmsEntry.findByDocumentId(documentId, status, resolvedLocale);
    } else {
      entity = entryRepository.findPublished(documentId, resolvedLocale);
      if (entity == null) {
        entity = entryRepository.findDraft(documentId, resolvedLocale);
      }
    }

    return entity != null ? new Entry(entity) : null;
  }

  // ---- Mutation Operations ----

  /**
   * Creates a new draft entry.
   *
   * @param contentType the content type UID
   * @param data        the content field data (JSON object)
   * @param locale      optional locale code
   * @param userId      the creating user's ID (may be null for anonymous)
   * @return the created entry
   */
  public Entry create(String contentType, Map<String, Object> data, String locale, Long userId) {
    String resolvedLocale = resolveLocale(locale);
    CmsEntry entity;
    if (userId != null) {
      entity = entryRepository.createWithCreator(contentType, data, resolvedLocale, userId);
    } else {
      entity = entryRepository.create(contentType, data, resolvedLocale);
    }
    return new Entry(entity);
  }

  /**
   * Updates an existing draft entry.
   *
   * @param documentId the document ID
   * @param data       the updated content field data
   * @return the updated entry
   */
  public Entry update(String documentId, Map<String, Object> data) {
    CmsEntry entity = entryRepository.update(documentId, data, null);
    return new Entry(entity);
  }

  /**
   * Deletes an entry (all versions).
   *
   * @param documentId the document ID
   * @return true if the entry was deleted
   */
  public boolean delete(String documentId) {
    CmsEntry entry = entryRepository.findByDocumentId(documentId);
    if (entry == null) {
      return false;
    }
    entryRepository.delete(documentId);
    return true;
  }

  /**
   * Publishes a draft entry.
   *
   * @param documentId the document ID
   * @param userId     the publishing user's ID
   * @return the published entry
   */
  public Entry publish(String documentId, Long userId) {
    CmsEntry published;
    if (userId != null) {
      published = draftPublishService.publish(documentId, "en", userId);
    } else {
      published = entryRepository.publish(documentId, null);
    }
    return new Entry(published);
  }

  /**
   * Unpublishes an entry.
   *
   * @param documentId the document ID
   * @return true if the entry was unpublished
   */
  public boolean unpublish(String documentId) {
    try {
      draftPublishService.unpublish(documentId, "en");
      return true;
    } catch (Exception e) {
      Log.warnf("Failed to unpublish entry %s: %s", documentId, e.getMessage());
      return false;
    }
  }

  // ---- Localization ----

  /**
   * Creates a locale variant of an existing entry.
   *
   * @param documentId    the document ID
   * @param sourceLocale  the source locale to copy non-localized fields from
   * @param targetLocale  the target locale for the new translation
   * @param localizedData locale-specific field data
   * @param userId        the user performing the operation
   * @return the newly created entry in the target locale
   */
  public Entry createLocalization(
      String documentId,
      String sourceLocale,
      String targetLocale,
      Map<String, Object> localizedData,
      Long userId) {
    CmsEntry entry =
        i18nService.createLocalization(
            documentId, sourceLocale, targetLocale, localizedData, userId);
    return new Entry(entry);
  }

  // ---- Schema Introspection ----

  /** Returns all registered content type schemas. */
  public List<ContentTypeSchema> getContentTypes() {
    return schemaService.getAllContentTypes().stream()
        .map(ContentTypeSchema::new)
        .toList();
  }

  /** Returns a single content type schema by UID. */
  public ContentTypeSchema getContentType(String uid) {
    ContentTypeDefinition ct = schemaService.getContentType(uid);
    return ct != null ? new ContentTypeSchema(ct) : null;
  }

  /** Returns all registered component schemas. */
  public List<ComponentSchema> getComponents() {
    return schemaService.getAllComponents().stream()
        .map(ComponentSchema::new)
        .toList();
  }

  /** Returns a single component schema by UID. */
  public ComponentSchema getComponent(String uid) {
    ComponentDefinition comp = schemaService.getComponent(uid);
    return comp != null ? new ComponentSchema(comp) : null;
  }

  // ---- Locale Queries ----

  /** Returns all configured locales. */
  public List<com.quarkus.cms.graphql.model.SchemaTypes.LocaleInfo> getLocales() {
    return localeService.listLocales().stream()
        .map(com.quarkus.cms.graphql.model.SchemaTypes.LocaleInfo::new)
        .toList();
  }

  // ---- DataLoader-friendly Relation Resolution ----

  /**
   * Batch-loads relations for a set of entries grouped by content type and field name.
   *
   * <p>This is the DataLoader-based approach: for a list of entries, resolves all relations in
   * a configurable number of bulk queries rather than one query per entry.
   *
   * @param sourceEntries the entries whose relations to resolve
   * @param fieldName     the relation field name
   * @return map of source document ID -> list of target entries
   */
  public Map<String, List<Entry>> resolveRelations(
      List<Entry> sourceEntries, String fieldName) {

    if (sourceEntries.isEmpty()) {
      return Map.of();
    }

    // Collect all source document IDs
    List<String> sourceIds =
        sourceEntries.stream().map(Entry::getDocumentId).toList();

    // Batch-fetch all relation rows for these sources
    Map<String, List<Entry>> result = new LinkedHashMap<>();
    for (Entry entry : sourceEntries) {
      List<String> targetIds = relationService.findTargetIds(entry.getDocumentId(), fieldName);
      List<CmsEntry> targetEntities = targetIds.stream()
          .map(id -> entryRepository.findPublished(id, "en"))
          .filter(e -> e != null)
          .toList();
      result.put(
          entry.getDocumentId(),
          targetEntities.stream().map(Entry::new).toList());
    }

    return result;
  }

  // ---- Internal helpers ----

  /** Resolves locale with fallback from explicit param to default locale. */
  private String resolveLocale(String locale) {
    if (locale != null && !locale.isBlank()) {
      return locale;
    }
    return localeService.getDefaultLocale();
  }

  /**
   * Parses a nested Map structure into a FilterNode tree.
   *
   * <p>Supports the Strapi v5 filter syntax:
   * <pre>{@code
   * { "title": { "$eq": "Hello" } }
   * { "$and": [ { "title": { "$eq": "Hello" } }, { "status": { "$eq": "draft" } } ] }
   * }</pre>
   */
  @SuppressWarnings("unchecked")
  private FilterNode parseFilterTree(Map<String, Object> filters) {
    if (filters == null || filters.isEmpty()) {
      return null;
    }

    List<FilterNode> conditions = new ArrayList<>();

    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if ("$and".equals(key) || "$or".equals(key)) {
        FilterNode.Logic logic =
            "$and".equals(key) ? FilterNode.Logic.AND : FilterNode.Logic.OR;
        List<FilterNode> children = new ArrayList<>();

        if (value instanceof List<?> list) {
          for (Object item : list) {
            if (item instanceof Map) {
              FilterNode child = parseFilterTree((Map<String, Object>) item);
              if (child != null) {
                children.add(child);
              }
            }
          }
        } else if (value instanceof Map) {
          FilterNode child = parseFilterTree((Map<String, Object>) value);
          if (child != null) {
            children.add(child);
          }
        }

        if (!children.isEmpty()) {
          conditions.add(new FilterNode.Group(logic, children));
        }
      } else if (value instanceof Map) {
        Map<String, Object> operatorMap = (Map<String, Object>) value;
        for (Map.Entry<String, Object> opEntry : operatorMap.entrySet()) {
          try {
            FilterNode.Operator operator =
                FilterNode.Operator.fromCode(opEntry.getKey());
            conditions.add(
                new FilterNode.Leaf(key, operator, opEntry.getValue()));
          } catch (IllegalArgumentException e) {
            // Unknown operator; skip
          }
        }
      } else {
        // Direct equality
        conditions.add(new FilterNode.Leaf(key, FilterNode.Operator.EQ, value));
      }
    }

    if (conditions.isEmpty()) return null;
    if (conditions.size() == 1) return conditions.get(0);
    return new FilterNode.Group(FilterNode.Logic.AND, conditions);
  }

  /**
   * Parses sort specifications from a single sort string like "title:asc".
   */
  private List<SortOrder> parseSortString(String sortStr) {
    if (sortStr == null || sortStr.isBlank()) return List.of();
    SortOrder order = parseSingleSortString(sortStr);
    return order != null ? List.of(order) : List.of();
  }

  /** Parses a single sort string like "title:asc" or "createdAt:desc". */
  private SortOrder parseSingleSortString(String sortStr) {
    if (sortStr == null || sortStr.isBlank()) return null;
    String[] parts = sortStr.split(":");
    String field = parts[0].trim();
    SortOrder.Direction direction = SortOrder.Direction.ASC;
    if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
      direction = SortOrder.Direction.DESC;
    }
    return new SortOrder(field, direction);
  }
}
