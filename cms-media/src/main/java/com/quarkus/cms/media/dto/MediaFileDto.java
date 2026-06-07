package com.quarkus.cms.media.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quarkus.cms.media.entity.CmsFile;
import com.quarkus.cms.media.entity.CmsFile.ImageFormatInfo;

import java.time.Instant;
import java.util.List;

/**
 * Data transfer object for media file responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaFileDto {

  public Long id;
  public String name;
  public String hash;
  public String ext;
  public String mimeType;
  public long size;
  public String url;
  public String provider;
  public String altText;
  public String caption;
  public Integer width;
  public Integer height;
  public Long folderId;
  public String folderPath;
  public List<ImageFormatInfo> formats;
  public Instant createdAt;
  public Instant updatedAt;

  /** Creates a DTO from a {@link CmsFile} entity. */
  public static MediaFileDto from(CmsFile file) {
    MediaFileDto dto = new MediaFileDto();
    dto.id = file.id;
    dto.name = file.name;
    dto.hash = file.hash;
    dto.ext = file.ext;
    dto.mimeType = file.mimeType;
    dto.size = file.size;
    dto.url = file.url;
    dto.provider = file.provider;
    dto.altText = file.altText;
    dto.caption = file.caption;
    dto.width = file.width;
    dto.height = file.height;
    dto.folderId = file.folderId;
    dto.folderPath = file.folderPath;
    dto.formats = file.formats;
    dto.createdAt = file.createdAt;
    dto.updatedAt = file.updatedAt;
    return dto;
  }

  /** Creates a lightweight DTO (no formats array) for list views. */
  public static MediaFileDto lightweight(CmsFile file) {
    MediaFileDto dto = new MediaFileDto();
    dto.id = file.id;
    dto.name = file.name;
    dto.ext = file.ext;
    dto.mimeType = file.mimeType;
    dto.size = file.size;
    dto.url = file.url;
    dto.provider = file.provider;
    dto.width = file.width;
    dto.height = file.height;
    dto.folderId = file.folderId;
    dto.folderPath = file.folderPath;
    dto.createdAt = file.createdAt;
    return dto;
  }
}
