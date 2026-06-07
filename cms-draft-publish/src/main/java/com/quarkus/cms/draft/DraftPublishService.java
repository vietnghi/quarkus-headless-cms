package com.quarkus.cms.draft;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.draft.model.ContentStatus;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the draft/publish content lifecycle with versioning.
 * <p>
 * Each content entry is identified by a {@code documentId} that groups
 * draft and published versions together. The draft (versionNumber=0)
 * is the mutable working copy. Publishing creates an immutable snapshot
 * with an incrementing version number. Only one draft and one "current"
 * published version exist per (documentId, locale) pair.
 * <p>
 * This mirrors the Strapi v5 draft/publish system, adapted for the
 * hybrid document-on-RDBMS schema in this CMS.
 */
@ApplicationScoped
public class DraftPublishService {

    // ---- Create / Update drafts ----

    /**
     * Creates a brand-new entry as a draft with a generated document ID.
     *
     * @param contentType the content type UID (e.g. "api::article.article")
     * @param data        the content field data (JSONB)
     * @param locale      the locale code (e.g. "en")
     * @param userId      the ID of the user creating the entry
     * @return the persisted draft entry
     */
    @Transactional
    public CmsEntry createDraft(String contentType, Map<String, Object> data,
                                 String locale, Long userId) {
        var entry = new CmsEntry();
        entry.documentId = UUID.randomUUID().toString();
        entry.contentType = contentType;
        entry.locale = locale;
        entry.status = ContentStatus.DRAFT.getValue();
        entry.versionNumber = 0;
        entry.data = (data != null) ? data : Map.of();
        entry.createdById = userId;
        entry.updatedById = userId;
        entry.persist();
        return entry;
    }

    /**
     * Updates an existing draft. If no draft exists but a published version
     * does, a new draft is created from the published data as a starting point.
     *
     * @param documentId the document ID
     * @param data       the new content data (merged into existing data)
     * @param locale     the locale code
     * @param userId     the ID of the user performing the update
     * @return the updated draft entry
     * @throws IllegalStateException if no entry exists for this documentId
     */
    @Transactional
    public CmsEntry updateDraft(String documentId, Map<String, Object> data,
                                 String locale, Long userId) {
        var draft = findDraft(documentId, locale);

        if (draft != null) {
            // Update existing draft
            if (data != null) {
                draft.data.putAll(data);
            }
            draft.updatedById = userId;
            draft.persist();
            return draft;
        }

        // No draft — check for a published version to clone from
        var published = findCurrentPublished(documentId, locale);
        if (published == null) {
            // If no published, try to find any version to clone from (e.g., unpublished)
            published = (CmsEntry) CmsEntry.find(
                "documentId = ?1 and locale = ?2 and status <> ?3 order by versionNumber desc",
                documentId, locale, ContentStatus.DRAFT.getValue()).firstResult();
        }
        if (published != null) {
            var newDraft = new CmsEntry();
            newDraft.documentId = documentId;
            newDraft.contentType = published.contentType;
            newDraft.locale = locale;
            newDraft.status = ContentStatus.DRAFT.getValue();
            newDraft.versionNumber = 0;
            newDraft.data = new java.util.HashMap<>(published.data);
            if (data != null) {
                newDraft.data.putAll(data);
            }
            newDraft.createdById = published.createdById;
            newDraft.updatedById = userId;
            newDraft.persist();
            return newDraft;
        }

        // Fallback: check for any existing entry to clone from (e.g. unpublished)
        var anyEntry = findAnyEntry(documentId, locale);
        if (anyEntry != null) {
            var newDraft = new CmsEntry();
            newDraft.documentId = documentId;
            newDraft.contentType = anyEntry.contentType;
            newDraft.locale = locale;
            newDraft.status = ContentStatus.DRAFT.getValue();
            newDraft.versionNumber = 0;
            newDraft.data = new java.util.HashMap<>(anyEntry.data);
            if (data != null) {
                newDraft.data.putAll(data);
            }
            newDraft.createdById = anyEntry.createdById;
            newDraft.updatedById = userId;
            newDraft.persist();
            return newDraft;
        }

        throw new IllegalStateException(
            "No entry found for documentId=" + documentId + " locale=" + locale);
    }

    // ---- Publish / Unpublish ----

    /**
     * Publishes the current draft, creating an immutable version snapshot.
     * <p>
     * The draft data is copied to a new published entry with an incremented
     * version number. The draft itself is preserved for further editing.
     * Any previously published version remains as history.
     *
     * @param documentId the document ID
     * @param locale     the locale code
     * @param userId     the ID of the user publishing
     * @return the newly created published entry
     * @throws IllegalStateException if no draft exists to publish
     */
    @Transactional
    public CmsEntry publish(String documentId, String locale, Long userId) {
        var draft = findDraft(documentId, locale);
        if (draft == null) {
            throw new IllegalStateException(
                "No draft found to publish for documentId=" + documentId + " locale=" + locale);
        }

        int nextVersion = getNextVersionNumber(documentId, locale);

        var published = new CmsEntry();
        published.documentId = documentId;
        published.contentType = draft.contentType;
        published.locale = locale;
        published.status = ContentStatus.PUBLISHED.getValue();
        published.versionNumber = nextVersion;
        published.data = new java.util.HashMap<>(draft.data);
        published.createdById = draft.createdById;
        published.updatedById = userId;
        published.publishedById = userId;
        published.publishedAt = Instant.now();
        published.persist();

        // Discard the draft after publishing so there are no unpublished changes
        draft.delete();

        return published;
    }

    /**
     * Unpublishes the currently published version.
     * The published entry is marked as {@code unpublished} and retains its
     * data and version number for audit trail purposes.
     *
     * @param documentId the document ID
     * @param locale     the locale code
     * @throws IllegalStateException if no published entry exists
     */
    @Transactional
    public void unpublish(String documentId, String locale) {
        var published = findCurrentPublished(documentId, locale);
        if (published == null) {
            throw new IllegalStateException(
                "No published entry to unpublish for documentId=" + documentId + " locale=" + locale);
        }
        published.status = ContentStatus.UNPUBLISHED.getValue();
        published.persist();
    }

    // ---- Discard draft ----

    /**
     * Discards (deletes) the current draft, leaving the published version untouched.
     *
     * @param documentId the document ID
     * @param locale     the locale code
     */
    @Transactional
    public void discardDraft(String documentId, String locale) {
        var draft = findDraft(documentId, locale);
        if (draft != null) {
            draft.delete();
        }
    }

    // ---- Queries ----

    /**
     * Retrieves the current draft for a document.
     *
     * @return the draft entry, or {@code null} if none exists
     */
    public CmsEntry getDraft(String documentId, String locale) {
        return findDraft(documentId, locale);
    }

    /**
     * Retrieves the currently published version (highest version number).
     *
     * @return the published entry, or {@code null} if none exists
     */
    public CmsEntry getPublished(String documentId, String locale) {
        return findCurrentPublished(documentId, locale);
    }

    /**
     * Returns all entries (draft + published versions) for a document,
     * ordered by version number descending (newest first).
     */
    public List<CmsEntry> getVersions(String documentId, String locale) {
        return CmsEntry.list(
            "documentId = ?1 and locale = ?2 order by versionNumber desc",
            documentId, locale);
    }

    /**
     * Retrieves a specific published version by number.
     *
     * @return the version entry, or {@code null} if not found
     */
    public CmsEntry getVersion(String documentId, int versionNumber, String locale) {
        return (CmsEntry) CmsEntry.find(
            "documentId = ?1 and versionNumber = ?2 and locale = ?3",
            documentId, versionNumber, locale).firstResult();
    }

    /**
     * Checks whether the draft has unsaved changes compared to the published version.
     * Returns {@code true} when the draft exists with different data than published.
     */
    public boolean hasUnpublishedChanges(String documentId, String locale) {
        var draft = findDraft(documentId, locale);
        var published = findCurrentPublished(documentId, locale);
        if (draft == null && published != null) {
            return false;
        }
        if (draft != null && published == null) {
            return true;
        }
        if (draft != null) {
            return !draft.data.equals(published.data);
        }
        return false;
    }

    // ---- Internal helpers ----

    private CmsEntry findDraft(String documentId, String locale) {
        return (CmsEntry) CmsEntry.find(
            "documentId = ?1 and status = ?2 and locale = ?3",
            documentId, ContentStatus.DRAFT.getValue(), locale).firstResult();
    }

    private CmsEntry findCurrentPublished(String documentId, String locale) {
        return (CmsEntry) CmsEntry.find(
            "documentId = ?1 and status = ?2 and locale = ?3 order by versionNumber desc",
            documentId, ContentStatus.PUBLISHED.getValue(), locale).firstResult();
    }

    private CmsEntry findAnyEntry(String documentId, String locale) {
        return (CmsEntry) CmsEntry.find(
            "documentId = ?1 and locale = ?2 order by versionNumber desc",
            documentId, locale).firstResult();
    }

    private int getNextVersionNumber(String documentId, String locale) {
        var latest = (CmsEntry) CmsEntry.find(
            "documentId = ?1 and locale = ?2 order by versionNumber desc",
            documentId, locale).firstResult();
        if (latest == null || latest.versionNumber == null || latest.versionNumber == 0) {
            return 1;
        }
        return latest.versionNumber + 1;
    }
}
