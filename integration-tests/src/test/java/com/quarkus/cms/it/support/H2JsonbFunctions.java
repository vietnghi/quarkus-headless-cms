package com.quarkus.cms.it.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * H2-compatible implementations of PostgreSQL jsonb functions for integration testing.
 *
 * <p>H2 does not have a native {@code jsonb_extract_path_text()} function, even in PostgreSQL
 * compatibility mode. This class provides static methods registered as H2 SQL aliases via the
 * {@code INIT} parameter in the JDBC URL so that HQL queries using
 * {@code FUNCTION('jsonb_extract_path_text', data, 'field')} work correctly during @QuarkusTest
 * runs that use H2 instead of PostgreSQL.
 */
public final class H2JsonbFunctions {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private H2JsonbFunctions() {}

  /**
   * Extracts a text value from a JSON(B) string by field name.
   *
   * <p>Maps to PostgreSQL's {@code jsonb_extract_path_text(jsonb, VARIADIC text[])}. Only the
   * single-field variant is supported (the only variant used by CmsQueryBuilder).
   *
   * @param jsonb  the JSON(B) column value as a string
   * @param field  the top-level field name to extract
   * @return the field's text value, or null if the field is missing
   */
  public static String extractPathText(String jsonb, String field) {
    if (jsonb == null || field == null) {
      return null;
    }
    try {
      JsonNode node = MAPPER.readTree(jsonb);
      JsonNode value = node.get(field);
      if (value == null) {
        return null;
      }
      if (value.isTextual()) {
        return value.asText();
      }
      return value.asText();
    } catch (Exception e) {
      return null;
    }
  }
}
