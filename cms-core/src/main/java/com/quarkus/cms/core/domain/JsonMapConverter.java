package com.quarkus.cms.core.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

/**
 * JPA {@link AttributeConverter} that serializes {@code Map<String, Object>} to a JSON string
 * for storage in a VARCHAR/CLOB column, and deserializes it back on read.
 *
 * <p>This avoids reliance on Hibernate's JSON format mapper infrastructure, which has
 * compatibility issues with certain database dialects (e.g. SQLite community dialect)
 * and ObjectMapper configuration quirks.
 *
 * <p>Uses Jackson's {@link ObjectMapper} for all serialization/deserialization.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final TypeReference<Map<String, Object>> MAP_TYPE =
      new TypeReference<Map<String, Object>>() {};

  @Override
  public String convertToDatabaseColumn(Map<String, Object> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return "{}";
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize Map to JSON string", e);
    }
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return new HashMap<>();
    }
    try {
      return MAPPER.readValue(dbData, MAP_TYPE);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to deserialize JSON string to Map", e);
    }
  }
}
