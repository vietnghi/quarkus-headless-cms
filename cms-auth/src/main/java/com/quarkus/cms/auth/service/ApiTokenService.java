package com.quarkus.cms.auth.service;

import com.quarkus.cms.auth.entity.CmsApiToken;
import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.repository.CmsApiTokenRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Service for managing API tokens (client-side authentication).
 *
 * <p>API tokens are used for non-admin API access. Each token has a type:
 *
 * <ul>
 *   <li>{@code full-access} — unrestricted access to all content types
 *   <li>{@code custom} — scoped to specific content types and actions
 * </ul>
 */
@ApplicationScoped
public class ApiTokenService {

  @Inject CmsApiTokenRepository apiTokenRepository;

  /**
   * Creates a new API token and returns the raw token value (only shown once).
   *
   * @param name human-readable name for the token
   * @param type "full-access" or "custom"
   * @param description optional description
   * @param createdBy the admin user creating the token
   * @param expiresAt optional expiry timestamp
   * @return the raw token value (must be stored by the caller — not retrievable later)
   */
  @Transactional
  public String createToken(
      String name,
      String type,
      String description,
      CmsUser createdBy,
      Instant expiresAt) {

    String rawToken =
        apiTokenRepository.create(name, type, description, createdBy.id, expiresAt);
    Log.infof("Created API token '%s' (type=%s) by user %s", name, type, createdBy.username);
    return rawToken;
  }

  /** Lists all active API tokens. */
  public List<CmsApiToken> listActive() {
    return apiTokenRepository.listActive();
  }

  /** Lists API tokens created by a specific user. */
  public List<CmsApiToken> listByCreator(Long userId) {
    return apiTokenRepository.listByCreator(userId);
  }

  /** Validates a raw token against stored hashes. Returns the token entity if valid. */
  public CmsApiToken validateRawToken(String rawToken) {
    return apiTokenRepository.validate(rawToken);
  }

  /** Records token usage (updates lastUsedAt). */
  @Transactional
  public void recordUsage(Long tokenId) {
    apiTokenRepository.recordUsage(tokenId);
  }

  /** Revokes (deactivates) a token. */
  @Transactional
  public void revoke(Long tokenId) {
    apiTokenRepository.revoke(tokenId);
  }

  /** Permanently deletes a token. */
  @Transactional
  public void delete(Long tokenId) {
    apiTokenRepository.delete(tokenId);
  }
}
