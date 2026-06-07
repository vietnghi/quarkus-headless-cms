package com.quarkus.cms.i18n.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Represents an available locale in the CMS.
 *
 * <p>Locales are managed by administrators and define which languages the content
 * can be translated into. Each locale has a code (e.g., "en", "fr", "de"),
 * a display name, and an enabled/disabled state. Exactly one locale
 * can be marked as the default locale.
 */
@Entity
@Table(
    name = "cms_locales",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_locales_code", columnNames = "code")
    })
public class CmsLocale extends PanacheEntityBase {

  @Id
  @Column(length = 10, nullable = false)
  public String code;

  @Column(name = "display_name", nullable = false, length = 100)
  public String displayName;

  @Column(name = "is_default", nullable = false)
  public Boolean isDefault = false;

  @Column(nullable = false)
  public Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  // ---- Lifecycle ----

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  // ---- Finders ----

  /** Finds a locale by its code. */
  public static CmsLocale findByCode(String code) {
    return find("code", code).firstResult();
  }

  /** Returns the default locale, or {@code null} if none is configured. */
  public static CmsLocale findDefault() {
    return find("isDefault", true).firstResult();
  }

  /** Returns all enabled locales. */
  public static java.util.List<CmsLocale> findEnabled() {
    return list("enabled", true);
  }

  /** Returns the default locale code, falling back to "en". */
  public static String defaultCode() {
    CmsLocale def = findDefault();
    return def != null ? def.code : "en";
  }
}
