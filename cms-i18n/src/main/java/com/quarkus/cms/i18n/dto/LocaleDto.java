package com.quarkus.cms.i18n.dto;

import com.quarkus.cms.i18n.model.CmsLocale;

/**
 * DTO for locale data returned by the admin API.
 */
public class LocaleDto {

  public String code;
  public String displayName;
  public boolean isDefault;
  public boolean enabled;

  public LocaleDto() {}

  public LocaleDto(String code, String displayName, boolean isDefault, boolean enabled) {
    this.code = code;
    this.displayName = displayName;
    this.isDefault = isDefault;
    this.enabled = enabled;
  }

  /** Creates a DTO from a CmsLocale entity. */
  public static LocaleDto from(CmsLocale locale) {
    return new LocaleDto(locale.code, locale.displayName,
        locale.isDefault != null && locale.isDefault,
        locale.enabled != null && locale.enabled);
  }

  /** Creates a CmsLocale entity from this DTO. */
  public CmsLocale toEntity() {
    CmsLocale loc = new CmsLocale();
    loc.code = this.code;
    loc.displayName = this.displayName;
    loc.isDefault = this.isDefault;
    loc.enabled = this.enabled;
    return loc;
  }
}
