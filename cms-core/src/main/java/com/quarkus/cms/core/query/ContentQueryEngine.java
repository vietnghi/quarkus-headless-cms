package com.quarkus.cms.core.query;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.model.RelationType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enhanced query engine that extends {@link CmsQueryBuilder} with relation population support.
 *
 * <p>Resolves relation fields referenced in the {@code populate} parameter of a {@link CmsQuery}
 * by looking up the actual target documents from the {@code cms_entries} table via the
 * {@code cms_relations} join table.
 *
 * <p>Supports deep population (nested), population of all relations ({@code populate=*}), and
 * merged results where each entry's {@code data} map includes resolved relation documents.
 *
 * <p>Population depth is configurable via {@link PopulateNode#getDepthOverride()} — each branch
 * of the populate tree can set its own depth limit. Cycle detection prevents infinite recursion
 * from circular relation chains.
 *
 * <h3>Population Result Format</h3>
 * <p>Each populated relation field is injected into the {@code data} map under the relation
 * field name. The value is either:
 * <ul>
 *   <li>A single populated {@code Map<String, Object>} (for one-to-one / morph-one relations)
 *   <li>A {@code List<Map<String, Object>>} (for one-to-many / many-to-many relations)
 *   <li>The raw document ID if the entry is not published or not found (fallback)
 * </ul>
 */
@ApplicationScoped
public class ContentQueryEngine {

    @Inject
    SchemaStorageService schemaStorageService;

    /** Default maximum depth for relation population to prevent infinite recursion. */
    static final int DEFAULT_MAX_POPULATE_DEPTH = 5;

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Executes a query and populates relation fields as specified.
     *
     * @param query the query parameters (filters, sort, pagination, populate)
     * @return list of entries with relation data populated in their data maps
     */
    public List<CmsEntry> list(CmsQuery query) {
        List<CmsEntry> entries = CmsQueryBuilder.list(query);
        if (entries.isEmpty() || query.getPopulate() == null || query.getPopulate().isEmpty()) {
            return entries;
        }
        return populateEntries(entries, query);
    }

    /** Counts entries matching the query (same as CmsQueryBuilder). */
    public long count(CmsQuery query) {
        return CmsQueryBuilder.count(query);
    }

    /**
     * Populates a single entry with relation data for the specified populate spec.
     *
     * @param entry the entry to populate
     * @param contentTypeDef the schema definition for the entry's content type
     * @param populateSpec the list of populate nodes
     * @return the entry with populated relation data in its data map
     */
    public CmsEntry populateEntry(CmsEntry entry, ContentTypeDefinition contentTypeDef,
                                   List<PopulateNode> populateSpec) {
        if (populateSpec == null || populateSpec.isEmpty()) {
            return entry;
        }
        resolvePopulations(entry, contentTypeDef, populateSpec, new HashMap<>(),
                new HashSet<>(), 0, resolveEffectiveDepth(populateSpec));
        return entry;
    }

    /**
     * Returns resolved relation targets for a specific field on an entry.
     * Used by the dedicated relations API endpoint.
     *
     * @param entry the source entry
     * @param contentTypeDef the schema definition
     * @param fieldName the relation field name
     * @param locale optional locale filter
     * @param status optional status filter
     * @return list of target entries, or empty list if no relation field found
     */
    public List<CmsEntry> resolveRelationField(CmsEntry entry, ContentTypeDefinition contentTypeDef,
                                                String fieldName, String locale, String status) {
        RelationDefinition relDef = findRelation(contentTypeDef, fieldName);
        if (relDef == null) {
            return List.of();
        }

        // Find target document IDs from cms_relations
        List<CmsRelation> relations = CmsRelation.findRelations(entry.documentId, fieldName);
        if (relations.isEmpty()) {
            return List.of();
        }

        List<String> targetIds = relations.stream()
                .map(r -> r.targetDocumentId)
                .distinct()
                .toList();

        return loadTargets(targetIds, new HashMap<>(), locale, status);
    }

    // ========================================================================
    // Internal: Population
    // ========================================================================

    /**
     * Resolves the effective max depth from the populate spec. Each node can have
     * its own depth override; we use the maximum across all top-level nodes, or the
     * default if none have overrides.
     */
    private int resolveEffectiveDepth(List<PopulateNode> populateSpec) {
        int maxDepth = DEFAULT_MAX_POPULATE_DEPTH;
        for (PopulateNode node : populateSpec) {
            if (node.getDepthOverride() != null && node.getDepthOverride() > maxDepth) {
                maxDepth = node.getDepthOverride();
            }
        }
        return maxDepth;
    }

    private List<CmsEntry> populateEntries(List<CmsEntry> entries, CmsQuery query) {
        if (query.getContentType() == null) {
            return entries;
        }

        ContentTypeDefinition ct = schemaStorageService.getContentType(query.getContentType());
        if (ct == null) {
            Log.warnf("Cannot populate: unknown content type %s", query.getContentType());
            return entries;
        }

        List<PopulateNode> populateSpec = resolvePopulateSpec(ct, query.getPopulate());

        // Map of documentId → entry for bulk resolution
        Map<String, CmsEntry> entryMap = new HashMap<>();
        for (CmsEntry entry : entries) {
            entryMap.put(entry.documentId, entry);
        }

        int effectiveDepth = resolveEffectiveDepth(populateSpec);

        // Resolve each entry's populations
        for (CmsEntry entry : entries) {
            resolvePopulations(entry, ct, populateSpec, entryMap,
                    new HashSet<>(), 0, effectiveDepth);
        }

        return entries;
    }

    /**
     * Resolves the populate spec against the content type's relations.
     * If populate=* (populateAll), all relation fields are populated.
     */
    private List<PopulateNode> resolvePopulateSpec(ContentTypeDefinition ct,
                                                    List<PopulateNode> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }

        // Check if populate=* is set
        boolean all = requested.stream().anyMatch(PopulateNode::isPopulateAll);
        if (all) {
            // Populate all relation fields
            List<PopulateNode> allFields = new ArrayList<>();
            for (RelationDefinition rel : ct.getRelations()) {
                PopulateNode node = new PopulateNode(rel.getFieldName());
                // Propagate fields filter if specified on the wildcard node
                PopulateNode wildcard = requested.stream()
                        .filter(PopulateNode::isPopulateAll)
                        .findFirst().orElse(null);
                if (wildcard != null && wildcard.getFields() != null && !wildcard.getFields().isEmpty()) {
                    node.setFields(new HashSet<>(wildcard.getFields()));
                }
                if (wildcard != null && wildcard.getDepthOverride() != null) {
                    node.setDepthOverride(wildcard.getDepthOverride());
                }
                allFields.add(node);
            }
            return allFields;
        }

        return requested;
    }

    /**
     * Resolves all populations for a single entry by loading relation data from the DB.
     * Detects cycles via the {@code visited} set and enforces maximum depth to prevent
     * infinite recursion from circular relation chains.
     */
    @SuppressWarnings("unchecked")
    private void resolvePopulations(CmsEntry entry, ContentTypeDefinition ct,
                                     List<PopulateNode> populateSpec,
                                     Map<String, CmsEntry> entryMap,
                                     Set<String> visited, int depth, int maxDepth) {
        if (entry == null || populateSpec == null || populateSpec.isEmpty()) {
            return;
        }

        // Depth limit: stop recursion at max depth
        if (depth >= maxDepth) {
            Log.debugf("Population depth limit (%d) reached for entry %s", maxDepth, entry.documentId);
            return;
        }

        // Cycle detection: if we've already visited this document in this path, skip
        if (visited.contains(entry.documentId)) {
            Log.debugf("Cycle detected in population for entry %s, skipping", entry.documentId);
            return;
        }
        visited.add(entry.documentId);

        try {
            if (entry.data == null) {
                entry.data = new HashMap<>();
            }

            for (PopulateNode node : populateSpec) {
                String fieldName = node.getFieldName();
                if (fieldName == null) continue;

                // Find the relation definition for this field
                RelationDefinition relDef = findRelation(ct, fieldName);

                if (relDef == null) {
                    // Try field-based relation (IDs stored directly in data)
                    FieldDefinition fieldDef = ct.getField(fieldName);
                    if (fieldDef != null && fieldDef.getType() == FieldType.RELATION) {
                        Object rawValue = entry.data.get(fieldName);
                        List<String> targetIds = extractTargetIds(rawValue);
                        if (!targetIds.isEmpty()) {
                            List<CmsEntry> targets = loadTargets(targetIds, entryMap, null, null);
                            if (!targets.isEmpty()) {
                                // Apply nested population if specified
                                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                                    String targetType = targets.get(0).contentType;
                                    ContentTypeDefinition targetCt = schemaStorageService.getContentType(targetType);
                                    if (targetCt != null) {
                                        int childDepth = node.effectiveDepth(maxDepth);
                                        for (CmsEntry target : targets) {
                                            resolvePopulations(target, targetCt, node.getChildren(),
                                                    entryMap, visited, depth + 1, childDepth);
                                        }
                                    }
                                }
                                populateField(entry, fieldName, targets, null, node);
                            }
                        }
                    }
                    continue;
                }

                // Find target document IDs from cms_relations
                List<CmsRelation> relations = CmsRelation.findRelations(entry.documentId, fieldName);
                if (relations.isEmpty()) {
                    continue;
                }

                // Collect target IDs
                List<String> targetIds = relations.stream()
                        .map(r -> r.targetDocumentId)
                        .distinct()
                        .toList();

                // Load target entries — respect locale and status if specified
                List<CmsEntry> targets = loadTargets(targetIds, entryMap, null, null);

                // Handle nested population (with depth tracking)
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    // Resolve target type from the first relation (or morph-specific)
                    String targetType = resolveTargetType(relDef, relations);
                    ContentTypeDefinition targetCt = schemaStorageService.getContentType(targetType);
                    if (targetCt != null) {
                        int childDepth = node.effectiveDepth(maxDepth);
                        for (CmsEntry target : targets) {
                            resolvePopulations(target, targetCt, node.getChildren(),
                                    entryMap, visited, depth + 1, childDepth);
                        }
                    } else if (relDef.isMorph()) {
                        // For morph relations, resolve each target by its own content type
                        for (CmsEntry target : targets) {
                            ContentTypeDefinition morphCt = schemaStorageService.getContentType(target.contentType);
                            if (morphCt != null) {
                                int childDepth = node.effectiveDepth(maxDepth);
                                resolvePopulations(target, morphCt, node.getChildren(),
                                        entryMap, visited, depth + 1, childDepth);
                            }
                        }
                    }
                }

                populateField(entry, fieldName, targets, relDef, node);
            }
        } finally {
            visited.remove(entry.documentId);
        }
    }

    /**
     * Resolves the common target content type for a relation.
     * For non-morph relations this is the target defined in the schema.
     * For morph relations, use the first relation's targetType.
     */
    private String resolveTargetType(RelationDefinition relDef, List<CmsRelation> relations) {
        if (!relDef.isMorph()) {
            return relDef.getTarget();
        }
        if (!relations.isEmpty()) {
            return relations.get(0).targetType;
        }
        return null;
    }

    /**
     * Populates a single field on the entry based on the relation cardinality.
     * Applies field filtering if the PopulateNode specifies fields.
     */
    private void populateField(CmsEntry entry, String fieldName,
                                List<CmsEntry> targets,
                                RelationDefinition relDef,
                                PopulateNode node) {
        List<Map<String, Object>> populated = targets.stream()
                .map(t -> entryToPopulatedMap(t, node.getFields()))
                .toList();

        if (relDef != null && isSingleTarget(relDef)) {
            // One-to-one, morph-one: return single object
            entry.data.put(fieldName, populated.isEmpty() ? null : populated.get(0));
        } else {
            // One-to-many, many-to-many, morph-to-many: return array
            entry.data.put(fieldName, populated);
        }
    }

    /**
     * Loads target entries by document ID, checking the in-memory map first.
     * Supports optional locale and status filtering.
     */
    private List<CmsEntry> loadTargets(List<String> targetIds,
                                        Map<String, CmsEntry> entryMap,
                                        String locale, String status) {
        if (targetIds.isEmpty()) return List.of();

        List<CmsEntry> results = new ArrayList<>();
        List<String> missingIds = new ArrayList<>();

        for (String id : targetIds) {
            if (entryMap != null) {
                CmsEntry cached = entryMap.get(id);
                if (cached != null) {
                    results.add(cached);
                    continue;
                }
            }
            missingIds.add(id);
        }

        // Load any uncached targets from the database
        if (!missingIds.isEmpty()) {
            String queryStr = "documentId in ?1";
            List<Object> params = new ArrayList<>();
            params.add(missingIds);

            if (status != null) {
                queryStr += " and status = ?" + (params.size() + 1);
                params.add(status);
            }
            if (locale != null) {
                queryStr += " and locale = ?" + (params.size() + 1);
                params.add(locale);
            }

            @SuppressWarnings("unchecked")
            List<CmsEntry> dbEntries = CmsEntry.find(queryStr, params.toArray()).list();
            results.addAll(dbEntries);

            // Cache them for subsequent lookups
            if (entryMap != null) {
                for (CmsEntry e : dbEntries) {
                    entryMap.put(e.documentId, e);
                }
            }
        }

        return results;
    }

    /**
     * Converts a CmsEntry to a populated map that can be injected into the parent's data.
     * Respects field-level filtering if {@code fields} is non-empty.
     */
    private Map<String, Object> entryToPopulatedMap(CmsEntry entry, Set<String> fields) {
        Map<String, Object> result = new HashMap<>();
        result.put("documentId", entry.documentId);
        result.put("contentType", entry.contentType);
        result.put("locale", entry.locale);
        result.put("status", entry.status);
        result.put("versionNumber", entry.versionNumber);

        // Always include id
        if (entry.id != null) {
            result.put("id", entry.id);
        }

        Map<String, Object> data = entry.data != null ? entry.data : Collections.emptyMap();

        if (fields != null && !fields.isEmpty()) {
            // Only include specified fields from data
            for (String field : fields) {
                Object val = data.get(field);
                if (val != null) {
                    result.put(field, val);
                }
            }
        } else {
            // Include all data fields
            result.putAll(data);
        }

        return result;
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Finds a relation definition by field name in a content type definition.
     */
    private RelationDefinition findRelation(ContentTypeDefinition ct, String fieldName) {
        if (ct == null || ct.getRelations() == null) return null;
        return ct.getRelations().stream()
                .filter(r -> r.getFieldName().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns true if the relation type maps to a single target.
     */
    private boolean isSingleTarget(RelationDefinition relDef) {
        if (relDef == null) return false;
        return switch (relDef.getType()) {
            case ONE_TO_ONE, MORPH_TO_ONE -> true;
            case ONE_TO_MANY, MANY_TO_MANY, MANY_TO_ONE, MORPH_TO_MANY -> false;
        };
    }

    /**
     * Extracts target document ID(s) from a raw field value.
     * Supports both single ID strings and lists of IDs.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractTargetIds(Object rawValue) {
        if (rawValue == null) return List.of();
        if (rawValue instanceof String s) {
            return List.of(s);
        }
        if (rawValue instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s) {
                    ids.add(s);
                } else if (item instanceof Map<?, ?> m) {
                    Object docId = m.get("documentId");
                    if (docId instanceof String s) {
                        ids.add(s);
                    }
                }
            }
            return ids;
        }
        if (rawValue instanceof Map<?, ?> m) {
            Object docId = m.get("documentId");
            if (docId instanceof String s) {
                return List.of(s);
            }
        }
        return List.of();
    }

    // ========================================================================
    // Legacy: Static methods for backward compatibility
    // ========================================================================

    /**
     * Populates a list of entries for a given content type, populating all relation fields.
     */
    public static List<CmsEntry> populateAllRelations(
            List<CmsEntry> entries, ContentTypeDefinition ct) {
        if (entries.isEmpty() || ct == null) return entries;
        // Create a temporary engine — in production use the injected bean
        ContentQueryEngine engine = new ContentQueryEngine();
        List<PopulateNode> allRelations = ct.getRelations().stream()
                .map(r -> new PopulateNode(r.getFieldName()))
                .toList();

        Map<String, CmsEntry> entryMap = new HashMap<>();
        for (CmsEntry e : entries) {
            entryMap.put(e.documentId, e);
        }
        for (CmsEntry entry : entries) {
            engine.resolvePopulations(entry, ct, allRelations, entryMap,
                    new java.util.HashSet<>(), 0, DEFAULT_MAX_POPULATE_DEPTH);
        }
        return entries;
    }

    // ========================================================================
    // Relations API support
    // ========================================================================

    /**
     * Populates an entry's single relation field, returning a properly formatted response.
     * Used by {@code RelationsResource} for {@code GET /api/{ct}/{docId}/relations/{field}}.
     *
     * @param entry the source entry
     * @param ct the content type definition
     * @param fieldName the relation field to resolve
     * @param locale optional locale filter
     * @param status optional status filter
     * @return list of populated maps (one per target entry), or empty list
     */
    public List<Map<String, Object>> resolveRelationFieldAsMaps(
            CmsEntry entry, ContentTypeDefinition ct,
            String fieldName, String locale, String status) {

        RelationDefinition relDef = findRelation(ct, fieldName);
        if (relDef == null) return List.of();

        List<CmsRelation> relations = CmsRelation.findRelations(entry.documentId, fieldName);
        if (relations.isEmpty()) return List.of();

        List<String> targetIds = relations.stream()
                .map(r -> r.targetDocumentId)
                .distinct()
                .toList();

        List<CmsEntry> targets = loadTargets(targetIds, new HashMap<>(), locale, status);
        return targets.stream()
                .map(t -> entryToPopulatedMap(t, null))
                .toList();
    }

    /**
     * Returns the list of relation definitions for a content type.
     */
    public List<RelationDefinition> getRelationDefinitions(String contentType) {
        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null || ct.getRelations() == null) return List.of();
        return ct.getRelations();
    }

    /**
     * Returns a relation definition by field name.
     */
    public RelationDefinition getRelationDefinition(String contentType, String fieldName) {
        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) return null;
        return findRelation(ct, fieldName);
    }
}
