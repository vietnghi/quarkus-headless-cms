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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Admin user entity for the CMS.
 *
 * <p>Stores administrator credentials (bcrypt-hashed password) and profile metadata. Users are
 * assigned roles which carry permission grants.
 */
@Entity
@Table(
    name = "cms_users",
    indexes = {
      @Index(name = "idx_users_username", columnList = "username"),
      @Index(name = "idx_users_email", columnList = "email")
    })
public class CmsUser extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, unique = true, length = 100)
  public String username;

  @NotBlank
  @Email
  @Size(max = 255)
  @Column(nullable = false, unique = true, length = 255)
  public String email;

  @NotBlank
  @Size(max = 255)
  @Column(name = "password_hash", nullable = false, length = 255)
  public String passwordHash;

  @Size(max = 100)
  @Column(name = "first_name", length = 100)
  public String firstName;

  @Size(max = 100)
  @Column(name = "last_name", length = 100)
  public String lastName;

  @Column(name = "is_active", nullable = false)
  public boolean isActive = true;

  @Column(name = "is_blocked", nullable = false)
  public boolean isBlocked = false;

  @Size(max = 10)
  @Column(name = "preferred_locale", length = 10)
  public String preferredLocale = "en";

  @Column(name = "last_login_at")
  public Instant lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  @ManyToMany(
      fetch = FetchType.LAZY,
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinTable(
      name = "cms_user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  public Set<CmsRole> roles = new HashSet<>();

  /** Finds a user by username (active only). */
  public static CmsUser findByUsername(String username) {
    return find("username = ?1 and isActive = true", username).firstResult();
  }

  /** Finds a user by email (active only). */
  public static CmsUser findByEmail(String email) {
    return find("email = ?1 and isActive = true", email).firstResult();
  }

  /** Checks if a username exists. */
  public static boolean existsByUsername(String username) {
    return count("username", username) > 0;
  }

  /** Lists all active users. */
  public static java.util.List<CmsUser> findActive() {
    return list("isActive = true");
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
