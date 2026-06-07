package com.quarkus.cms.graphql.service;

import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.service.AuthService;
import com.quarkus.cms.auth.service.TokenService;
import com.quarkus.cms.graphql.model.AuthPayload;
import com.quarkus.cms.graphql.model.AuthPayload.UserInfo;

import io.quarkus.logging.Log;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication service for the GraphQL API.
 *
 * <p>Supports login (via {@link AuthService}), current user info retrieval from the security
 * context, and direct JWT verification for API token support.
 */
@ApplicationScoped
public class GraphQLAuthService {

  @Inject AuthService authService;

  @Inject TokenService tokenService;

  @Inject JWTParser jwtParser;

  /**
   * Authenticates a user by identifier (username or email) and password.
   *
   * @param identifier username or email
   * @param password   the password
   * @return auth payload with JWT and user info
   * @throws SecurityException if credentials are invalid
   */
  public AuthPayload login(String identifier, String password) {
    AuthService.LoginResult result = authService.login(identifier, password);
    CmsUser user = result.user();

    Set<String> roleCodes =
        user.roles.stream().map(r -> r.code).collect(Collectors.toSet());

    UserInfo userInfo = new UserInfo(user.id, user.username, user.email, roleCodes);
    return new AuthPayload(result.accessToken(), userInfo);
  }

  /**
   * Verifies a JWT token and extracts the user's roles.
   *
   * @param token the JWT access token
   * @return the parsed JSON Web Token with claims
   * @throws ParseException if the token is invalid or expired
   */
  public JsonWebToken verifyToken(String token) throws ParseException {
    return jwtParser.parse(token);
  }

  /**
   * Extracts user info from a verified JWT.
   *
   * @param jwt the parsed JSON Web Token
   * @return user info, or null if not resolvable
   */
  public UserInfo userInfoFromToken(JsonWebToken jwt) {
    if (jwt == null) return null;
    Long userId = Long.valueOf(jwt.getSubject());
    String username = jwt.getClaim("preferred_username");
    String email = jwt.getClaim("email");
    Set<String> roles = jwt.getClaim("roles");

    return new UserInfo(userId, username, email, roles);
  }
}
