package com.quarkus.cms.example;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile that enables GraphQL and JWT for integration tests.
 *
 * <p>Overrides the default test config which has {@code mp.graphql.enabled=false}
 * to work around a pre-existing schema generation issue. Also configures the
 * JWT signing key and issuer so {@code @RolesAllowed} mutations work.
 */
public class GraphQLTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "mp.graphql.enabled", "true",
        "smallrye.jwt.sign.key",
            "{\"kty\":\"oct\",\"k\":\"mt_T0W9vhrC0Dcn41E6TowYei4iB5F3L-ikDFsaseew\"}",
        "mp.jwt.verify.issuer", "quarkus-headless-cms",
        "quarkus.cms.auth.api-tokens.enabled", "false"
    );
  }
}
