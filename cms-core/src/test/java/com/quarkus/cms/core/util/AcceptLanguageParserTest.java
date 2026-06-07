package com.quarkus.cms.core.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for Accept-Language header parsing and locale resolution.
 */
class AcceptLanguageParserTest {

  @Test
  void shouldParseSingleLanguage() {
    var result = AcceptLanguageParser.parse("en");
    assertEquals(1, result.size());
    assertEquals("en", result.get(0).languageTag);
    assertEquals(1.0, result.get(0).weight, 0.001);
  }

  @Test
  void shouldParseWithQualityValues() {
    var result = AcceptLanguageParser.parse("fr-CH, fr;q=0.9, en;q=0.8, de;q=0.7");
    assertEquals(4, result.size());
    assertEquals("fr-CH", result.get(0).languageTag);
    assertEquals("fr", result.get(1).languageTag);
    assertEquals("en", result.get(2).languageTag);
    assertEquals("de", result.get(3).languageTag);
  }

  @Test
  void shouldSortByQualityDescending() {
    var result = AcceptLanguageParser.parse("de;q=0.5, en;q=0.8, fr;q=0.9");
    assertEquals("fr", result.get(0).languageTag);
    assertEquals("en", result.get(1).languageTag);
    assertEquals("de", result.get(2).languageTag);
  }

  @Test
  void shouldHandleEmptyHeader() {
    assertTrue(AcceptLanguageParser.parse(null).isEmpty());
    assertTrue(AcceptLanguageParser.parse("").isEmpty());
    assertTrue(AcceptLanguageParser.parse("   ").isEmpty());
  }

  @Test
  void shouldSkipWildcard() {
    var result = AcceptLanguageParser.parse("en, *;q=0.5");
    assertEquals(1, result.size());
    assertEquals("en", result.get(0).languageTag);
  }

  @Test
  void shouldBestMatchExactLocales() {
    String best = AcceptLanguageParser.bestMatch(
        "fr-CH, fr;q=0.9, en;q=0.8",
        List.of("en", "fr", "de"));
    assertEquals("fr", best);
  }

  @Test
  void shouldBestMatchPrimaryLanguage() {
    String best = AcceptLanguageParser.bestMatch(
        "en-US;q=0.9, de;q=0.8",
        List.of("en", "fr"));
    assertEquals("en", best);
  }

  @Test
  void shouldFallbackToFirstSupported() {
    String best = AcceptLanguageParser.bestMatch(
        "ja;q=0.9, ko;q=0.8",
        List.of("en", "fr"));
    assertEquals("en", best);
  }

  @Test
  void shouldReturnEnForEmptySupported() {
    String best = AcceptLanguageParser.bestMatch("fr", List.of());
    assertEquals("en", best);
  }

  @Test
  void shouldNormalizeCode() {
    assertEquals("en", AcceptLanguageParser.normalizeCode("EN"));
    assertEquals("en-US", AcceptLanguageParser.normalizeCode("EN_US"));
    assertEquals("fr-CH", AcceptLanguageParser.normalizeCode("fr-CH"));
  }

  @Test
  void shouldExtractPrimaryLanguage() {
    assertEquals("en", AcceptLanguageParser.primaryLanguage("en-US"));
    assertEquals("fr", AcceptLanguageParser.primaryLanguage("fr"));
    assertNull(AcceptLanguageParser.primaryLanguage(null));
  }

  @Test
  void shouldPickCorrectLocaleWithQuality() {
    String best = AcceptLanguageParser.bestMatch(
        "en;q=0.1, fr;q=0.9",
        List.of("en", "fr"));
    assertEquals("fr", best);
  }

  @Test
  void shouldHandleMultipleSubtags() {
    var result = AcceptLanguageParser.parse("en-GB, en-US;q=0.9, en;q=0.8");
    assertEquals(3, result.size());
    assertEquals("en-GB", result.get(0).languageTag);
    assertEquals("en-US", result.get(1).languageTag);
    assertEquals("en", result.get(2).languageTag);
  }
}
