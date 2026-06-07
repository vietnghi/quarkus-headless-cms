/**
 * Internationalization (i18n) module for multi-locale content management.
 *
 * <p>Provides locale management (CRUD for available locales), content localization
 * (creating and querying translations), locale resolution with Accept-Language
 * header support, and per-field i18n configuration via the content-type schema.
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@link com.quarkus.cms.i18n.model.CmsLocale} — persistent locale entity</li>
 *   <li>{@link com.quarkus.cms.i18n.service.LocaleService} — locale CRUD and resolution</li>
 *   <li>{@link com.quarkus.cms.i18n.service.I18nService} — content localization operations</li>
 *   <li>{@link com.quarkus.cms.i18n.resource.AdminI18nResource} — admin REST API</li>
 * </ul>
 */
package com.quarkus.cms.i18n;
