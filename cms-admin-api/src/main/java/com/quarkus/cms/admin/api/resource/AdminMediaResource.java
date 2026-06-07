package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.media.config.MediaConfig;
import com.quarkus.cms.media.dto.MediaFileDto;
import com.quarkus.cms.media.dto.FolderDto;
import com.quarkus.cms.media.dto.UploadResponse;
import com.quarkus.cms.media.entity.CmsFile;
import com.quarkus.cms.media.entity.CmsFolder;
import com.quarkus.cms.media.repository.CmsFileRepository;
import com.quarkus.cms.media.storage.StorageProvider;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin media library endpoints.
 *
 * Provides admin-facing CRUD for media files and folders, wrapping
 * the existing cms-media module with admin authentication and
 * consistent Strapi JSON envelope responses.
 *
 * All endpoints require admin authentication with appropriate permissions.
 */
@Path("/admin/media")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminMediaResource {

    @Inject
    CmsFileRepository fileRepository;

    @Inject
    MediaConfig mediaConfig;

    @Inject
    StorageProvider storageProvider;

    // ---- Files ---- //

    /**
     * List all media files with pagination and filtering.
     */
    @GET
    @Path("/files")
    @PermissionCheck("admin::media.read")
    public Uni<Response> listFiles(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("50") int pageSize,
            @QueryParam("folderId") Long folderId,
            @QueryParam("mimeType") String mimeType,
            @QueryParam("search") String search) {

        return Uni.createFrom().item(() -> {
            List<CmsFile> files;
            long total;

            if (search != null && !search.isBlank()) {
                files = fileRepository.searchByName(search);
                total = files.size();
            } else if (folderId != null) {
                files = fileRepository.findByFolder(folderId);
                total = CmsFile.countByFolder(folderId);
            } else if (mimeType != null) {
                files = fileRepository.findByMimeTypePrefix(mimeType);
                total = CmsFile.count("mimeType like ?1", mimeType + "%");
            } else {
                files = fileRepository.listPaged(page, Math.min(pageSize, 100));
                total = fileRepository.count();
            }

            List<MediaFileDto> dtos = files.stream().map(MediaFileDto::from).toList();

            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("pageSize", pageSize);
            pagination.put("total", total);
            pagination.put("pageCount", pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0);

            Map<String, Object> meta = new HashMap<>();
            meta.put("pagination", pagination);

            return Response.ok(new StrapiCollectionResponse<>(dtos, meta)).build();
        });
    }

    /**
     * Get a single file by ID.
     */
    @GET
    @Path("/files/{id}")
    @PermissionCheck("admin::media.read")
    public Uni<Response> getFile(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> {
            CmsFile file = fileRepository.findById(id);
            if (file == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(StrapiErrorResponse.of(404, "NotFoundError", "File not found: " + id))
                        .build();
            }
            return Response.ok(new StrapiSingleResponse<>(MediaFileDto.from(file))).build();
        });
    }

    /**
     * Delete a file by ID.
     */
    @DELETE
    @Path("/files/{id}")
    @PermissionCheck("admin::media.delete")
    public Uni<Response> deleteFile(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> {
            CmsFile file = fileRepository.findById(id);
            if (file == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(StrapiErrorResponse.of(404, "NotFoundError", "File not found: " + id))
                        .build();
            }

            storageProvider.delete(file.storageKey);
            if (file.formats != null) {
                for (CmsFile.ImageFormatInfo fmt : file.formats) {
                    if (fmt.storageKey != null) {
                        storageProvider.delete(fmt.storageKey);
                    }
                }
            }

            fileRepository.delete(id);
            Log.infof("Admin deleted file: %s (id=%d)", file.name, id);
            return Response.ok(Map.of("id", id, "deleted", true)).build();
        });
    }

    /**
     * Update file metadata (name, alternative text, caption, folder).
     */
    @PUT
    @Path("/files/{id}")
    @PermissionCheck("admin::media.update")
    public Uni<Response> updateFile(
            @PathParam("id") Long id,
            Map<String, Object> body) {

        return Uni.createFrom().item(() -> {
            CmsFile file = fileRepository.findById(id);
            if (file == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(StrapiErrorResponse.of(404, "NotFoundError", "File not found: " + id))
                        .build();
            }

            if (body.containsKey("name")) {
                file.name = (String) body.get("name");
            }
            if (body.containsKey("alternativeText")) {
                file.altText = (String) body.get("alternativeText");
            }
            if (body.containsKey("caption")) {
                file.caption = (String) body.get("caption");
            }
            if (body.containsKey("folderId")) {
                Object fId = body.get("folderId");
                file.folderId = fId instanceof Number ? ((Number) fId).longValue() : null;
            }

            file.persist();
            return Response.ok(new StrapiSingleResponse<>(MediaFileDto.from(file))).build();
        });
    }

    // ---- Folders ---- //

    /**
     * List all folders, optionally filtered by parent.
     */
    @GET
    @Path("/folders")
    @PermissionCheck("admin::media.read")
    public Uni<Response> listFolders(@QueryParam("parentId") Long parentId) {
        return Uni.createFrom().item(() -> {
            List<CmsFolder> folders = CmsFolder.findByParent(parentId);
            List<FolderDto> dtos = new ArrayList<>();
            for (CmsFolder folder : folders) {
                FolderDto dto = FolderDto.from(folder);
                dto.fileCount = (int) CmsFile.countByFolder(folder.id);
                dtos.add(dto);
            }
            return Response.ok(new StrapiCollectionResponse<>(dtos)).build();
        });
    }

    /**
     * Get a single folder by ID.
     */
    @GET
    @Path("/folders/{id}")
    @PermissionCheck("admin::media.read")
    public Uni<Response> getFolder(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> {
            CmsFolder folder = CmsFolder.findById(id);
            if (folder == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(StrapiErrorResponse.of(404, "NotFoundError", "Folder not found: " + id))
                        .build();
            }
            FolderDto dto = FolderDto.from(folder);
            dto.fileCount = (int) CmsFile.countByFolder(folder.id);
            return Response.ok(new StrapiSingleResponse<>(dto)).build();
        });
    }

    /**
     * Create a new folder.
     */
    @POST
    @Path("/folders")
    @PermissionCheck("admin::media.create")
    public Uni<Response> createFolder(Map<String, Object> body) {
        return Uni.createFrom().item(() -> {
            String name = (String) body.get("name");
            if (name == null || name.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(StrapiErrorResponse.of(400, "ValidationError", "Folder name is required"))
                        .build();
            }

            Long parentId = body.get("parentId") instanceof Number
                    ? ((Number) body.get("parentId")).longValue() : null;

            String path;
            if (parentId != null) {
                CmsFolder parent = CmsFolder.findById(parentId);
                if (parent == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(StrapiErrorResponse.of(400, "ValidationError", "Parent folder not found: " + parentId))
                            .build();
                }
                path = parent.path + "/" + name;
            } else {
                path = "/" + name;
            }

            if (CmsFolder.existsByPath(path)) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(StrapiErrorResponse.of(409, "ConflictError", "Folder already exists at: " + path))
                        .build();
            }

            CmsFolder folder = new CmsFolder();
            folder.name = name;
            folder.path = path;
            folder.parentId = parentId;
            folder.persist();

            FolderDto dto = FolderDto.from(folder);
            dto.fileCount = 0;
            return Response.status(Response.Status.CREATED)
                    .entity(new StrapiSingleResponse<>(dto)).build();
        });
    }

    /**
     * Update folder metadata.
     */
    @PUT
    @Path("/folders/{id}")
    @PermissionCheck("admin::media.update")
    public Uni<Response> updateFolder(
            @PathParam("id") Long id,
            Map<String, Object> body) {

        return Uni.createFrom().item(() -> {
            CmsFolder folder = CmsFolder.findById(id);
            if (folder == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(StrapiErrorResponse.of(404, "NotFoundError", "Folder not found: " + id))
                        .build();
            }

            String newName = (String) body.get("name");
            if (newName != null && !newName.isBlank() && !newName.equals(folder.name)) {
                // Update the path
                String parentPath = folder.path.contains("/")
                        ? folder.path.substring(0, folder.path.lastIndexOf('/'))
                        : "";
                String newPath = parentPath.isEmpty() ? "/" + newName : parentPath + "/" + newName;

                if (CmsFolder.existsByPath(newPath)) {
                    return Response.status(Response.Status.CONFLICT)
                            .entity(StrapiErrorResponse.of(409, "ConflictError", "Folder already exists at: " + newPath))
                            .build();
                }
                folder.name = newName;
                folder.path = newPath;
            }

            folder.persist();
            FolderDto dto = FolderDto.from(folder);
            dto.fileCount = (int) CmsFile.countByFolder(folder.id);
            return Response.ok(new StrapiSingleResponse<>(dto)).build();
        });
    }

    /**
     * Delete a folder (must be empty).
     */
    @DELETE
    @Path("/folders/{id}")
    @PermissionCheck("admin::media.delete")
    public Uni<Response> deleteFolder(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> {
            CmsFolder folder = CmsFolder.findById(id);
            if (folder == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(StrapiErrorResponse.of(404, "NotFoundError", "Folder not found: " + id))
                        .build();
            }

            long fileCount = CmsFile.countByFolder(id);
            long childCount = CmsFolder.count("parentId", id);
            if (fileCount > 0 || childCount > 0) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(StrapiErrorResponse.of(409, "ConflictError",
                                "Folder is not empty (" + fileCount + " files, " + childCount + " subfolders)"))
                        .build();
            }

            folder.delete();
            return Response.ok(Map.of("id", id, "deleted", true)).build();
        });
    }

    // ---- Upload Info ---- //

    /**
     * Get media library configuration (upload settings, allowed types, etc.).
     */
    @GET
    @Path("/configuration")
    @PermissionCheck("admin::media.read")
    public Uni<Response> getConfiguration() {
        return Uni.createFrom().item(() -> {
            Map<String, Object> config = new HashMap<>();
            // Parse max upload size (e.g., "10M" -> bytes)
            String maxSizeStr = mediaConfig.maxUploadSize();
            long maxSizeBytes = parseSizeToBytes(maxSizeStr);
            config.put("uploadMaxFileSize", maxSizeBytes);
            config.put("uploadMaxFileSizeHuman", maxSizeStr);
            config.put("allowedTypes", List.of(mediaConfig.allowedTypes().split(",")));
            config.put("provider", mediaConfig.storageProvider());
            config.put("imageProcessing", mediaConfig.image().enabled());
            return Response.ok(new StrapiSingleResponse<>(config)).build();
        });
    }

    private long parseSizeToBytes(String size) {
        if (size == null || size.isBlank()) return 10 * 1024 * 1024;
        size = size.trim().toUpperCase();
        long multiplier = 1;
        if (size.endsWith("KB")) {
            multiplier = 1024;
            size = size.substring(0, size.length() - 2).trim();
        } else if (size.endsWith("MB")) {
            multiplier = 1024 * 1024;
            size = size.substring(0, size.length() - 2).trim();
        } else if (size.endsWith("GB")) {
            multiplier = 1024 * 1024 * 1024;
            size = size.substring(0, size.length() - 2).trim();
        } else if (size.endsWith("B")) {
            size = size.substring(0, size.length() - 1).trim();
        }
        try {
            return (long) (Double.parseDouble(size) * multiplier);
        } catch (NumberFormatException e) {
            return 10 * 1024 * 1024;
        }
    }
}
