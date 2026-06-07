package com.quarkus.cms.media.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Media library folder for organizing uploaded files.
 */
@Entity
@Table(
    name = "cms_folders",
    indexes = {
      @Index(name = "idx_folders_parent_id", columnList = "parent_id"),
      @Index(name = "idx_folders_path", columnList = "path")
    })
public class CmsFolder extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** Folder display name. */
  @NotBlank
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  public String name;

  /** Full path (e.g., "/images/banners"). */
  @NotBlank
  @Size(max = 1024)
  @Column(nullable = false, length = 1024)
  public String path;

  /** Parent folder ID (null for root-level folders). */
  @Column(name = "parent_id")
  public Long parentId;

  /** User ID of the folder creator. */
  @Column(name = "created_by_id")
  public Long createdById;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  // ---- Named queries ----

  /** Finds all child folders of a given parent. */
  public static List<CmsFolder> findByParent(Long parentId) {
    if (parentId == null) {
      return list("parentId is null order by name");
    }
    return list("parentId = ?1 order by name", parentId);
  }

  /** Finds a folder by its path. */
  public static CmsFolder findByPath(String path) {
    return find("path = ?1", path).firstResult();
  }

  /** Checks if a folder path already exists. */
  public static boolean existsByPath(String path) {
    return count("path", path) > 0;
  }

  // ---- Lifecycle ----

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
