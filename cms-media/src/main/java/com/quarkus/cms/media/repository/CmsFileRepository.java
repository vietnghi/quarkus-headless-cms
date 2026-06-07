package com.quarkus.cms.media.repository;

import com.quarkus.cms.media.entity.CmsFile;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Repository for {@link CmsFile} media file operations.
 *
 * <p>Provides CRUD operations and search functionality for files in the media library.
 */
@ApplicationScoped
public class CmsFileRepository {

  /** Persists a new file record. */
  @Transactional
  public CmsFile create(CmsFile file) {
    file.persist();
    Log.infof("Created media file: %s (id=%d, provider=%s)", file.name, file.id, file.provider);
    return file;
  }

  /** Finds a file by ID. */
  public CmsFile findById(Long id) {
    return CmsFile.findById(id);
  }

  /** Lists all files, newest first. */
  public List<CmsFile> listAll() {
    return CmsFile.listAll();
  }

  /** Lists files with pagination. */
  public List<CmsFile> listPaged(int page, int pageSize) {
    return CmsFile.findAll().page(page, pageSize).list();
  }

  /** Counts total files. */
  public long count() {
    return CmsFile.count();
  }

  /** Finds files in a specific folder. */
  public List<CmsFile> findByFolder(Long folderId) {
    return CmsFile.findByFolder(folderId);
  }

  /** Searches files by name (case-insensitive partial match). */
  public List<CmsFile> searchByName(String query) {
    return CmsFile.searchByName(query);
  }

  /** Finds files by MIME type prefix (e.g., "image/"). */
  public List<CmsFile> findByMimeTypePrefix(String prefix) {
    return CmsFile.findByMimeTypePrefix(prefix);
  }

  /** Finds a file by its content hash (for duplicate detection). */
  public CmsFile findByHash(String hash) {
    return CmsFile.findByHash(hash);
  }

  /** Updates an existing file record. */
  @Transactional
  public CmsFile update(CmsFile file) {
    CmsFile existing = CmsFile.findById(file.id);
    if (existing == null) {
      throw new IllegalArgumentException("File not found: " + file.id);
    }
    existing.name = file.name;
    existing.altText = file.altText;
    existing.caption = file.caption;
    existing.folderId = file.folderId;
    existing.folderPath = file.folderPath;
    existing.relatedContentType = file.relatedContentType;
    existing.persist();
    Log.infof("Updated media file: %d", file.id);
    return existing;
  }

  /**
   * Replaces a file's content with new data (new file upload replacing existing).
   * The storage provider is responsible for cleaning up the old file.
   */
  @Transactional
  public CmsFile replaceContent(
      Long fileId, String name, String hash, String ext, String mimeType, long size,
      String url, String storageKey, String provider) {
    CmsFile existing = CmsFile.findById(fileId);
    if (existing == null) {
      throw new IllegalArgumentException("File not found: " + fileId);
    }
    existing.name = name;
    existing.hash = hash;
    existing.ext = ext;
    existing.mimeType = mimeType;
    existing.size = size;
    existing.url = url;
    existing.storageKey = storageKey;
    existing.provider = provider;
    existing.width = null;
    existing.height = null;
    existing.formats.clear();
    existing.persist();
    Log.infof("Replaced content for media file: %d", fileId);
    return existing;
  }

  /** Deletes a file record (storage cleanup is handled by the caller). */
  @Transactional
  public void delete(Long id) {
    CmsFile file = CmsFile.findById(id);
    if (file != null) {
      file.delete();
      Log.infof("Deleted media file: %d (%s)", id, file.name);
    }
  }

  /** Moves a file to a different folder. */
  @Transactional
  public CmsFile moveToFolder(Long fileId, Long targetFolderId, String targetFolderPath) {
    CmsFile file = CmsFile.findById(fileId);
    if (file == null) {
      throw new IllegalArgumentException("File not found: " + fileId);
    }
    file.folderId = targetFolderId;
    file.folderPath = targetFolderPath;
    file.persist();
    Log.infof("Moved file %d to folder %d", fileId, targetFolderId);
    return file;
  }
}
