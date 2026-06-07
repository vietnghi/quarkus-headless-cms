package com.quarkus.cms.graphql;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.util.Map;

/**
 * Custom JSON scalar adapter for SmallRye GraphQL.
 *
 * <p>Handles {@link Map} (dynamic field data) as an opaque JSON value,
 * preventing SmallRye GraphQL from generating invalid empty input types
 * for {@code Map<String, Object>} parameters.
 *
 * <p>The scalar serializes arbitrary objects to their JSON representation
 * as a string, and parses JSON strings back to objects.
 */
public class JsonScalarAdapter {

  public GraphQLScalarType jsonScalar() {
    return GraphQLScalarType.newScalar()
        .name("JSON")
        .description("Arbitrary JSON value — maps to Java Object")
        .coercing(new Coercing<>() {
          @Override
          @SuppressWarnings("rawtypes")
          public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
            if (dataFetcherResult instanceof Map || dataFetcherResult instanceof java.util.List) {
              return dataFetcherResult;
            }
            if (dataFetcherResult instanceof String) {
              return dataFetcherResult;
            }
            return String.valueOf(dataFetcherResult);
          }

          @Override
          public Object parseValue(Object input) throws CoercingParseValueException {
            return input; // passthrough — GraphQL input already parsed
          }

          @Override
          public Object parseLiteral(Object input) throws CoercingParseLiteralException {
            return input; // passthrough — already a parsed object
          }
        })
        .build();
  }
}
