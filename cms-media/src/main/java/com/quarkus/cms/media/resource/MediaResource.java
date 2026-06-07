package com.quarkus.cms.media.resource;

import com.quarkus.cms.media.config.MediaConfig;
import com.quarkus.cms.media.dto.FolderDto;
import com.quarkus.cms.media.dto.MediaFileDto;
import com.quarkus.cms.media.dto.UploadResponse;
import com.quarkus.cms.media.entity.CmsFile;
import com.quarkus.cms.media.entity.CmsFile.ImageFormatInfo;
import com.quarkus.cms.media.entity.CmsFolder;
import com.quarkus.cms.media.image.ImageOptimizer;
import com.quarkus.cms.media.image.ImageOptimizer.VariantResult;
import com.quarkus.cms.media.repository.CmsFileRepository;
import com.quarkus.cms.media.storage.StorageProvider;
import com.quarkus.cms.media.validation.UploadValidator;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Media Library REST API.
 *
 * <p>Provides endpoints for file upload (single and multi-part), listing, searching, deleting,
 * replacing files, and folder management.
 *
 * <p>Upload handling uses Vert.x {@link RoutingContext#fileUploads()} for efficient
 * multi-part form data processing.
 */
@Path("/api/upload")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Media Library", description = "File upload and media management endpoints")
public class MediaResource {

  @Inject MediaConfig config;

  @Inject CmsFileRepository fileRepository;

  @Inject UploadValidator uploadValidator;

  @Inject ImageOptimizer imageOptimizer;

  @Inject StorageProvider storageProvider;

  @Inject RoutingContext routingContext;

  /**
   * Upload one or more files via multipart form data.
   *
   * <p>Expects a multipart request with one or more file parts. Each part's name is used as the
   * form field name; the original filename and content type are extracted from the part headers.
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
      summary = "Upload files",
      description = "Upload one or more files via multipart form data. Returns metadata for each successfully uploaded file.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Upload completed (check response for per-file errors)"),
      @APIResponse(responseCode = "400", description = "Invalid upload (no files or validation failed)")
  })
  public Uni<Response> upload(
      @Parameter(description = "Target folder ID for organizing uploads")
      @QueryParam("folderId") Long folderId,
      @Parameter(description = "Target folder path (e.g., /images/banners)")
      @QueryParam("folderPath") String folderPath) {

    return Uni.createFrom().item(() -> {
      List<io.vertx.ext.web.FileUpload> uploadedFiles = routingContext.fileUploads();
      if (uploadedFiles == null || uploadedFiles.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "No files uploaded"))
            .build();
      }

      List<MediaFileDto> successFiles = new ArrayList<>();
      List<UploadResponse.UploadError> errors = new ArrayList<>();

      for (io.vertx.ext.web.FileUpload fu : uploadedFiles) {
        try {
          CmsFile file = processUpload(fu, folderId, folderPath);
          successFiles.add(MediaFileDto.from(file));
        } catch (Exception e) {
          Log.errorf("Upload failed for %s: %s", fu.fileName(), e.getMessage());
          errors.add(new UploadResponse.UploadError(fu.fileName(), e.getMessage()));
        }
      }

      if (successFiles.size() == 1 && errors.isEmpty()) {
        return Response.ok(UploadResponse.single(successFiles.get(0))).build();
      }
      return Response.ok(UploadResponse.multiple(successFiles, errors.isEmpty() ? null : errors))
          .build();
    });
  }

  /** List all files with optional pagination. */
  @GET
  @Path("/files")
  @Operation(summary = "List files", description = "Returns a paginated list of all uploaded files.")
  public Uni<Response> listFiles(
      @Parameter(description = "Page number (0-based)")
      @QueryParam("page") @DefaultValue("0") int page,
      @Parameter(description = "Page size")
      @QueryParam("pageSize") @DefaultValue("50") int pageSize,
      @Parameter(description = "Filter by folder ID")
      @QueryParam("folderId") Long folderId,
      @Parameter(description = "Filter by MIME type prefix (e.g., \"image/\")")
      @QueryParam("mimeType") String mimeType) {

    return Uni.createFrom().item(() -> {
      List<CmsFile> files;
      if (folderId != null) {
        files = fileRepository.findByFolder(folderId);
      } else if (mimeType != null) {
        files = fileRepository.findByMimeTypePrefix(mimeType);
      } else {
        files = fileRepository.listPaged(page, pageSize);
      }

      List<MediaFileDto> dtos = files.stream().map(MediaFileDto::lightweight).toList();
      long total = folderId != null
          ? CmsFile.countByFolder(folderId)
          : fileRepository.count();

      Map<String, Object> result = new HashMap<>();
      result.put("data", dtos);
      result.put("total", total);
      result.put("page", page);
      result.put("pageSize", pageSize);
      return Response.ok(result).build();
    });
  }

  /** Get a single file by ID. */
  @GET
  @Path("/files/{id}")
  @Operation(summary = "Get file", description = "Returns file metadata by ID.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "File found"),
      @APIResponse(responseCode = "404", description = "File not found")
  })
  public Uni<Response> getFile(
      @Parameter(description = "File ID", required = true)
      @PathParam("id") Long id) {

    return Uni.createFrom().item(() -> {
      CmsFile file = fileRepository.findById(id);
      if (file == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "File not found: " + id))
            .build();
      }
      return Response.ok(MediaFileDto.from(file)).build();
    });
  }

  /** Delete a file by ID. */
  @DELETE
  @Path("/files/{id}")
  @Operation(summary = "Delete file", description = "Deletes a file and its storage artifacts.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "File deleted"),
      @APIResponse(responseCode = "404", description = "File not found")
  })
  public Uni<Response> deleteFile(
      @Parameter(description = "File ID", required = true)
      @PathParam("id") Long id) {

    return Uni.createFrom().item(() -> {
      CmsFile file = fileRepository.findById(id);
      if (file == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "File not found: " + id))
            .build();
      }

      // Delete storage artifacts
      storageProvider.delete(file.storageKey);
      for (ImageFormatInfo fmt : file.formats) {
        if (fmt.storageKey != null) {
          storageProvider.delete(fmt.storageKey);
        }
      }

      fileRepository.delete(id);
      return Response.ok(Map.of("id", id, "deleted", true)).build();
    });
  }

  /** Replace a file's content with a new upload. */
  @PUT
  @Path("/files/{id}")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(summary = "Replace file", description = "Replaces the content of an existing file with a new upload.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "File replaced"),
      @APIResponse(responseCode = "404", description = "File not found")
  })
  public Uni<Response> replaceFile(
      @Parameter(description = "File ID", required = true)
      @PathParam("id") Long id) {

    return Uni.createFrom().item(() -> {
      CmsFile existing = fileRepository.findById(id);
      if (existing == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "File not found: " + id))
            .build();
      }

      List<io.vertx.ext.web.FileUpload> uploadedFiles = routingContext.fileUploads();
      if (uploadedFiles == null || uploadedFiles.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "No file provided for replacement"))
            .build();
      }

      io.vertx.ext.web.FileUpload fu = uploadedFiles.iterator().next();

      // Delete old storage artifacts
      storageProvider.delete(existing.storageKey);
      for (ImageFormatInfo fmt : existing.formats) {
        if (fmt.storageKey != null) {
          storageProvider.delete(fmt.storageKey);
        }
      }

      try {
        byte[] fileBytes = readUploadedFile(fu);
        String hash = sha256(fileBytes);
        String mimeType = fu.contentType();
        String ext = extractExtension(fu.fileName());

        StorageProvider.StoreResult result = storageProvider.store(
            new java.io.ByteArrayInputStream(fileBytes), fu.fileName(), mimeType,
            Map.of("folderPath", existing.folderPath != null ? existing.folderPath : ""));

        CmsFile updated = fileRepository.replaceContent(id, fu.fileName(), hash, ext, mimeType,
            result.size(), result.url(), result.storageKey(), storageProvider.providerKey());

        // Process image
        if (mimeType.startsWith("image/")) {
          processImage(updated, fileBytes, ext);
        }

        return Response.ok(MediaFileDto.from(updated)).build();
      } catch (Exception e) {
        Log.errorf("File replacement failed: %s", e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Map.of("error", "Replacement failed: " + e.getMessage()))
            .build();
      }
    });
  }

  /** Search files by name. */
  @GET
  @Path("/files/search")
  @Operation(summary = "Search files", description = "Search files by name (case-insensitive partial match).")
  public Uni<Response> searchFiles(
      @Parameter(description = "Search query", required = true)
      @QueryParam("q") String query) {

    return Uni.createFrom().item(() -> {
      if (query == null || query.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "Search query 'q' is required"))
            .build();
      }
      List<CmsFile> files = fileRepository.searchByName(query);
      List<MediaFileDto> dtos = files.stream().map(MediaFileDto::lightweight).toList();
      return Response.ok(Map.of("data", dtos, "total", dtos.size())).build();
    });
  }

  // ---- Folder endpoints ----

  /** List folders. */
  @GET
  @Path("/folders")
  @Operation(summary = "List folders", description = "Returns all folders, optionally filtered by parent.")
  public Uni<Response> listFolders(
      @Parameter(description = "Parent folder ID (omit for root-level folders)")
      @QueryParam("parentId") Long parentId) {

    return Uni.createFrom().item(() -> {
      List<CmsFolder> folders = CmsFolder.findByParent(parentId);
      List<FolderDto> dtos = new ArrayList<>();
      for (CmsFolder folder : folders) {
        FolderDto dto = FolderDto.from(folder);
        dto.fileCount = (int) CmsFile.countByFolder(folder.id);
        dtos.add(dto);
      }
      return Response.ok(Map.of("data", dtos)).build();
    });
  }

  /** Create a folder. */
  @POST
  @Path("/folders")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Create folder", description = "Creates a new folder in the media library.")
  public Uni<Response> createFolder(Map<String, Object> body) {

    return Uni.createFrom().item(() -> {
      String name = (String) body.get("name");
      if (name == null || name.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "Folder name is required"))
            .build();
      }

      Long parentId = body.get("parentId") instanceof Number
          ? ((Number) body.get("parentId")).longValue() : null;

      // Build path
      String path;
      if (parentId != null) {
        CmsFolder parent = CmsFolder.findById(parentId);
        if (parent == null) {
          return Response.status(Response.Status.BAD_REQUEST)
              .entity(Map.of("error", "Parent folder not found: " + parentId))
              .build();
        }
        path = parent.path + "/" + name;
      } else {
        path = "/" + name;
      }

      if (CmsFolder.existsByPath(path)) {
        return Response.status(Response.Status.CONFLICT)
            .entity(Map.of("error", "A folder already exists at path: " + path))
            .build();
      }

      CmsFolder folder = new CmsFolder();
      folder.name = name;
      folder.path = path;
      folder.parentId = parentId;
      folder.persist();

      Log.infof("Created folder: %s (id=%d)", path, folder.id);
      FolderDto dto = FolderDto.from(folder);
      dto.fileCount = 0;
      return Response.status(Response.Status.CREATED).entity(dto).build();
    });
  }

  /** Delete a folder. */
  @DELETE
  @Path("/folders/{id}")
  @Operation(summary = "Delete folder", description = "Deletes a folder. Only empty folders can be deleted.")
  public Uni<Response> deleteFolder(
      @Parameter(description = "Folder ID", required = true)
      @PathParam("id") Long id) {

    return Uni.createFrom().item(() -> {
      CmsFolder folder = CmsFolder.findById(id);
      if (folder == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "Folder not found: " + id))
            .build();
      }

      long fileCount = CmsFile.countByFolder(id);
      long childCount = CmsFolder.count("parentId", id);
      if (fileCount > 0 || childCount > 0) {
        return Response.status(Response.Status.CONFLICT)
            .entity(Map.of("error",
                "Folder is not empty (" + fileCount + " files, " + childCount
                    + " subfolders)"))
            .build();
      }

      folder.delete();
      Log.infof("Deleted folder: %s (id=%d)", folder.path, id);
      return Response.ok(Map.of("id", id, "deleted", true)).build();
    });
  }

  // ---- Private helpers ----

  /**
   * Processes a single uploaded file via Vert.x, validates, stores, and creates the DB record.
   */
  private CmsFile processUpload(io.vertx.ext.web.FileUpload fu, Long folderId, String folderPath) {
    byte[] fileBytes = readUploadedFile(fu);

    String mimeType = fu.contentType();
    if (mimeType == null || mimeType.isBlank()) {
      mimeType = "application/octet-stream";
    }

    uploadValidator.validate(fu.fileName(), mimeType, fileBytes.length);

    String hash = sha256(fileBytes);
    String ext = extractExtension(fu.fileName());

    // Store file via configured provider
    Map<String, String> metadata = new HashMap<>();
    if (folderPath != null) {
      metadata.put("folderPath", folderPath);
    }
    StorageProvider.StoreResult result = storageProvider.store(
        new java.io.ByteArrayInputStream(fileBytes), fu.fileName(), mimeType, metadata);

    // Create DB record
    CmsFile file = new CmsFile();
    file.name = fu.fileName();
    file.hash = hash;
    file.ext = ext;
    file.mimeType = mimeType;
    file.size = fileBytes.length;
    file.url = result.url();
    file.provider = storageProvider.providerKey();
    file.storageKey = result.storageKey();
    file.folderId = folderId;
    file.folderPath = folderPath;
    fileRepository.create(file);

    // Process image
    if (mimeType.startsWith("image/")) {
      processImage(file, fileBytes, ext);
    }

    return file;
  }

  /** Reads file bytes from a Vert.x FileUpload. */
  private byte[] readUploadedFile(io.vertx.ext.web.FileUpload fu) {
    try (InputStream in = new java.io.FileInputStream(fu.uploadedFileName())) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int n;
      while ((n = in.read(chunk)) != -1) {
        buffer.write(chunk, 0, n);
      }
      return buffer.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read uploaded file: " + fu.fileName(), e);
    }
  }

  /** Generates thumbnails and responsive variants, storing them as format entries. */
  private void processImage(CmsFile file, byte[] imageBytes, String formatName) {
    // Get dimensions
    int[] dims = imageOptimizer.getDimensions(imageBytes);
    if (dims != null) {
      file.width = dims[0];
      file.height = dims[1];
      file.persist();
    }

    // Generate thumbnails
    List<VariantResult> thumbnails = imageOptimizer.generateThumbnails(imageBytes, formatName);
    List<ImageFormatInfo> formats = new ArrayList<>();

    for (VariantResult vr : thumbnails) {
      Map<String, String> variantMeta = Map.of("variant", vr.name());
      StorageProvider.StoreResult stored = storageProvider.store(
          new java.io.ByteArrayInputStream(vr.bytes()),
          file.name + "_" + vr.name() + "." + vr.format(),
          vr.mimeType(), variantMeta);

      formats.add(new ImageFormatInfo(
          vr.name(), vr.width(), vr.height(), vr.size(), stored.url(), stored.storageKey(),
          vr.mimeType()));
    }

    // Generate responsive variants
    List<VariantResult> responsive = imageOptimizer.generateResponsiveVariants(imageBytes,
        formatName);
    for (VariantResult vr : responsive) {
      Map<String, String> variantMeta = Map.of("variant", vr.name());
      StorageProvider.StoreResult stored = storageProvider.store(
          new java.io.ByteArrayInputStream(vr.bytes()),
          file.name + "_" + vr.name() + "." + vr.format(),
          vr.mimeType(), variantMeta);

      formats.add(new ImageFormatInfo(
          vr.name(), vr.width(), vr.height(), vr.size(), stored.url(), stored.storageKey(),
          vr.mimeType()));
    }

    file.formats = formats;
    file.persist();
  }

  /** Computes SHA-256 hex digest of bytes. */
  private static String sha256(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(bytes);
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  /** Extracts the file extension (without dot) from a file name. */
  private static String extractExtension(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dot + 1).toLowerCase();
  }
}
