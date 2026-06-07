package com.quarkus.cms.media.service;

import com.quarkus.cms.media.entity.CmsFile;
import com.quarkus.cms.media.image.ImageOptimizer;
import com.quarkus.cms.media.repository.CmsFileRepository;
import com.quarkus.cms.media.storage.StorageProvider;
import com.quarkus.cms.media.validation.UploadValidator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Core media service orchestrating file upload, retrieval, deletion,
 * image processing, and search operations.
 */
@ApplicationScoped
public class MediaService {

  @Inject StorageProvider storageProvider;

  @Inject CmsFileRepository fileRepository;

  @Inject ImageOptimizer imageOptimizer;

  @Inject UploadValidator uploadValidator;

  /**
   * Uploads a file, stores it, generates thumbnails if it's an image,
   * and persists metadata in the database.
   */
  @Transactional
  public CmsFile upload(String name, String mimeType, byte[] data, String altText, String caption,
      String folderPath) {
    if (folderPath == null || folderPath.isBlank()) {
      folderPath = "/";
    }

    uploadValidator.validate(name, mimeType, data.length);

    String hash = sha256(data);

    // Store file via the storage provider
    Map<String, String> metadata = Map.of("folderPath", folderPath);
    StorageProvider.StoreResult result = storageProvider.store(
        new java.io.ByteArrayInputStream(data), name, mimeType, metadata);

    CmsFile file = new CmsFile();
    file.name = name;
    file.altText = altText;
    file.caption = caption;
    file.hash = hash;
    file.mimeType = mimeType;
    file.size = data.length;
    file.url = result.url();
    file.provider = storageProvider.providerKey();
    file.storageKey = result.storageKey();
    file.folderPath = folderPath;
    fileRepository.create(file);

    // Process image
    if (mimeType != null && mimeType.startsWith("image/")) {
      processImage(file, data);
    }

    return file;
  }

  public CmsFile findById(Long id) {
    return fileRepository.findById(id);
  }

  public List<CmsFile> listAll() {
    return fileRepository.listAll();
  }

  public List<CmsFile> listPaginated(int page, int pageSize) {
    return fileRepository.listPaged(page, pageSize);
  }

  public long count() {
    return fileRepository.count();
  }

  public List<CmsFile> searchByName(String query) {
    return fileRepository.searchByName(query);
  }

  public List<CmsFile> findByMimeType(String mimeTypePrefix) {
    return fileRepository.findByMimeTypePrefix(mimeTypePrefix);
  }

  public List<CmsFile> findByFolder(String folderPath) {
    return CmsFile.find("folderPath = ?1 order by createdAt desc", folderPath).list();
  }

  public InputStream getFileStream(CmsFile file) {
    if (file.storageKey != null) {
      return storageProvider.retrieve(file.storageKey);
    }
    return null;
  }

  @Transactional
  public boolean delete(Long id) {
    CmsFile file = fileRepository.findById(id);
    if (file == null) {
      return false;
    }
    storageProvider.delete(file.storageKey);
    fileRepository.delete(id);
    return true;
  }

  private void processImage(CmsFile file, byte[] imageBytes) {
    int[] dims = imageOptimizer.getDimensions(imageBytes);
    if (dims != null) {
      file.width = dims[0];
      file.height = dims[1];
    }
    file.persist();
  }

  private static String sha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(data);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
