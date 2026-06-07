package com.quarkus.cms.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * API token for client-side (non-admin) authentication.
 *
 * <p>Tokens are stored as bcrypt/argon2 hashes. The raw token value is generated on creation and
 * returned to the caller once; it cannot be retrieved afterwards.
 */
@Entity
@Table(
    name = "cms_api_tokens",
    indexes = {@Index(name = "idx_api_tokens_hash", columnList = "token_hash")})
public class CmsApiToken extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  public String name;

  @NotBlank
  @Size(max = 255)
  @Column(name = "token_hash", nullable = false, unique = true, length = 255)
  public String tokenHash;

  @NotBlank
  @Size(max = 50)
  @Column(nullable = false, length = 50)
  public String type = "full-access";

  @Column(columnDefinition = "TEXT")
  public String description;

  @Column(name = "last_used_at")
  public Instant lastUsedAt;

  @Column(name = "expires_at")
  public Instant expiresAt;

  @Column(name = "is_active", nullable = false)
  public boolean isActive = true;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id", foreignKey = @ForeignKey(name = "fk_token_created_by"))
  public CmsUser createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  /** Finds an active token by its hash. */
  public static CmsApiToken findByTokenHash(String tokenHash) {
    return find("tokenHash = ?1 and isActive = true", tokenHash).firstResult();
  }

  /** Lists all active tokens. */
  public static java.util.List<CmsApiToken> findActive() {
    return list("isActive = true");
  }

  /** Lists all tokens created by a given user. */
  public static java.util.List<CmsApiToken> findByCreatedBy(Long userId) {
    return list("createdBy.id", userId);
  }

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
