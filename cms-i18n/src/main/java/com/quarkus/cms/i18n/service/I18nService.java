package com.quarkus.cms.i18n.service;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core service for internationalization operations.
 *
 * <p>Handles creating localizations (translation variants) of content entries,
 * retrieving entries with locale fallback, and filtering localized fields
 * according to the content-type schema configuration.
 */
@ApplicationScoped
public class I18nService {

  @Inject
  SchemaStorageService schemaStorageService;

  @Inject
  LocaleService localeService;

  @PersistenceContext
  EntityManager entityManager;

  // ---- Localization CRUD ----

  /**
   * Returns all localizations (translations) for a document, grouped by locale.
   *
   * <p>This finds all entries sharing the same {@code documentId} across different locales.
   * For each locale, the most recent version (published or draft) is returned.
   *
   * @param documentId the document ID to find localizations for
   * @return map of locale code to entry, empty map if none found
   */
  public Map<String, CmsEntry> getLocalizations(String documentId) {
    List<CmsEntry> allEntries = CmsEntry.list("documentId", documentId);
    if (allEntries.isEmpty()) {
      return Map.of();
    }

    // For each locale, return the most relevant version (published > draft > unpublished)
    Map<String, CmsEntry> bestPerLocale = new LinkedHashMap<>();
    for (CmsEntry entry : allEntries) {
      if (entry == null) continue;
      String locale = entry.locale;
      CmsEntry existing = bestPerLocale.get(locale);
      if (existing == null || shouldReplace(existing, entry)) {
        bestPerLocale.put(locale, entry);
      }
    }
    return bestPerLocale;
  }

  /**
   * Returns a list of locale summaries for all translations of a document.
   * Lightweight version of {@link #getLocalizations(String)} returning only
   * locale codes and publish status.
   */
  public List<Map<String, Object>> getLocalizationsSummary(String documentId) {
    Map<String, CmsEntry> localizations = getLocalizations(documentId);
    return localizations.entrySet().stream()
        .map(e -> {
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("locale", e.getKey());
          summary.put("status", e.getValue().status);
          summary.put("published", "published".equals(e.getValue().status));
          return summary;
        })
        .toList();
  }

  /**
   * Creates a new locale variant of an existing entry.
   *
   * <p>From a source entry in one locale, this creates a new entry sharing the same
   * {@code documentId} but with the target locale. Localized fields are populated
   * from the provided {@code localizedData}; non-localized fields are copied from
   * the source entry in the default locale.
   *
   * @param documentId      the document ID
   * @param sourceLocale    the source locale to copy non-localized fields from
   * @param targetLocale    the target locale for the new translation
   * @param localizedData   field data specific to the new locale
   * @param userId          the user performing the operation
   * @return the newly created entry in the target locale
   * @throws IllegalArgumentException if the source entry or content type is not found,
   *         or if a translation already exists for the target locale
   */
  @Transactional
  public CmsEntry createLocalization(
      String documentId, String sourceLocale, String targetLocale,
      Map<String, Object> localizedData, Long userId) {

    // Flush to ensure recently persisted entities are visible
    entityManager.flush();

    // Find the source entry to copy non-localized fields from
    CmsEntry source = findBestEntry(documentId, sourceLocale);
    if (source == null) {
      throw new IllegalArgumentException(
          "Source entry not found for documentId=" + documentId + " locale=" + sourceLocale);
    }

    // Check if a translation already exists for target locale
    Map<String, CmsEntry> existing = getLocalizations(documentId);
    if (existing.containsKey(targetLocale)) {
      throw new IllegalArgumentException(
          "Translation already exists for documentId=" + documentId + " locale=" + targetLocale
              + ". Use update instead.");
    }

    // Determine which fields are localized vs non-localized
    ContentTypeDefinition ct = schemaStorageService.getContentType(source.contentType);

    Map<String, Object> mergedData;
    if (ct != null) {
      mergedData = buildLocalizedData(source.data, ct, localizedData);
    } else {
      // No schema metadata available — just merge
      mergedData = new HashMap<>(source.data);
      if (localizedData != null) {
        mergedData.putAll(localizedData);
      }
    }

    // Create new entry in target locale
    CmsEntry translation = new CmsEntry();
    translation.documentId = documentId;
    translation.contentType = source.contentType;
    translation.locale = targetLocale;
    translation.status = "draft";
    translation.versionNumber = 0;
    translation.data = mergedData;
    translation.createdById = userId;
    translation.updatedById = userId;
    translation.persist();

    Log.infof("Created localization: doc=%s locale=%s from=%s",
        documentId, targetLocale, sourceLocale);
    return translation;
  }

  /**
   * Gets an entry with locale fallback.
   *
   * <p>Tries, in order:
   * <ol>
   *   <li>The requested locale</li>
   *   <li>The default locale</li>
   *   <li>Any available locale</li>
   * </ol>
   *
   * If a fallback occurs, non-localized fields are populated from the fallback
   * so the response is complete even when the requested locale doesn't have
   * all fields translated yet.
   *
   * @param documentId     the document ID
   * @param requestedLocale the requested locale (may be null for auto-detect)
   * @param status         the entry status filter (e.g., "published", "draft")
   * @return the best matching entry, or {@code null} if none found in any locale
   */
  public CmsEntry getWithFallback(String documentId, String requestedLocale, String status) {
    String defaultLocale = localeService.getDefaultLocale();
    String resolvedLocale = requestedLocale != null ? requestedLocale : defaultLocale;

    // 1. Try the resolved locale
    CmsEntry entry = findEntryByLocale(documentId, resolvedLocale, status);
    if (entry != null) {
      return entry;
    }

    // 2. Try the default locale
    if (!resolvedLocale.equals(defaultLocale)) {
      entry = findEntryByLocale(documentId, defaultLocale, status);
      if (entry != null) {
        return entry;
      }
    }

    // 3. Try any available locale
    Map<String, CmsEntry> localizations = getLocalizations(documentId);
    if (!localizations.isEmpty()) {
      // Pick the first one that matches the requested status
      for (CmsEntry locEntry : localizations.values()) {
        if (status == null || status.equals(locEntry.status)) {
          return locEntry;
        }
      }
      // Fallback to any entry regardless of status
      return localizations.values().iterator().next();
    }

    return null;
  }

  /**
   * Returns the localized fields from entry data, applying locale fallback
   * for non-localized fields.
   *
   * <p>This merges data from the requested locale entry with data from the
   * default locale entry so that non-localized fields always have values
   * even when not translated yet.
   */
  public Map<String, Object> getLocalizedData(
      CmsEntry entry, String requestedLocale) {

    if (entry == null) return Map.of();

    String defaultLocale = localeService.getDefaultLocale();

    // If the entry is already in the default locale, return as-is
    if (entry.locale.equals(defaultLocale)) {
      return entry.data != null ? entry.data : Map.of();
    }

    ContentTypeDefinition ct = schemaStorageService.getContentType(entry.contentType);
    if (ct == null) {
      // No schema — return entry's data as-is
      return entry.data != null ? entry.data : Map.of();
    }

    // Find the default locale entry to fill in non-localized fields
    CmsEntry defaultEntry = findEntryByLocale(entry.documentId, defaultLocale, null);

    Map<String, Object> result = new HashMap<>();
    if (defaultEntry != null && defaultEntry.data != null) {
      result.putAll(defaultEntry.data);
    }
    // Overlay the requested locale's data (contains localized fields)
    if (entry.data != null) {
      result.putAll(entry.data);
    }

    return result;
  }

  // ---- Internal Helpers ----

  /**
   * Builds merged data for a new translation:
   * - Non-localized fields from source entry
   * - Localized fields from the provided data
   */
  private Map<String, Object> buildLocalizedData(
      Map<String, Object> sourceData, ContentTypeDefinition ct,
      Map<String, Object> localizedData) {

    Map<String, Object> result = new HashMap<>();

    if (sourceData != null) {
      result.putAll(sourceData);
    }

    // Remove localized fields from the source and replace with new values
    Map<String, FieldDefinition> fieldMap = getLocalizedFields(ct);
    for (String fieldName : fieldMap.keySet()) {
      result.remove(fieldName);
    }

    // Apply the new locale-specific data
    if (localizedData != null) {
      for (Map.Entry<String, Object> field : localizedData.entrySet()) {
        if (fieldMap.containsKey(field.getKey())) {
          result.put(field.getKey(), field.getValue());
        }
      }
    }

    return result;
  }

  /**
   * Returns the fields that are marked as localized for a content type.
   */
  private Map<String, FieldDefinition> getLocalizedFields(ContentTypeDefinition ct) {
    if (ct == null) return Map.of();
    Map<String, FieldDefinition> localized = new LinkedHashMap<>();
    for (FieldDefinition field : ct.getFields()) {
      if (field.isLocalized()) {
        localized.put(field.getName(), field);
      }
    }
    return localized;
  }

  /**
   * Determines whether a new entry should replace an existing one
   * when selecting the best entry per locale. Prefers published over draft,
   * draft over unpublished, higher version number.
   */
  private boolean shouldReplace(CmsEntry existing, CmsEntry candidate) {
    int existingRank = statusRank(existing.status);
    int candidateRank = statusRank(candidate.status);
    if (candidateRank != existingRank) {
      return candidateRank > existingRank;
    }
    // Same status — prefer higher version number
    return candidate.versionNumber != null
        && (existing.versionNumber == null || candidate.versionNumber > existing.versionNumber);
  }

  private int statusRank(String status) {
    if ("published".equals(status)) return 3;
    if ("draft".equals(status)) return 2;
    return 1; // unpublished or other
  }

  /** Finds the best entry for a document in a specific locale matching the given status. */
  private CmsEntry findEntryByLocale(String documentId, String locale, String status) {
    if (status != null && !status.isBlank()) {
      return CmsEntry.findByDocumentId(documentId, status, locale);
    }
    // Try published first, then draft
    CmsEntry published = CmsEntry.findByDocumentId(documentId, "published", locale);
    if (published != null) return published;
    return CmsEntry.findByDocumentId(documentId, "draft", locale);
  }

  /** Finds the best entry for a document in a specific locale (any status). */
  private CmsEntry findBestEntry(String documentId, String locale) {
    // Force flush to ensure recently persisted entities are visible
    entityManager.flush();
    CmsEntry published = CmsEntry.findByDocumentId(documentId, "published", locale);
    if (published != null) return published;
    CmsEntry draft = CmsEntry.findByDocumentId(documentId, "draft", locale);
    if (draft != null) return draft;
    return (CmsEntry) CmsEntry.find(
        "documentId = ?1 and locale = ?2 order by versionNumber desc",
        documentId, locale).firstResult();
  }
}
