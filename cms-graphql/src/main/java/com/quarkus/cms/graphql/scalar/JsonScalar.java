package com.quarkus.cms.graphql.scalar;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;
import java.util.Map;

/**
 * Custom JSON scalar for GraphQL, allowing arbitrary JSON values to flow through the schema
 * without predefined GraphQL types.
 *
 * <p>Required for the headless CMS where content-type fields are dynamically defined at runtime.
 * Accepts {@code Map}, {@code List}, {@code String}, {@code Number}, and {@code Boolean} values.
 */
@ApplicationScoped
public class JsonScalar {

  public static final String NAME = "JSON";

  private static final GraphQLScalarType INSTANCE =
      GraphQLScalarType.newScalar()
          .name(NAME)
          .description("Arbitrary JSON value (object, array, string, number, boolean, or null)")
          .coercing(new JsonCoercing())
          .build();

  @Produces
  @ApplicationScoped
  public GraphQLScalarType jsonScalar() {
    return INSTANCE;
  }

  /** Internal Coercing implementation for the JSON scalar. */
  private static class JsonCoercing implements Coercing<Object, Object> {

    @Override
    @SuppressWarnings("unchecked")
    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
      if (dataFetcherResult == null) {
        return null;
      }
      if (dataFetcherResult instanceof Map || dataFetcherResult instanceof List
          || dataFetcherResult instanceof String || dataFetcherResult instanceof Number
          || dataFetcherResult instanceof Boolean) {
        return dataFetcherResult;
      }
      return dataFetcherResult.toString();
    }

    @Override
    public Object parseValue(Object input) throws CoercingParseValueException {
      return input;
    }

    @Override
    public Object parseLiteral(Object input) throws CoercingParseLiteralException {
      return input;
    }
  }
}
