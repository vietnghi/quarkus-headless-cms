package com.quarkus.cms.core.entity;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.query.CmsQuery;
import com.quarkus.cms.core.query.ContentQueryEngine;
import com.quarkus.cms.core.query.PopulateNode;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.relation.RelationService;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Schema-aware entity service providing comprehensive CRUD operations on dynamic content types.
 *
 * <p>This is the primary service layer for content entity management. It integrates:
 * <ul>
 *   <li>Content-type schema validation via {@link SchemaStorageService}</li>
 *   <li>Entity lifecycle event hooks via {@link EntityEventPublisher}</li>
 *   <li>Relation population via {@link ContentQueryEngine}</li>
 *   <li>Relation management via {@link RelationService}</li>
 *   <li>Draft/publish lifecycle (versioning, published state)</li>
 *   <li>Bulk operations and version history management</li>
 * </ul>
 *
 * <p>All public methods provide both blocking and reactive ({@link Uni}) variants.
 */
@ApplicationScoped
public class EntityService {

    @Inject
    CmsEntryRepository repository;

    @Inject
    SchemaStorageService schemaStorageService;

    @Inject
    EntityEventPublisher eventPublisher;

    @Inject
    ContentQueryEngine queryEngine;

    @Inject
    RelationService relationService;

    // ========================================================================
    // Create
    // ========================================================================

    /**
     * Creates a new content entry as a draft with generated document ID.
     *
     * @param contentType the content-type UID
     * @param data        the field data map
     * @param locale      the locale code
     * @param userId      the creating user ID (nullable)
     * @return the created draft entry
     * @throws IllegalArgumentException if content type is unknown or data fails validation
     */
    @Transactional
    public CmsEntry create(String contentType, Map<String, Object> data,
                            String locale, Long userId) {
        ContentTypeDefinition ct = validateContentType(contentType);

        // Validate data against schema fields
        validateData(ct, data);

        // Enforce single-type uniqueness
        if (ct.isSingleType()) {
            CmsEntry existing = findSingleTypeEntry(contentType, locale);
            if (existing != null) {
                throw new IllegalStateException(
                        "Single type '" + contentType + "' already has an entry for locale '" + locale + "'");
            }
        }

        // Fire BEFORE hook
        eventPublisher.fireBeforeCreate(contentType, data, locale, userId);

        // Create the entry
        String resolvedLocale = locale != null ? locale : "en";
        CmsEntry entry = new CmsEntry();
        entry.documentId = UUID.randomUUID().toString();
        entry.contentType = contentType;
        entry.locale = resolvedLocale;
        entry.status = "draft";
        entry.versionNumber = 0;
        entry.data = data != null ? new HashMap<>(data) : new HashMap<>();
        entry.createdById = userId;
        entry.updatedById = userId;
        entry.persist();

        // Process relation fields from data payload
        processRelationFields(ct, entry.documentId, entry.contentType, data);

        Log.debugf("EntityService: created entry %s (type=%s, locale=%s)",
                entry.documentId, contentType, resolvedLocale);

        // Fire AFTER hook
        eventPublisher.fireAfterCreate(contentType, entry, entry.data, resolvedLocale, userId);

        return entry;
    }

    /** Reactive variant of {@link #create(String, Map, String, Long)}. */
    public Uni<CmsEntry> createAsync(String contentType, Map<String, Object> data,
                                      String locale, Long userId) {
        return Uni.createFrom().item(() -> create(contentType, data, locale, userId));
    }

    // ========================================================================
    // Bulk Create
    // ========================================================================

    /**
     * Creates multiple entries in a single transaction.
     *
     * @param contentType the content-type UID
     * @param entries     list of field-data maps
     * @param locale      the locale code
     * @param userId      the creating user ID
     * @return list of successfully created entries
     */
    @Transactional
    public List<CmsEntry> bulkCreate(String contentType, List<Map<String, Object>> entries,
                                      String locale, Long userId) {
        List<CmsEntry> results = new ArrayList<>();
        for (Map<String, Object> data : entries) {
            results.add(create(contentType, data, locale, userId));
        }
        Log.debugf("EntityService: bulk-created %d entries (type=%s)", results.size(), contentType);
        return results;
    }

    /** Reactive variant of bulkCreate. */
    public Uni<List<CmsEntry>> bulkCreateAsync(String contentType, List<Map<String, Object>> entries,
                                                String locale, Long userId) {
        return Uni.createFrom().item(() -> bulkCreate(contentType, entries, locale, userId));
    }

    // ========================================================================
    // Read / Find
    // ========================================================================

    /**
     * Finds a single entry by document ID, trying draft first then published.
     *
     * @param documentId the document UUID
     * @param locale     the locale code
     * @return the entry, or {@code null} if not found
     */
    public CmsEntry findById(String documentId, String locale) {
        String loc = locale != null ? locale : "en";

        CmsEntry entry = repository.findDraft(documentId, loc);
        if (entry == null) {
            entry = repository.findPublished(documentId, loc);
        }

        String resolvedContentType = resolveContentType(documentId, entry);
        eventPublisher.fireBeforeFindOne(resolvedContentType, documentId, loc);

        eventPublisher.fireAfterFindOne(
                resolvedContentType,
                documentId,
                entry != null ? entry.data : null,
                loc,
                entry);

        return entry;
    }

    /** Finds a specific status/locale entry by document ID. */
    public CmsEntry findById(String documentId, String status, String locale) {
        String loc = locale != null ? locale : "en";
        String st = status != null ? status : "published";

        CmsEntry entry = CmsEntry.findByDocumentId(documentId, st, loc);

        String resolvedContentType = resolveContentType(documentId, entry);
        eventPublisher.fireBeforeFindOne(resolvedContentType, documentId, loc);

        eventPublisher.fireAfterFindOne(
                resolvedContentType,
                documentId,
                entry != null ? entry.data : null,
                loc,
                entry);

        return entry;
    }

    /** Reactive variant of findById. */
    public Uni<CmsEntry> findByIdAsync(String documentId, String locale) {
        return Uni.createFrom().item(() -> findById(documentId, locale));
    }

    /**
     * Queries entries with filtering, sorting, pagination, and relation population.
     *
     * @param query the query parameters
     * @return list of entries with populated relations as requested
     */
    public List<CmsEntry> findMany(CmsQuery query) {
        eventPublisher.fireBeforeFindMany(query.getContentType());

        List<CmsEntry> results = queryEngine.list(query);

        eventPublisher.fireAfterFindMany(query.getContentType());

        return results;
    }

    /** Reactive variant of findMany. */
    public Uni<List<CmsEntry>> findManyAsync(CmsQuery query) {
        return Uni.createFrom().item(() -> findMany(query));
    }

    /** Counts entries matching the given query. */
    public long count(CmsQuery query) {
        return queryEngine.count(query);
    }

    /**
     * Returns a query result with pagination metadata.
     *
     * @param query the query
     * @return map with "results" (list of entries) and "pagination" metadata
     */
    public Map<String, Object> findManyWithMeta(CmsQuery query) {
        List<CmsEntry> results = findMany(query);
        long total = count(query);

        Map<String, Object> meta = new HashMap<>();
        meta.put("results", results);
        meta.put("pagination", Map.of(
                "page", query.getPage(),
                "pageSize", query.getPageSize(),
                "pageCount", (int) Math.ceil((double) total / query.getPageSize()),
                "total", total
        ));

        return meta;
    }

    // ========================================================================
    // Update
    // ========================================================================

    /**
     * Updates a draft entry's data. If no draft exists but a published version does,
     * creates a new draft from the published data first.
     *
     * @param documentId the document ID
     * @param data       the updated field data (merged into existing)
     * @param locale     the locale code
     * @param userId     the updating user ID (nullable)
     * @return the updated draft entry
     * @throws IllegalStateException if no entry exists for this documentId
     */
    @Transactional
    public CmsEntry update(String documentId, Map<String, Object> data,
                            String locale, Long userId) {
        String loc = locale != null ? locale : "en";

        // Find existing to get content type
        CmsEntry existing = findById(documentId, loc);
        if (existing == null) {
            throw new IllegalStateException(
                    "No entry found for documentId=" + documentId + " locale=" + loc);
        }

        String contentType = existing.contentType;
        ContentTypeDefinition ct = validateContentType(contentType);

        // Validate data against schema fields
        validateData(ct, data);

        // Fire BEFORE hook
        eventPublisher.fireBeforeUpdate(contentType, documentId, data, loc, userId);

        // Perform update via repository (handles draft creation from published)
        CmsEntry entry;
        try {
            entry = repository.update(documentId, data, userId, loc);
        } catch (IllegalArgumentException e) {
            // If no draft exists but published does, the repository handles it
            // If still fails, attempt direct update on draft
            CmsEntry draft = repository.findDraft(documentId, loc);
            if (draft != null) {
                if (data != null) {
                    draft.data.putAll(data);
                }
                draft.updatedById = userId;
                draft.persist();
                entry = draft;
            } else {
                CmsEntry published = repository.findPublished(documentId, loc);
                if (published != null) {
                    // Create new draft from published data
                    entry = new CmsEntry();
                    entry.documentId = documentId;
                    entry.contentType = published.contentType;
                    entry.locale = loc;
                    entry.status = "draft";
                    entry.versionNumber = 0;
                    entry.data = new HashMap<>(published.data);
                    entry.createdById = published.createdById;
                    if (data != null) {
                        entry.data.putAll(data);
                    }
                    entry.updatedById = userId;
                    entry.persist();
                } else {
                    throw new IllegalStateException(
                            "No entry found to update for documentId=" + documentId + " locale=" + loc);
                }
            }
        }

        // Update relations from data payload
        processRelationFields(ct, documentId, contentType, data);

        // Fire AFTER hook
        eventPublisher.fireAfterUpdate(contentType, entry, data, loc, userId);

        Log.debugf("EntityService: updated entry %s (locale=%s)", documentId, loc);
        return entry;
    }

    /** Reactive variant of update. */
    public Uni<CmsEntry> updateAsync(String documentId, Map<String, Object> data,
                                      String locale, Long userId) {
        return Uni.createFrom().item(() -> update(documentId, data, locale, userId));
    }

    /**
     * Creates or updates a single-type entry.
     *
     * @param contentType the single-type content type UID
     * @param data        the field data
     * @param locale      the locale code
     * @param userId      the user ID
     * @return the created or updated entry
     */
    @Transactional
    public CmsEntry upsertSingleType(String contentType, Map<String, Object> data,
                                      String locale, Long userId) {
        validateContentType(contentType);
        String loc = locale != null ? locale : "en";

        CmsEntry existing = findSingleTypeEntry(contentType, loc);
        if (existing != null) {
            return update(existing.documentId, data, loc, userId);
        }
        return create(contentType, data, loc, userId);
    }

    // ========================================================================
    // Bulk Update
    // ========================================================================

    /**
     * Updates multiple entries in a single transaction.
     *
     * @param updates list of update descriptors, each containing documentId, data, and optional locale
     * @param userId  the updating user ID
     * @return list of updated entries (same order as input)
     * @throws IllegalStateException if any documentId is not found
     */
    @Transactional
    public List<CmsEntry> bulkUpdate(List<BulkUpdateItem> updates, Long userId) {
        List<CmsEntry> results = new ArrayList<>();
        for (BulkUpdateItem item : updates) {
            results.add(update(item.documentId(), item.data(), item.locale(), userId));
        }
        Log.debugf("EntityService: bulk-updated %d entries", results.size());
        return results;
    }

    /** Reactive variant of bulkUpdate. */
    public Uni<List<CmsEntry>> bulkUpdateAsync(List<BulkUpdateItem> updates, Long userId) {
        return Uni.createFrom().item(() -> bulkUpdate(updates, userId));
    }

    // ========================================================================
    // Delete
    // ========================================================================

    /**
     * Deletes all versions of a document and cascades to relations.
     *
     * @param documentId the document ID
     * @return the number of deleted rows
     */
    @Transactional
    public long delete(String documentId) {
        CmsEntry entry = repository.findByDocumentId(documentId);
        if (entry == null) return 0;

        String contentType = entry.contentType;
        String locale = entry.locale;

        // Fire BEFORE hook
        eventPublisher.fireBeforeDelete(contentType, documentId, locale, null);

        // Cascade: remove all relations
        relationService.removeAllForDocument(documentId);

        // Delete all versions
        long deleted = CmsEntry.delete("documentId", documentId);

        Log.debugf("EntityService: deleted entry %s (%d rows)", documentId, deleted);

        // Fire AFTER hook
        eventPublisher.fireAfterDelete(contentType, documentId, locale, null);

        return deleted;
    }

    /** Reactive variant of delete. */
    public Uni<Long> deleteAsync(String documentId) {
        return Uni.createFrom().item(() -> delete(documentId));
    }

    // ========================================================================
    // Bulk Delete
    // ========================================================================

    /**
     * Deletes multiple documents in a single transaction.
     *
     * @param documentIds list of document IDs to delete
     * @return map of documentId -> number of deleted rows
     */
    @Transactional
    public Map<String, Long> bulkDelete(List<String> documentIds) {
        Map<String, Long> result = new HashMap<>();
        for (String docId : documentIds) {
            result.put(docId, delete(docId));
        }
        Log.debugf("EntityService: bulk-deleted %d entries", documentIds.size());
        return result;
    }

    /** Reactive variant of bulkDelete. */
    public Uni<Map<String, Long>> bulkDeleteAsync(List<String> documentIds) {
        return Uni.createFrom().item(() -> bulkDelete(documentIds));
    }

    // ========================================================================
    // Draft/Publish Lifecycle
    // ========================================================================

    /**
     * Publishes a draft entry, creating an immutable published snapshot.
     * After publishing, the draft is deleted (no pending changes remain).
     *
     * @param documentId the document ID
     * @param locale     the locale code
     * @param userId     the publishing user ID
     * @return the published entry
     * @throws IllegalStateException if no draft exists to publish
     */
    @Transactional
    public CmsEntry publish(String documentId, String locale, Long userId) {
        String loc = locale != null ? locale : "en";
        CmsEntry draft = repository.findDraft(documentId, loc);
        if (draft == null) {
            throw new IllegalStateException(
                    "No draft found to publish for documentId=" + documentId + " locale=" + loc);
        }

        String contentType = draft.contentType;
        eventPublisher.fireBeforePublish(contentType, documentId, loc, userId);

        // Calculate next version number
        int nextVersion = 1;
        CmsEntry latest = (CmsEntry) CmsEntry.find(
                "documentId = ?1 and locale = ?2 order by versionNumber desc",
                documentId, loc).firstResult();
        if (latest != null && latest.versionNumber != null) {
            nextVersion = latest.versionNumber + 1;
        }

        // Create published copy
        CmsEntry published = new CmsEntry();
        published.documentId = draft.documentId;
        published.contentType = draft.contentType;
        published.locale = draft.locale;
        published.status = "published";
        published.versionNumber = nextVersion;
        published.data = new HashMap<>(draft.data);
        published.createdAt = draft.createdAt;
        published.createdById = draft.createdById;
        published.updatedById = userId;
        published.publishedById = userId;
        published.publishedAt = Instant.now();
        published.persist();

        // Delete the draft after publishing
        draft.delete();

        Log.infof("EntityService: published entry %s (type=%s, v%d)",
                documentId, contentType, nextVersion);

        eventPublisher.fireAfterPublish(contentType, published, loc, userId);

        return published;
    }

    /** Reactive variant of publish. */
    public Uni<CmsEntry> publishAsync(String documentId, String locale, Long userId) {
        return Uni.createFrom().item(() -> publish(documentId, locale, userId));
    }

    /**
     * Unpublishes a published entry (marks as unpublished).
     *
     * @param documentId the document ID
     * @param locale     the locale code
     * @throws IllegalStateException if no published entry exists
     */
    @Transactional
    public void unpublish(String documentId, String locale) {
        String loc = locale != null ? locale : "en";
        CmsEntry published = repository.findPublished(documentId, loc);
        if (published == null) {
            throw new IllegalStateException(
                    "No published entry to unpublish for documentId=" + documentId + " locale=" + loc);
        }

        String contentType = published.contentType;
        eventPublisher.fireBeforeUnpublish(contentType, documentId, loc, null);

        published.status = "unpublished";
        published.persist();

        Log.infof("EntityService: unpublished entry %s (locale=%s)", documentId, loc);

        eventPublisher.fireAfterUnpublish(contentType, documentId, loc, null);
    }

    /** Reactive variant of unpublish. */
    public Uni<Void> unpublishAsync(String documentId, String locale) {
        return Uni.createFrom().item(() -> {
            unpublish(documentId, locale);
            return null;
        });
    }

    /**
     * Discards (deletes) the current draft, leaving the published version intact.
     *
     * @param documentId the document ID
     * @param locale     the locale code
     */
    @Transactional
    public void discardDraft(String documentId, String locale) {
        String loc = locale != null ? locale : "en";
        CmsEntry draft = repository.findDraft(documentId, loc);
        if (draft != null) {
            eventPublisher.fireBeforeDelete(draft.contentType, documentId, loc, null);
            draft.delete();
            eventPublisher.fireAfterDelete(draft.contentType, documentId, loc, null);
            Log.debugf("EntityService: discarded draft for entry %s (locale=%s)", documentId, loc);
        }
    }

    // ========================================================================
    // Version History
    // ========================================================================

    /**
     * Returns all versions of a document across locales and statuses.
     */
    public List<CmsEntry> getVersions(String documentId, String locale) {
        String loc = locale != null ? locale : "en";
        return CmsEntry.list("documentId = ?1 and locale = ?2 order by versionNumber desc",
                documentId, loc);
    }

    /**
     * Restores a document to a specific historical version by creating a new draft
     * with the historical version's data.
     *
     * @param documentId    the document ID
     * @param versionNumber the version number to restore from
     * @param locale        the locale code
     * @param userId        the user performing the restore
     * @return the new draft entry with the restored data
     * @throws IllegalStateException if the version does not exist
     */
    @Transactional
    public CmsEntry restoreVersion(String documentId, int versionNumber,
                                    String locale, Long userId) {
        String loc = locale != null ? locale : "en";

        // Find the specific version
        CmsEntry versionEntry = (CmsEntry) CmsEntry.find(
                "documentId = ?1 and versionNumber = ?2 and locale = ?3",
                documentId, versionNumber, loc).firstResult();
        if (versionEntry == null) {
            throw new IllegalStateException(
                    "Version " + versionNumber + " not found for documentId=" + documentId + " locale=" + loc);
        }

        String contentType = versionEntry.contentType;

        // Fire BEFORE hook
        eventPublisher.fireBeforeUpdate(contentType, documentId, versionEntry.data, loc, userId);

        // Remove existing draft if any
        CmsEntry existingDraft = repository.findDraft(documentId, loc);
        if (existingDraft != null) {
            existingDraft.delete();
        }

        // Create new draft from the historical version's data
        CmsEntry newDraft = new CmsEntry();
        newDraft.documentId = documentId;
        newDraft.contentType = contentType;
        newDraft.locale = loc;
        newDraft.status = "draft";
        newDraft.versionNumber = 0;
        newDraft.data = new HashMap<>(versionEntry.data);
        newDraft.createdById = versionEntry.createdById;
        newDraft.updatedById = userId;
        newDraft.persist();

        Log.infof("EntityService: restored entry %s to version %d (locale=%s)",
                documentId, versionNumber, loc);

        eventPublisher.fireAfterUpdate(contentType, newDraft, versionEntry.data, loc, userId);

        return newDraft;
    }

    /** Reactive variant of restoreVersion. */
    public Uni<CmsEntry> restoreVersionAsync(String documentId, int versionNumber,
                                              String locale, Long userId) {
        return Uni.createFrom().item(() -> restoreVersion(documentId, versionNumber, locale, userId));
    }

    /**
     * Checks whether a document has unpublished draft changes.
     */
    public boolean hasUnpublishedChanges(String documentId, String locale) {
        String loc = locale != null ? locale : "en";
        CmsEntry draft = repository.findDraft(documentId, loc);
        CmsEntry published = repository.findPublished(documentId, loc);

        if (draft == null) return false;
        if (published == null) return true;
        return !draft.data.equals(published.data);
    }

    // ========================================================================
    // Relation Population
    // ========================================================================

    /**
     * Populates relation fields on an entry based on the content type schema.
     *
     * @param entry        the entry to populate
     * @param contentType  the content type UID
     * @param populateSpec list of relation field names to populate (null/empty = no population)
     * @return the entry with populated data
     */
    public CmsEntry populate(CmsEntry entry, String contentType, List<String> populateSpec) {
        if (populateSpec == null || populateSpec.isEmpty()) return entry;

        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) return entry;

        List<PopulateNode> nodes = populateSpec.stream()
                .map(PopulateNode::new)
                .toList();

        return queryEngine.populateEntry(entry, ct, nodes);
    }

    /**
     * Populates all relation fields on an entry.
     */
    public CmsEntry populateAll(CmsEntry entry, String contentType) {
        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null || ct.getRelations().isEmpty()) return entry;

        List<PopulateNode> allRelations = ct.getRelations().stream()
                .map(r -> new PopulateNode(r.getFieldName()))
                .toList();

        return queryEngine.populateEntry(entry, ct, allRelations);
    }

    // ========================================================================
    // Relation Management
    // ========================================================================

    /**
     * Attaches a relation from a source document to a target document.
     */
    @Transactional
    public CmsRelation attachRelation(String sourceDocumentId, String sourceType,
                                       String targetDocumentId, String targetType,
                                       String fieldName, int orderIndex) {
        return relationService.attach(sourceDocumentId, sourceType,
                targetDocumentId, targetType, fieldName, orderIndex);
    }

    /**
     * Detaches a specific relation.
     */
    @Transactional
    public void detachRelation(String sourceDocumentId, String fieldName, String targetDocumentId) {
        relationService.detach(sourceDocumentId, fieldName, targetDocumentId);
    }

    /**
     * Detaches all relations for a given source document and field.
     */
    @Transactional
    public void detachAllRelations(String sourceDocumentId, String fieldName) {
        relationService.detachAll(sourceDocumentId, fieldName);
    }

    /**
     * Finds all target document IDs for a relation field on a source document.
     */
    public List<String> findRelationTargets(String sourceDocumentId, String fieldName) {
        return relationService.findTargetIds(sourceDocumentId, fieldName);
    }

    /**
     * Reorders relations for a given field.
     */
    @Transactional
    public void reorderRelations(String sourceDocumentId, String fieldName,
                                  List<String> orderedTargetIds) {
        relationService.reorder(sourceDocumentId, fieldName, orderedTargetIds);
    }

    // ========================================================================
    // Internal: Field Validation
    // ========================================================================

    /**
     * Validates input data against the content type's field definitions.
     * Checks for unknown fields and type-compatible values where possible.
     *
     * @param ct   the content type definition
     * @param data the input data map (may be null)
     * @throws IllegalArgumentException if validation fails
     */
    private void validateData(ContentTypeDefinition ct, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        List<String> unknownFields = new ArrayList<>();
        for (String key : data.keySet()) {
            // Skip internal/system fields
            if (key.startsWith("_") || "id".equals(key) || "documentId".equals(key)
                    || "contentType".equals(key) || "locale".equals(key)
                    || "status".equals(key) || "createdAt".equals(key)
                    || "updatedAt".equals(key)) {
                continue;
            }

            FieldDefinition field = ct.getField(key);
            if (field == null) {
                // Check if it's a relation field
                boolean isRelation = ct.getRelations().stream()
                        .anyMatch(r -> r.getFieldName().equals(key));
                if (!isRelation) {
                    // Check if it's a dynamic zone or component field
                    boolean isDzOrComponent = ct.getDynamicZones().stream()
                            .anyMatch(dz -> dz.getName().equals(key));
                    if (!isDzOrComponent) {
                        unknownFields.add(key);
                    }
                }
            } else {
                // Basic type validation
                validateFieldValue(field, data.get(key));
            }
        }

        if (!unknownFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown fields for content type '" + ct.getUid() + "': " + unknownFields);
        }
    }

    /**
     * Validates a single field's value against its definition.
     * Performs basic type checks where feasible.
     */
    private void validateFieldValue(FieldDefinition field, Object value) {
        if (value == null) return;

        FieldType type = field.getType();
        switch (type) {
            case STRING:
            case EMAIL:
            case PASSWORD:
            case TEXT:
            case UID:
                if (!(value instanceof String)) {
                    Log.warnf("Field '%s' expects String, got %s", field.getName(), value.getClass().getSimpleName());
                }
                break;
            case INTEGER:
                if (!(value instanceof Number)) {
                    Log.warnf("Field '%s' expects Number, got %s", field.getName(), value.getClass().getSimpleName());
                }
                break;
            case FLOAT:
            case DECIMAL:
                if (!(value instanceof Number)) {
                    Log.warnf("Field '%s' expects Number, got %s", field.getName(), value.getClass().getSimpleName());
                }
                break;
            case BOOLEAN:
                if (!(value instanceof Boolean)) {
                    Log.warnf("Field '%s' expects Boolean, got %s", field.getName(), value.getClass().getSimpleName());
                }
                break;
            case DATE:
            case DATETIME:
            case TIME:
                // Accept strings (ISO format) or numeric timestamps
                if (!(value instanceof String) && !(value instanceof Number)) {
                    Log.warnf("Field '%s' expects String/Number date, got %s",
                            field.getName(), value.getClass().getSimpleName());
                }
                break;
            case JSON:
                // Any value acceptable for JSON field
                break;
            case MEDIA:
            case RELATION:
                // Accept ID strings, maps, or lists
                break;
            case ENUMERATION:
                if (value instanceof String s && !field.getEnumValues().isEmpty()
                        && !field.getEnumValues().contains(s)) {
                    Log.warnf("Field '%s' value '%s' is not in allowed enum values: %s",
                            field.getName(), s, field.getEnumValues());
                }
                break;
            case COMPONENT:
            case DYNAMIC_ZONE:
                // Must be a map or list of maps
                if (!(value instanceof Map) && !(value instanceof List)) {
                    Log.warnf("Field '%s' expects Map or List, got %s",
                            field.getName(), value.getClass().getSimpleName());
                }
                break;
            default:
                break;
        }
    }

    // ========================================================================
    // Internal: Relation Processing
    // ========================================================================

    /**
     * Extracts relation data from the entry data payload and establishes the
     * corresponding relations in the relation table. Fields whose values look
     * like document IDs or lists of document IDs are assumed to be relation fields.
     *
     * @param ct          the content type definition (for schema-based relation detection)
     * @param documentId  the source document ID
     * @param contentType the source content type UID
     * @param data        the entry data payload
     */
    @SuppressWarnings("unchecked")
    private void processRelationFields(ContentTypeDefinition ct, String documentId,
                                        String contentType, Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            // Determine if this field is a relation based on schema
            boolean isRelation = ct.getRelations().stream()
                    .anyMatch(r -> r.getFieldName().equals(fieldName));

            if (!isRelation) continue;

            // Clear existing relations for this field first
            relationService.detachAll(documentId, fieldName);

            // Process relation values
            processRelationValue(documentId, contentType, fieldName, value, 0);
        }
    }

    @SuppressWarnings("unchecked")
    private void processRelationValue(String sourceDocumentId, String sourceType,
                                       String fieldName, Object value, int orderIndex) {
        if (value == null) return;

        if (value instanceof String targetId) {
            attachRelation(sourceDocumentId, sourceType, targetId, "*", fieldName, orderIndex);
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String targetId) {
                    attachRelation(sourceDocumentId, sourceType, targetId, "*", fieldName, orderIndex++);
                } else if (item instanceof Map<?, ?> m) {
                    Object docId = m.get("documentId");
                    if (docId instanceof String id) {
                        attachRelation(sourceDocumentId, sourceType, id, "*", fieldName, orderIndex++);
                    }
                }
            }
        } else if (value instanceof Map<?, ?> m) {
            Object docId = m.get("documentId");
            if (docId instanceof String id) {
                attachRelation(sourceDocumentId, sourceType, id, "*", fieldName, orderIndex);
            }
        }
    }

    // ========================================================================
    // Internal Helpers
    // ========================================================================

    private ContentTypeDefinition validateContentType(String contentType) {
        ContentTypeDefinition ct = schemaStorageService.getContentType(contentType);
        if (ct == null) {
            throw new IllegalArgumentException("Unknown content type: " + contentType);
        }
        return ct;
    }

    private String resolveContentType(String documentId, CmsEntry entry) {
        if (entry != null && entry.contentType != null) {
            return entry.contentType;
        }
        return "unknown";
    }

    private CmsEntry findSingleTypeEntry(String contentType, String locale) {
        String loc = locale != null ? locale : "en";
        CmsEntry draft = CmsEntry.find(
                "contentType = ?1 and status = ?2 and locale = ?3",
                contentType, "draft", loc).firstResult();
        if (draft != null) return draft;
        return CmsEntry.find(
                "contentType = ?1 and status = ?2 and locale = ?3 order by versionNumber desc",
                contentType, "published", loc).firstResult();
    }

    // ========================================================================
    // Inner types
    // ========================================================================

    /**
     * Descriptor for a single item in a bulk update operation.
     */
    public record BulkUpdateItem(String documentId, Map<String, Object> data, String locale) {
        public BulkUpdateItem(String documentId, Map<String, Object> data) {
            this(documentId, data, "en");
        }
    }
}
