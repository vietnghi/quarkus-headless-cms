package com.quarkus.cms.auth.security;

import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.service.TokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Security utility for creating authenticated JWT tokens.
 *
 * <p>Thin wrapper around {@link TokenService} that provides a simplified API for generating tokens
 * from authenticated users. Used primarily by auth resources to produce the final JWT response.
 */
@ApplicationScoped
public class CmsJwtGenerator {

  @Inject TokenService tokenService;

  /**
   * Generates a complete auth response with access and refresh tokens.
   *
   * @param user the authenticated user
   * @return a token pair
   */
  public TokenPair generateTokens(CmsUser user) {
    String accessToken = tokenService.generateAccessToken(user);
    String refreshToken = tokenService.generateRefreshToken(user);
    return new TokenPair(accessToken, refreshToken);
  }

  /** A pair of access + refresh tokens. */
  public record TokenPair(String accessToken, String refreshToken) {}

  /**
   * Generates only an access token.
   *
   * @param user the authenticated user
   * @return the JWT access token
   */
  public String generateAccessToken(CmsUser user) {
    return tokenService.generateAccessToken(user);
  }
}
