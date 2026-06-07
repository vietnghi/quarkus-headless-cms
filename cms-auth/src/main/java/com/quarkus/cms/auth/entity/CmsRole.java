package com.quarkus.cms.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * RBAC role entity.
 *
 * <p>Roles are named collections of permissions (actions on subjects). Users are assigned roles,
 * and each role carries a set of permission grants.
 */
@Entity
@Table(
    name = "cms_roles",
    indexes = {@Index(name = "idx_roles_code", columnList = "code")})
public class CmsRole extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, unique = true, length = 100)
  public String code;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  public String name;

  @Column(columnDefinition = "TEXT")
  public String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  @OneToMany(
      mappedBy = "role",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  public List<CmsPermission> permissions = new ArrayList<>();

  /** Finds a role by its code. */
  public static CmsRole findByCode(String code) {
    return find("code", code).firstResult();
  }

  /** Lists all roles. */
  public static java.util.List<CmsRole> listAllRoles() {
    return listAll();
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
