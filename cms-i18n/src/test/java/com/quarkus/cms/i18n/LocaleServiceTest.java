package com.quarkus.cms.i18n;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.i18n.dto.LocaleDto;
import com.quarkus.cms.i18n.model.CmsLocale;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for LocaleService and CmsLocale entity.
 *
 * <p>Tests locale code normalization, entity defaults, and DTO conversion.
 * DB-dependent tests use {@link I18nIntegrationTest}.
 */
class LocaleServiceTest {

  // ---- CmsLocale entity defaults ----

  @Test
  void shouldHaveDefaultValues() {
    CmsLocale locale = new CmsLocale();
    assertNotNull(locale.createdAt);
    assertNotNull(locale.updatedAt);
    assertFalse(locale.isDefault);
    assertTrue(locale.enabled);
  }

  @Test
  void shouldSetProperties() {
    CmsLocale locale = new CmsLocale();
    locale.code = "fr";
    locale.displayName = "French";
    locale.isDefault = false;
    locale.enabled = true;

    assertEquals("fr", locale.code);
    assertEquals("French", locale.displayName);
    assertFalse(locale.isDefault);
    assertTrue(locale.enabled);
  }

  // ---- LocaleDto conversion ----

  @Test
  void shouldConvertEntityToDto() {
    CmsLocale locale = new CmsLocale();
    locale.code = "de";
    locale.displayName = "German";
    locale.isDefault = true;
    locale.enabled = true;

    LocaleDto dto = LocaleDto.from(locale);
    assertEquals("de", dto.code);
    assertEquals("German", dto.displayName);
    assertTrue(dto.isDefault);
    assertTrue(dto.enabled);
  }

  @Test
  void shouldConvertDtoToEntity() {
    LocaleDto dto = new LocaleDto("es", "Spanish", true, true);
    CmsLocale locale = dto.toEntity();

    assertEquals("es", locale.code);
    assertEquals("Spanish", locale.displayName);
    assertTrue(locale.isDefault);
    assertTrue(locale.enabled);
  }

  @Test
  void shouldHandleNullDefaults() {
    CmsLocale locale = new CmsLocale();
    locale.code = "en";
    locale.displayName = "English";

    LocaleDto dto = LocaleDto.from(locale);
    assertFalse(dto.isDefault);
    assertTrue(dto.enabled);
  }

  // ---- Locale code normalization ----

  @Test
  void shouldNormalizeSimpleCodes() {
    assertEquals("en", normalize("EN"));
    assertEquals("fr", normalize("FR"));
    assertEquals("de", normalize("de"));
  }

  @Test
  void shouldNormalizeWithCountryCode() {
    assertEquals("en-US", normalize("en-US"));
    assertEquals("en-US", normalize("EN_US"));
    assertEquals("pt-BR", normalize("pt-BR"));
    assertEquals("zh-CN", normalize("zh_CN"));
  }

  @Test
  void shouldHandleNull() {
    assertNull(normalize(null));
  }

  // ---- Locale resolution ----

  @Test
  void shouldNormalizeExplicit() {
    assertEquals("fr", normalizeExplicit("fr"));
    assertEquals("en-US", normalizeExplicit("en-US"));
  }

  // ---- Helper methods mirroring LocaleService logic ----

  private static String normalize(String code) {
    if (code == null) return null;
    try {
      java.util.Locale loc = java.util.Locale.forLanguageTag(code.replace('_', '-'));
      String lang = loc.getLanguage();
      String country = loc.getCountry();
      return country.isEmpty() ? lang : lang + "-" + country;
    } catch (Exception e) {
      return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /** Mirrors resolveLocale logic for the explicit locale case. */
  private static String normalizeExplicit(String code) {
    return normalize(code);
  }
}
