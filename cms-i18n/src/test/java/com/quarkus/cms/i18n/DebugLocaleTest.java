package com.quarkus.cms.i18n;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.i18n.dto.LocaleDto;
import com.quarkus.cms.i18n.model.CmsLocale;
import com.quarkus.cms.i18n.service.LocaleService;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import java.util.List;

import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class DebugLocaleTest {

  @Inject
  LocaleService localeService;

  @Test
  void shouldDebugLocalePersistence() {
    // Create a locale explicitly as default
    LocaleDto created = localeService.createLocale(new LocaleDto("en", "English", true, true));
    System.out.println("=== Created: code=" + created.code + " isDefault=" + created.isDefault);

    // Count locales
    long count = CmsLocale.count();
    System.out.println("=== Locale count: " + count);

    // Try findByCode
    CmsLocale byCode = CmsLocale.findByCode("en");
    System.out.println("=== findByCode(en): " + (byCode != null ? "found code=" + byCode.code + " isDefault=" + byCode.isDefault : "null"));

    // Try findDefault
    CmsLocale def = CmsLocale.findDefault();
    System.out.println("=== findDefault(): " + (def != null ? def.code : "null"));

    // Try find with native query
    CmsLocale hqlResult = (CmsLocale) CmsLocale.find("isDefault = ?1", true).firstResult();
    System.out.println("=== HQL isDefault=true: " + (hqlResult != null ? hqlResult.code : "null"));

    // Trying with list
    List<CmsLocale> allDefaults = CmsLocale.list("isDefault", true);
    System.out.println("=== list(isDefault, true): " + allDefaults.size() + " entries");

    // Try using 'where' clause
    CmsLocale whereResult = (CmsLocale) CmsLocale.find("FROM CmsLocale WHERE isDefault = ?1", true).firstResult();
    System.out.println("=== WHERE isDefault: " + (whereResult != null ? whereResult.code : "null"));

    // List all locales
    List<CmsLocale> all = CmsLocale.listAll();
    for (CmsLocale loc : all) {
      System.out.println("=== All: code=" + loc.code + " isDefault=" + loc.isDefault + " enabled=" + loc.enabled);
    }

    // Verify via service
    assertEquals("en", localeService.getDefaultLocale(), "getDefaultLocale should return 'en'");
    assertEquals("en", CmsLocale.defaultCode(), "defaultCode should return 'en'");

    assertNotNull(byCode, "By code should find the locale");
    assertNotNull(def, "findDefault should find the locale");
  }
}
