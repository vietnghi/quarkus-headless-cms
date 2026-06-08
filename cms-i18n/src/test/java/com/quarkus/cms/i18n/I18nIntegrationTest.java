package com.quarkus.cms.i18n;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.i18n.dto.LocaleDto;
import com.quarkus.cms.i18n.model.CmsLocale;
import com.quarkus.cms.i18n.service.I18nService;
import com.quarkus.cms.i18n.service.LocaleService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the i18n module.
 *
 * <p>Tests the full LocaleService and I18nService with a real SQLite database.
 */
@QuarkusTest
@Transactional
class I18nIntegrationTest {

  @Inject
  LocaleService localeService;

  @Inject
  I18nService i18nService;

  @Inject
  CmsEntryRepository entryRepository;

  @BeforeEach
  @Transactional
  void cleanUp() {
    CmsLocale.deleteAll();
    CmsEntry.deleteAll();
  }

  // ---- Locale CRUD ----

  @Test
  void shouldCreateAndListLocales() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));
    localeService.createLocale(new LocaleDto("de", "German", false, true));

    List<LocaleDto> locales = localeService.listLocales();
    assertEquals(3, locales.size());
  }

  @Test
  void shouldMakeFirstLocaleDefault() {
    LocaleDto created = localeService.createLocale(new LocaleDto("fr", "French", false, true));
    assertTrue(created.isDefault, "First locale should become default");
  }

  @Test
  void shouldGetDefaultLocale() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    assertEquals("en", localeService.getDefaultLocale());
  }

  @Test
  void shouldUpdateLocale() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));

    LocaleDto updated = localeService.updateLocale("en",
        new LocaleDto("en", "English (US)", true, true));
    assertEquals("English (US)", updated.displayName);
  }

  @Test
  void shouldThrowOnDuplicateLocale() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    assertThrows(IllegalArgumentException.class,
        () -> localeService.createLocale(new LocaleDto("en", "English", false, true)));
  }

  @Test
  void shouldThrowOnDeleteDefault() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));

    assertThrows(IllegalStateException.class,
        () -> localeService.deleteLocale("en"));
  }

  @Test
  void shouldDeleteNonDefaultLocale() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));
    assertEquals(2, localeService.listLocales().size());
  }

  // ---- I18nService ----

  @Test
  void shouldCreateLocalization() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));

    // Create an entry in English
    CmsEntry enEntry = entryRepository.create("api::article.article",
        Map.of("title", "Hello World", "content", "Great article"), "en");

    // Create French localization
    CmsEntry frEntry = i18nService.createLocalization(
        enEntry.documentId, "en", "fr",
        Map.of("title", "Bonjour le Monde"), 1L);

    assertNotNull(frEntry);
    assertEquals("fr", frEntry.locale);
    assertEquals(enEntry.documentId, frEntry.documentId);

    // Verify French data has localized fields + non-localized from source
    assertEquals("Bonjour le Monde", frEntry.data.get("title"));
    assertEquals("Great article", frEntry.data.get("content"));
  }

  @Test
  void shouldListLocalizations() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));
    localeService.createLocale(new LocaleDto("de", "German", false, true));

    CmsEntry enEntry = entryRepository.create("api::article.article",
        Map.of("title", "Hello"), "en");

    i18nService.createLocalization(enEntry.documentId, "en", "fr", Map.of("title", "Bonjour"), 1L);
    i18nService.createLocalization(enEntry.documentId, "en", "de", Map.of("title", "Hallo"), 1L);

    var localizations = i18nService.getLocalizations(enEntry.documentId);
    assertEquals(3, localizations.size());
    assertTrue(localizations.containsKey("en"));
    assertTrue(localizations.containsKey("fr"));
    assertTrue(localizations.containsKey("de"));
  }

  @Test
  void shouldThrowOnDuplicateLocalization() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));

    CmsEntry enEntry = entryRepository.create("api::article.article",
        Map.of("title", "Hello"), "en");

    i18nService.createLocalization(enEntry.documentId, "en", "fr", Map.of("title", "Bonjour"), 1L);

    assertThrows(IllegalArgumentException.class,
        () -> i18nService.createLocalization(enEntry.documentId, "en", "fr", Map.of(), 1L));
  }

  @Test
  void shouldGetWithFallback() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));

    CmsEntry enEntry = entryRepository.create("api::article.article",
        Map.of("title", "Hello"), "en");

    // Requesting non-existent locale should fallback to default
    CmsEntry fallback = i18nService.getWithFallback(enEntry.documentId, "de", null);
    assertNotNull(fallback);
    assertEquals("en", fallback.locale);
  }

  @Test
  void shouldGetLocalizationsSummary() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));

    CmsEntry enEntry = entryRepository.create("api::article.article",
        Map.of("title", "Hello"), "en");

    i18nService.createLocalization(enEntry.documentId, "en", "fr", Map.of("title", "Bonjour"), 1L);

    var summary = i18nService.getLocalizationsSummary(enEntry.documentId);
    assertEquals(2, summary.size());
  }

  @Test
  void shouldReturnEmptyForNonexistentDocument() {
    var localizations = i18nService.getLocalizations("nonexistent-doc-id");
    assertTrue(localizations.isEmpty());
  }

  @Test
  void shouldResolveExplicitLocale() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));

    String resolved = localeService.resolveLocale("fr", null);
    assertEquals("fr", resolved);
  }

  @Test
  void shouldResolveDefaultLocale() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));

    String resolved = localeService.resolveLocale(null, null);
    assertEquals("en", resolved);
  }

  @Test
  void shouldGetEnabledLocaleCodes() {
    localeService.createLocale(new LocaleDto("en", "English", true, true));
    localeService.createLocale(new LocaleDto("fr", "French", false, true));
    localeService.createLocale(new LocaleDto("de", "German", false, false)); // disabled

    List<String> enabled = localeService.getEnabledLocaleCodes();
    assertEquals(2, enabled.size());
    assertTrue(enabled.contains("en"));
    assertTrue(enabled.contains("fr"));
    assertFalse(enabled.contains("de"));
  }
}
