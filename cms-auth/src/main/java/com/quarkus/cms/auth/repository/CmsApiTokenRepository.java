package com.quarkus.cms.auth.repository;

import com.quarkus.cms.auth.entity.CmsApiToken;
import com.quarkus.cms.auth.entity.CmsUser;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Repository for {@link CmsApiToken} client API token operations.
 *
 * <p>Generates cryptographically random tokens, stores only the bcrypt hash, and returns the raw
 * token value once at creation time.
 */
@ApplicationScoped
public class CmsApiTokenRepository {

  private static final SecureRandom RANDOM = new SecureRandom();

  /** Creates a new API token. Returns the raw token (only shown once). */
  @Transactional
  public String create(
      String name, String type, String description, Long createdById, Instant expiresAt) {
    String rawToken = generateRawToken();

    CmsApiToken token = new CmsApiToken();
    token.name = name;
    token.tokenHash = BcryptUtil.bcryptHash(rawToken);
    token.type = type != null ? type : "full-access";
    token.description = description;
    token.expiresAt = expiresAt;
    if (createdById != null) {
      token.createdBy = CmsUser.findById(createdById);
    }
    token.persist();

    Log.infof("Created API token: %s", name);
    return rawToken;
  }

  /** Lists all active tokens. */
  public List<CmsApiToken> listActive() {
    return CmsApiToken.findActive();
  }

  /** Lists tokens created by a specific user. */
  public List<CmsApiToken> listByCreator(Long userId) {
    return CmsApiToken.findByCreatedBy(userId);
  }

  /** Validates a raw token against stored hashes. Returns the token entity if valid. */
  public CmsApiToken validate(String rawToken) {
    List<CmsApiToken> active = CmsApiToken.findActive();
    for (CmsApiToken token : active) {
      if (token.expiresAt != null && token.expiresAt.isBefore(Instant.now())) {
        continue;
      }
      if (BcryptUtil.matches(rawToken, token.tokenHash)) {
        return token;
      }
    }
    return null;
  }

  /** Records usage of a token (last used timestamp). */
  @Transactional
  public void recordUsage(Long tokenId) {
    CmsApiToken token = CmsApiToken.findById(tokenId);
    if (token != null) {
      token.lastUsedAt = Instant.now();
      token.persist();
    }
  }

  /** Revokes (deactivates) a token. */
  @Transactional
  public void revoke(Long tokenId) {
    CmsApiToken token = CmsApiToken.findById(tokenId);
    if (token == null) {
      throw new IllegalArgumentException("API token not found: " + tokenId);
    }
    token.isActive = false;
    token.persist();
    Log.infof("Revoked API token: %d", tokenId);
  }

  /** Deletes a token permanently. */
  @Transactional
  public void delete(Long tokenId) {
    CmsApiToken.deleteById(tokenId);
    Log.infof("Deleted API token: %d", tokenId);
  }

  private String generateRawToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
