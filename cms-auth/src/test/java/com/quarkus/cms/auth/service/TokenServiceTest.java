package com.quarkus.cms.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TokenService JWT generation.
 */
@QuarkusTest
class TokenServiceTest {

  @Inject TokenService tokenService;

  private CmsUser testUser;

  @BeforeEach
  void setUp() {
    testUser = new CmsUser();
    testUser.id = 1L;
    testUser.username = "testuser";
    testUser.email = "test@example.com";

    CmsRole adminRole = new CmsRole();
    adminRole.id = 1L;
    adminRole.code = "Administrator";
    adminRole.name = "Administrator";

    CmsRole editorRole = new CmsRole();
    editorRole.id = 2L;
    editorRole.code = "Editor";
    editorRole.name = "Editor";

    testUser.roles = new java.util.HashSet<>(Set.of(adminRole, editorRole));
  }

  @Test
  void shouldGenerateAccessTokenWithClaims() {
    String token = tokenService.generateAccessToken(testUser);

    assertNotNull(token);
    assertFalse(token.isEmpty());
    String[] parts = token.split("\\.");
    assertEquals(3, parts.length);
  }

  @Test
  void shouldGenerateRefreshToken() {
    String token = tokenService.generateRefreshToken(testUser);

    assertNotNull(token);
    assertFalse(token.isEmpty());
    String[] parts = token.split("\\.");
    assertEquals(3, parts.length);
  }

  @Test
  void shouldGeneratePasswordResetToken() {
    String token = tokenService.generatePasswordResetToken("user@example.com", "abc123");

    assertNotNull(token);
    assertFalse(token.isEmpty());
  }

  @Test
  void shouldGenerateEmailConfirmationToken() {
    String token = tokenService.generateEmailConfirmationToken("user@example.com", "abc123");

    assertNotNull(token);
    assertFalse(token.isEmpty());
  }

  @Test
  void shouldGenerateApiTokenJwt() {
    String token =
        tokenService.generateApiTokenJwt(
            "my-token", 1L, "full-access", Set.of("api::article.article.read"));

    assertNotNull(token);
    assertFalse(token.isEmpty());
  }

  @Test
  void shouldGenerateApiTokenJwtWithoutPermissions() {
    String token = tokenService.generateApiTokenJwt("readonly-token", 2L, "custom", null);

    assertNotNull(token);
    assertFalse(token.isEmpty());
  }

  @Test
  void accessTokenLifetimeIs30Minutes() {
    assertEquals(java.time.Duration.ofMinutes(30), TokenService.ACCESS_TOKEN_LIFETIME);
  }

  @Test
  void refreshTokenLifetimeIs7Days() {
    assertEquals(java.time.Duration.ofDays(7), TokenService.REFRESH_TOKEN_LIFETIME);
  }
}
