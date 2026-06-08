package com.quarkus.cms.core.query;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Database-agnostic JSONB function adapter.
 *
 * <p>Centralizes the SQL/HQL expressions used to extract text values from JSON(B) columns.
 * The appropriate SQL syntax is selected at runtime based on the configured
 * {@code quarkus.datasource.db-kind} property.
 *
 * <p>Supported databases:
 * <ul>
 *   <li><b>postgresql</b> — uses {@code FUNCTION('jsonb_extract_path_text', ...)} (native PostgreSQL)</li>
 *   <li><b>sqlite</b> — uses {@code json_extract()} native function (built-in in SQLite 3.38+)</li>
 * </ul>
 *
 * <p>Replaces the earlier H2-specific approach with a database-agnostic
 * strategy usable by all modules.
 */
public final class JsonbFunctions {

  private static final String DB_KIND;

  static {
    DB_KIND = detectDbKind();
  }

  private JsonbFunctions() {}

  /**
   * Returns the SQL/HQL expression for extracting a text value from the {@code data}
   * JSON(B) column by top-level field name.
   *
   * <p>The returned expression can be used directly in HQL or native SQL queries.
   * The field name is safely escaped (single quotes are doubled).
   *
   * @param field the top-level JSON/B field name
   * @return a complete SQL expression, e.g. {@code json_extract(data, '$.title')}
   */
  public static String extractPathText(String field) {
    String escapedField = field.replace("'", "''");
    return switch (DB_KIND) {
      case "sqlite" -> "json_extract(data, '$." + escapedField + "')";
      case "postgresql" ->
          "FUNCTION('jsonb_extract_path_text', data, '" + escapedField + "')";
      default ->
          // Fallback: assume PostgreSQL-compatible (most common test scenario)
          "FUNCTION('jsonb_extract_path_text', data, '" + escapedField + "')";
    };
  }

  /**
   * Returns the SQL function invocation for jsonb_extract_path_text that can be
   * embedded in ORDER BY clauses or other HQL expressions where the column name
   * is needed as a standalone fragment.
   *
   * <p>For SQLite this returns a native {@code json_extract()} call. For PostgreSQL
   * it delegates to the registered function.
   */
  public static String extractPathTextFunc(String field) {
    return extractPathText(field);
  }

  /**
   * Detects the current database kind from MicroProfile Config.
   *
   * @return the db-kind string ("postgresql", "sqlite", or "postgresql" as fallback)
   */
  private static String detectDbKind() {
    try {
      return ConfigProvider.getConfig()
          .getOptionalValue("quarkus.datasource.db-kind", String.class)
          .orElse("postgresql")
          .toLowerCase();
    } catch (Exception e) {
      // If no config available (e.g. outside Quarkus), assume PostgreSQL
      return "postgresql";
    }
  }
}
