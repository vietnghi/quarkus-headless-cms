package com.quarkus.cms.i18n.service;

import com.quarkus.cms.i18n.dto.LocaleDto;
import com.quarkus.cms.i18n.model.CmsLocale;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service for managing CMS locales.
 *
 * <p>Provides CRUD operations for available locales, default locale configuration,
 * and locale resolution logic used by the i18n system.
 */
@ApplicationScoped
public class LocaleService {

  // ---- CRUD ----

  /** Returns all configured locales, ordered by code. */
  public List<LocaleDto> listLocales() {
    return CmsLocale.listAll().stream()
        .map(l -> LocaleDto.from((CmsLocale) l))
        .sorted(Comparator.comparing(a -> a.code))
        .toList();
  }

  /** Returns a single locale by code, or empty. */
  public Optional<LocaleDto> getLocale(String code) {
    CmsLocale loc = CmsLocale.findByCode(code);
    return loc != null ? Optional.of(LocaleDto.from(loc)) : Optional.empty();
  }

  /**
   * Creates a new locale. If {@code isDefault} is true, clears the default flag
   * from any other locale first.
   */
  @Transactional
  public LocaleDto createLocale(LocaleDto dto) {
    if (dto.code == null || dto.code.isBlank()) {
      throw new IllegalArgumentException("Locale code is required");
    }
    String normalizedCode = normalizeCode(dto.code);

    // Check for duplicate
    if (CmsLocale.findByCode(normalizedCode) != null) {
      throw new IllegalArgumentException("Locale already exists: " + normalizedCode);
    }

    // If setting as default, clear existing default
    if (dto.isDefault) {
      clearDefaultLocale();
    }

    // Ensure first locale becomes default if none exists
    boolean becomesDefault = dto.isDefault || CmsLocale.count() == 0;

    CmsLocale loc = new CmsLocale();
    loc.code = normalizedCode;
    loc.displayName = dto.displayName != null ? dto.displayName : normalizedCode;
    loc.isDefault = becomesDefault;
    loc.enabled = dto.enabled;
    loc.persist();

    Log.infof("Created locale: %s (default=%b)", normalizedCode, becomesDefault);
    return LocaleDto.from(loc);
  }

  /**
   * Updates an existing locale.
   */
  @Transactional
  public LocaleDto updateLocale(String code, LocaleDto dto) {
    CmsLocale loc = CmsLocale.findByCode(code);
    if (loc == null) {
      throw new IllegalArgumentException("Locale not found: " + code);
    }

    if (dto.displayName != null) {
      loc.displayName = dto.displayName;
    }
    // enabled is boolean primitive; always apply it
    loc.enabled = dto.enabled;
    if (dto.isDefault) {
      clearDefaultLocale();
      loc.isDefault = true;
    }

    loc.persist();
    Log.infof("Updated locale: %s", code);
    return LocaleDto.from(loc);
  }

  /**
   * Deletes a locale by code. Cannot delete the default locale.
   */
  @Transactional
  public void deleteLocale(String code) {
    CmsLocale loc = CmsLocale.findByCode(code);
    if (loc == null) {
      throw new IllegalArgumentException("Locale not found: " + code);
    }
    if (loc.isDefault != null && loc.isDefault) {
      throw new IllegalStateException("Cannot delete the default locale. Set a new default first.");
    }
    loc.delete();
    Log.infof("Deleted locale: %s", code);
  }

  // ---- Lookup & Resolution ----

  /** Returns the default locale code, falling back to "en". */
  public String getDefaultLocale() {
    return CmsLocale.defaultCode();
  }

  /** Returns the list of locale codes that are available and enabled. */
  public List<String> getEnabledLocaleCodes() {
    return CmsLocale.findEnabled().stream()
        .map(l -> l.code)
        .toList();
  }

  /**
   * Resolves the best matching locale for a request.
   *
   * <p>Tries, in order:
   * <ol>
   *   <li>The explicitly requested locale (if enabled)</li>
   *   <li>The Accept-Language header, parsed and matched against enabled locales</li>
   *   <li>The system default locale</li>
   * </ol>
   *
   * @param requestedLocale     explicit locale from query param (may be null)
   * @param acceptLanguageHeader Accept-Language HTTP header value (may be null)
   * @return the best matching locale code
   */
  public String resolveLocale(String requestedLocale, String acceptLanguageHeader) {
    // 1. Explicitly requested locale
    if (requestedLocale != null && !requestedLocale.isBlank()) {
      String normalized = normalizeCode(requestedLocale);
      CmsLocale loc = CmsLocale.findByCode(normalized);
      if (loc != null && loc.enabled) {
        return normalized;
      }
      // Even if not found/enabled, try to use it as-is for fallback
      return normalized;
    }

    // 2. Accept-Language header
    if (acceptLanguageHeader != null && !acceptLanguageHeader.isBlank()) {
      String bestMatch = parseAcceptLanguage(acceptLanguageHeader);
      if (bestMatch != null) {
        return bestMatch;
      }
    }

    // 3. Default locale
    return getDefaultLocale();
  }

  /**
   * Parses an Accept-Language header and returns the best matching enabled locale code,
   * or {@code null} if no match is found.
   *
   * <p>Handles quality values (q=) and language ranges with subtags.
   */
  String parseAcceptLanguage(String header) {
    if (header == null || header.isBlank()) return null;

    List<String> enabledCodes = getEnabledLocaleCodes();

    // Parse and sort by quality value (descending)
    List<LanguageWeight> languages = new java.util.ArrayList<>();
    for (String part : header.split(",")) {
      part = part.trim();
      String[] segments = part.split(";");
      String lang = segments[0].trim();
      double q = 1.0;
      if (segments.length > 1) {
        for (String s : segments) {
          s = s.trim();
          if (s.startsWith("q=")) {
            try {
              q = Double.parseDouble(s.substring(2));
            } catch (NumberFormatException ignored) {
            }
          }
        }
      }
      languages.add(new LanguageWeight(lang, q));
    }
    languages.sort((a, b) -> Double.compare(b.weight, a.weight));

    // Try exact match first, then language-only prefix match
    for (LanguageWeight lw : languages) {
      String langCode = normalizeCode(lw.lang);
      // Exact match
      for (String enabled : enabledCodes) {
        if (enabled.equalsIgnoreCase(langCode)) {
          return enabled;
        }
      }
      // Prefix match: "en-US" header vs "en" enabled locale
      String primaryLang = langCode.contains("-") ? langCode.substring(0, langCode.indexOf('-')) : langCode;
      for (String enabled : enabledCodes) {
        if (enabled.equalsIgnoreCase(primaryLang)) {
          return enabled;
        }
      }
    }

    return null;
  }

  /** Normalizes a locale code to lowercase with standard hyphen format. */
  public static String normalizeCode(String code) {
    if (code == null) return null;
    // Use Locale.forLanguageTag for proper normalization
    try {
      java.util.Locale loc = java.util.Locale.forLanguageTag(code.replace('_', '-'));
      String lang = loc.getLanguage();
      String country = loc.getCountry();
      return country.isEmpty() ? lang : lang + "-" + country;
    } catch (Exception e) {
      return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  // ---- Internal ----

  @Transactional
  void clearDefaultLocale() {
    CmsLocale currentDefault = CmsLocale.findDefault();
    if (currentDefault != null) {
      currentDefault.isDefault = false;
      currentDefault.persist();
    }
  }

  /** Internal weight-sorted language entry. */
  private static final class LanguageWeight {
    final String lang;
    final double weight;

    LanguageWeight(String lang, double weight) {
      this.lang = lang;
      this.weight = weight;
    }
  }
}
