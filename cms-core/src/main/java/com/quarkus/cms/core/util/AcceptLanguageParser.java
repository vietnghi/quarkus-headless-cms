package com.quarkus.cms.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple parser for HTTP Accept-Language headers.
 *
 * <p>Parses the header value, sorts by quality value (q=), and provides
 * methods to find the best matching language from a list of supported locales.
 *
 * <p>This utility does not require database access and can be used by any module.
 * For full locale resolution with DB-backed locale management, use
 * {@code com.quarkus.cms.i18n.service.LocaleService}.
 */
public final class AcceptLanguageParser {

  private AcceptLanguageParser() {}

  /**
   * Parses an Accept-Language header and returns the best matching language
   * from the given supported locale codes.
   *
   * @param header          the Accept-Language header value (e.g., "fr-CH, fr;q=0.9, en;q=0.8")
   * @param supportedLocales list of supported locale codes (e.g., ["en", "fr", "de"])
   * @return the best matching locale code, or the first supported locale if no match
   */
  public static String bestMatch(String header, List<String> supportedLocales) {
    if (header == null || header.isBlank()) {
      return supportedLocales.isEmpty() ? "en" : supportedLocales.get(0);
    }
    if (supportedLocales.isEmpty()) {
      return "en";
    }

    List<LanguageWeight> parsed = parse(header);
    if (parsed.isEmpty()) {
      return supportedLocales.get(0);
    }

    // Try exact matches first (sorted by q-value)
    for (LanguageWeight lw : parsed) {
      String normalized = normalizeCode(lw.languageTag);
      for (String supported : supportedLocales) {
        if (supported.equalsIgnoreCase(normalized)) {
          return supported;
        }
      }
    }

    // Try primary language prefix matches (e.g., "en-US" matches "en")
    for (LanguageWeight lw : parsed) {
      String primary = primaryLanguage(lw.languageTag);
      if (primary != null) {
        for (String supported : supportedLocales) {
          if (supported.equalsIgnoreCase(primary)) {
            return supported;
          }
        }
      }
    }

    // Fallback to the first supported locale
    return supportedLocales.get(0);
  }

  /**
   * Parses an Accept-Language header and returns the weighted list of language tags,
   * sorted by quality value (highest first).
   */
  public static List<LanguageWeight> parse(String header) {
    if (header == null || header.isBlank()) return List.of();

    List<LanguageWeight> result = new ArrayList<>();
    for (String part : header.split(",")) {
      part = part.trim();
      String[] segments = part.split(";");
      String lang = segments[0].trim();
      if (lang.isEmpty() || "*".equals(lang)) continue;

      double q = 1.0;
      for (int i = 1; i < segments.length; i++) {
        String s = segments[i].trim();
        if (s.startsWith("q=")) {
          try {
            q = Double.parseDouble(s.substring(2));
          } catch (NumberFormatException ignored) {
          }
        }
      }
      result.add(new LanguageWeight(lang, q));
    }

    result.sort((a, b) -> Double.compare(b.weight, a.weight));
    return result;
  }

  /** Normalizes a locale code to lowercase with standard hyphen format. */
  public static String normalizeCode(String code) {
    if (code == null) return null;
    try {
      Locale loc = Locale.forLanguageTag(code.replace('_', '-'));
      String lang = loc.getLanguage();
      String country = loc.getCountry();
      return country.isEmpty() ? lang : lang + "-" + country;
    } catch (Exception e) {
      return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /** Extracts the primary language from a locale code (e.g., "en-US" → "en"). */
  public static String primaryLanguage(String code) {
    if (code == null) return null;
    String normalized = normalizeCode(code);
    int idx = normalized.indexOf('-');
    return idx > 0 ? normalized.substring(0, idx) : normalized;
  }

  /** Weighted language tag from an Accept-Language header. */
  public static final class LanguageWeight {
    public final String languageTag;
    public final double weight;

    public LanguageWeight(String languageTag, double weight) {
      this.languageTag = languageTag;
      this.weight = weight;
    }

    @Override
    public String toString() {
      return languageTag + ";q=" + weight;
    }
  }
}
