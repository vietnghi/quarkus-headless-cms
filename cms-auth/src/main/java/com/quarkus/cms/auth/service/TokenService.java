package com.quarkus.cms.auth.service;

import com.quarkus.cms.auth.entity.CmsUser;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.jwt.Claims;

/**
 * JWT token generation service.
 *
 * <p>Uses SmallRye JWT to create signed access and refresh tokens. Access tokens carry user
 * identity and role claims; refresh tokens are long-lived and allow token rotation.
 */
@ApplicationScoped
public class TokenService {

  /** Access token lifetime: 30 minutes. */
  public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(30);

  /** Refresh token lifetime: 7 days. */
  public static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(7);

  /** Token issuer name. */
  private static final String ISSUER = "quarkus-headless-cms";

  /**
   * Generates an access token (JWT) for the given user.
   *
   * <p>The token carries standard claims (sub, iss, iat, exp) plus custom claims for roles obtained
   * through role membership.
   */
  public String generateAccessToken(CmsUser user) {
    Instant now = Instant.now();
    Set<String> roleCodes = user.roles.stream().map(r -> r.code).collect(Collectors.toSet());

    return Jwt.issuer(ISSUER)
        .subject(String.valueOf(user.id))
        .claim(Claims.preferred_username.name(), user.username)
        .claim(Claims.email.name(), user.email)
        .claim("roles", roleCodes)
        .issuedAt(now)
        .expiresAt(now.plus(ACCESS_TOKEN_LIFETIME))
        .sign();
  }

  /**
   * Generates a refresh token for the given user.
   *
   * <p>Refresh tokens carry minimal claims and a longer lifetime. They are used to obtain fresh
   * access tokens without re-authentication.
   */
  public String generateRefreshToken(CmsUser user) {
    Instant now = Instant.now();

    return Jwt.issuer(ISSUER)
        .subject(String.valueOf(user.id))
        .claim("type", "refresh")
        .issuedAt(now)
        .expiresAt(now.plus(REFRESH_TOKEN_LIFETIME))
        .sign();
  }

  /**
   * Generates a password reset token scoped to a user's email.
   *
   * <p>Short-lived (15 minutes) and includes a unique nonce to prevent replay.
   */
  public String generatePasswordResetToken(String email, String nonce) {
    Instant now = Instant.now();

    return Jwt.issuer(ISSUER)
        .subject(email)
        .claim("type", "password-reset")
        .claim("nonce", nonce)
        .issuedAt(now)
        .expiresAt(now.plus(Duration.ofMinutes(15)))
        .sign();
  }

  /**
   * Generates an email confirmation token for a new user.
   *
   * <p>Valid for 24 hours. Carries the user's email as subject.
   */
  public String generateEmailConfirmationToken(String email, String nonce) {
    Instant now = Instant.now();

    return Jwt.issuer(ISSUER)
        .subject(email)
        .claim("type", "email-confirmation")
        .claim("nonce", nonce)
        .issuedAt(now)
        .expiresAt(now.plus(Duration.ofHours(24)))
        .sign();
  }

  /**
   * Generates an API token JWT for client-side (non-admin) authentication.
   *
   * <p>This token carries the API token name as subject, a type claim ("api-token"), and optional
   * permission claims for custom scoped tokens.
   */
  public String generateApiTokenJwt(
      String tokenName, Long tokenId, String tokenType, Set<String> permissions) {
    Instant now = Instant.now();

    var builder =
        Jwt.issuer(ISSUER)
            .subject("api-token:" + tokenName)
            .claim("token_id", tokenId)
            .claim("type", "api-token")
            .claim("token_type", tokenType)
            .issuedAt(now);

    if (permissions != null && !permissions.isEmpty()) {
      builder.claim("permissions", permissions);
    }

    return builder.sign();
  }
}
