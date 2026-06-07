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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A permission grant attached to a role.
 *
 * <p>Each permission declares an action (e.g. "api::article.article.create"), an optional subject,
 * and optional JSON constraints (fields, conditions). This mirrors Strapi's CASL-based permission
 * model.
 */
@Entity
@Table(
    name = "cms_permissions",
    indexes = {
      @Index(name = "idx_permissions_role", columnList = "role_id"),
      @Index(name = "idx_permissions_action", columnList = "action")
    })
public class CmsPermission extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  public String action;

  @Size(max = 255)
  @Column(length = 255)
  public String subject;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fields", columnDefinition = "jsonb")
  public java.util.List<String> fields;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "conditions", columnDefinition = "jsonb")
  public Map<String, Object> conditions;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_permission_role"))
  public CmsRole role;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  /** Finds all permissions for a given role. */
  public static java.util.List<CmsPermission> findByRole(Long roleId) {
    return list("role.id", roleId);
  }

  /** Finds all permissions that grant a specific action. */
  public static java.util.List<CmsPermission> findByAction(String action) {
    return list("action", action);
  }

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }
}
