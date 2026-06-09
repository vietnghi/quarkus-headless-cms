package com.quarkus.cms.admin.api.service;

import com.quarkus.cms.admin.api.resource.SearchResponse;
import com.quarkus.cms.admin.api.resource.SearchResultItem;
import com.quarkus.cms.core.domain.JsonMapConverter;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for performing global search across all content entries and media files.
 *
 * <p>Uses native SQL queries with LIKE-based filtering on the data column (for entries)
 * and the name column (for media files). Results are loaded via native SQL and
 * converted to DTOs directly, avoiding JPA entity hydration issues that can occur
 * with certain database dialects (e.g. SQLite community dialect).
 */
@ApplicationScoped
public class SearchService {

    public static final int MAX_PAGE_SIZE = 20;
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int DEFAULT_PAGE = 0;

    private static final List<String> TITLE_FIELD_NAMES = List.of("title", "name", "heading", "headline",
            "label", "subject", "caption");

    private static final Set<FieldType> SEARCHABLE_FIELD_TYPES = Set.of(
            FieldType.STRING, FieldType.TEXT, FieldType.RICHTEXT,
            FieldType.EMAIL, FieldType.UID, FieldType.ENUMERATION);

    @Inject
    SchemaStorageService schemaStorageService;

    @PersistenceContext
    EntityManager entityManager;

    private static final JsonMapConverter JSON_CONVERTER = new JsonMapConverter();

    public SearchResponse search(String query, String contentType, String locale,
                                  int page, int pageSize) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(List.of(), 0);
        }
        int resolvedPage = Math.max(page, DEFAULT_PAGE);
        int resolvedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        String searchTerm = query.toLowerCase().trim();

        List<SearchResultItem> entryResults = searchEntries(searchTerm, contentType, locale,
                resolvedPage, resolvedPageSize);
        long totalEntryCount = countEntries(searchTerm, contentType, locale);

        List<SearchResultItem> mediaResults = searchMedia(searchTerm, contentType, locale,
                resolvedPage, resolvedPageSize);
        long totalMediaCount = countMedia(searchTerm, contentType, locale);

        List<SearchResultItem> combined = new ArrayList<>();
        combined.addAll(entryResults);
        combined.addAll(mediaResults);

        if (combined.size() > resolvedPageSize) {
            combined = combined.subList(0, resolvedPageSize);
        }

        return new SearchResponse(combined, (int) (totalEntryCount + totalMediaCount));
    }

    // ---- Entry search (native SQL with LIKE on data column) ---- //

    @SuppressWarnings("unchecked")
    List<SearchResultItem> searchEntries(String searchTerm, String contentType,
                                          String locale, int page, int pageSize) {
        List<Object[]> rows = queryEntryRows(searchTerm, contentType, locale, page, pageSize);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<ContentTypeDefinition> allTypes = schemaStorageService.getAllContentTypes();
        Map<String, ContentTypeDefinition> typeIndex = allTypes.stream()
                .collect(Collectors.toMap(ContentTypeDefinition::getUid, t -> t, (a, b) -> a));

        List<SearchResultItem> results = new ArrayList<>();
        for (Object[] row : rows) {
            // Row format: [id, document_id, content_type, locale, status, data, created_at, updated_at]
            Long entryId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String documentId = (String) row[1];
            String ctUid = (String) row[2];
            String dataJson = (String) row[5];

            Map<String, Object> data;
            try {
                data = JSON_CONVERTER.convertToEntityAttribute(dataJson);
            } catch (Exception e) {
                Log.warnf("Failed to parse entry data JSON documentId=%s: %s", documentId, e.getMessage());
                data = Map.of();
            }

            ContentTypeDefinition ctDef = typeIndex.get(ctUid);
            String title = extractTitle(data, ctDef);
            String excerpt = extractExcerpt(data, ctDef, searchTerm);

            results.add(SearchResultItem.forEntry(ctUid, entryId, documentId, title, excerpt));
        }
        return results;
    }

    long countEntries(String searchTerm, String contentType, String locale) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM cms_entries WHERE LOWER(data) LIKE :pattern");
        if (contentType != null && !contentType.isBlank()) {
            sql.append(" AND content_type = :contentType");
        }
        if (locale != null && !locale.isBlank()) {
            sql.append(" AND locale = :locale");
        }

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("pattern", "%" + searchTerm + "%");
        if (contentType != null && !contentType.isBlank()) {
            q.setParameter("contentType", contentType);
        }
        if (locale != null && !locale.isBlank()) {
            q.setParameter("locale", locale);
        }

        try {
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            Log.warnf("Entry count failed: %s", e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> queryEntryRows(String searchTerm, String contentType,
                                           String locale, int page, int pageSize) {
        // Use COALESCE to handle null id (some SQLite setups don't map the id column
        // with auto-increment due to Hibernate dialect quirks). The rowid or a hash
        // of the document_id provides a stable identifier.
        StringBuilder sql = new StringBuilder(
            "SELECT COALESCE(id, 0), document_id, content_type, locale, status, data, created_at, updated_at"
            + " FROM cms_entries WHERE LOWER(data) LIKE :pattern");
        if (contentType != null && !contentType.isBlank()) {
            sql.append(" AND content_type = :contentType");
        }
        if (locale != null && !locale.isBlank()) {
            sql.append(" AND locale = :locale");
        }
        sql.append(" ORDER BY updated_at DESC");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("pattern", "%" + searchTerm + "%");
        if (contentType != null && !contentType.isBlank()) {
            q.setParameter("contentType", contentType);
        }
        if (locale != null && !locale.isBlank()) {
            q.setParameter("locale", locale);
        }
        q.setFirstResult(page * pageSize);
        q.setMaxResults(pageSize);

        try {
            return q.getResultList();
        } catch (Exception e) {
            Log.warnf("Entry search query failed: %s", e.getMessage());
            return List.of();
        }
    }

    // ---- Media search ---- //

    @SuppressWarnings("unchecked")
    List<SearchResultItem> searchMedia(String searchTerm, String contentType,
                                        String locale, int page, int pageSize) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, name, caption, mime_type, file_size, folder_id, created_at"
            + " FROM cms_files WHERE LOWER(name) LIKE :pattern");
        if (contentType != null && !contentType.isBlank()) {
            sql.append(" AND related_content_type = :contentType");
        }
        sql.append(" ORDER BY created_at DESC");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("pattern", "%" + searchTerm + "%");
        if (contentType != null && !contentType.isBlank()) {
            q.setParameter("contentType", contentType);
        }
        q.setFirstResult(page * pageSize);
        q.setMaxResults(pageSize);

        List<Object[]> rows;
        try {
            rows = q.getResultList();
        } catch (Exception e) {
            Log.warnf("Media search failed: %s", e.getMessage());
            return List.of();
        }

        List<SearchResultItem> results = new ArrayList<>();
        for (Object[] row : rows) {
            Long fileId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String name = (String) row[1];
            String caption = (String) row[2];

            String excerpt = caption != null && !caption.isBlank() ? caption : name;
            if (excerpt.length() > 150) {
                excerpt = excerpt.substring(0, 147) + "...";
            }
            results.add(SearchResultItem.forMedia(fileId, name, excerpt));
        }
        return results;
    }

    long countMedia(String searchTerm, String contentType, String locale) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM cms_files WHERE LOWER(name) LIKE :pattern");
        if (contentType != null && !contentType.isBlank()) {
            sql.append(" AND related_content_type = :contentType");
        }

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("pattern", "%" + searchTerm + "%");
        if (contentType != null && !contentType.isBlank()) {
            q.setParameter("contentType", contentType);
        }

        try {
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            Log.warnf("Media count failed: %s", e.getMessage());
            return 0;
        }
    }

    // ---- Helpers ---- //

    public static String extractTitle(Map<String, Object> data, ContentTypeDefinition ctDef) {
        if (data == null || data.isEmpty()) {
            return "(untitled)";
        }

        for (String titleField : TITLE_FIELD_NAMES) {
            Object val = data.get(titleField);
            if (val instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        if (ctDef != null) {
            for (FieldDefinition field : ctDef.getFields()) {
                if (SEARCHABLE_FIELD_TYPES.contains(field.getType())) {
                    Object val = data.get(field.getName());
                    if (val instanceof String s && !s.isBlank()) {
                        return s.length() > 200 ? s.substring(0, 197) + "..." : s;
                    }
                }
            }
        }

        for (Object val : data.values()) {
            if (val instanceof String s && !s.isBlank()) {
                return s.length() > 200 ? s.substring(0, 197) + "..." : s;
            }
        }

        return "(untitled)";
    }

    public static String extractExcerpt(Map<String, Object> data, ContentTypeDefinition ctDef,
                                  String searchTerm) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        String term = searchTerm.toLowerCase();
        List<FieldDefinition> fields = ctDef != null ? ctDef.getFields() : List.of();

        for (FieldDefinition field : fields) {
            if (field.getType() == FieldType.RICHTEXT || field.getType() == FieldType.TEXT) {
                Object val = data.get(field.getName());
                if (val instanceof String s && !s.isBlank()
                        && s.toLowerCase().contains(searchTerm)) {
                    return snippet(s, 150);
                }
            }
        }

        for (FieldDefinition field : fields) {
            if (SEARCHABLE_FIELD_TYPES.contains(field.getType())) {
                Object val = data.get(field.getName());
                if (val instanceof String s && !s.isBlank()) {
                    if (s.toLowerCase().contains(term)) {
                        return snippet(s, 150);
                    }
                }
            }
        }

        for (Object val : data.values()) {
            if (val instanceof String s && !s.isBlank()
                    && s.toLowerCase().contains(term)) {
                return snippet(s, 150);
            }
        }

        for (Object val : data.values()) {
            if (val instanceof String s && !s.isBlank()) {
                return snippet(s, 150);
            }
        }

        return "";
    }

    private static String snippet(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        int cut = text.lastIndexOf(' ', maxLen);
        if (cut > maxLen / 2) {
            return text.substring(0, cut) + "...";
        }
        return text.substring(0, maxLen - 3) + "...";
    }
}
