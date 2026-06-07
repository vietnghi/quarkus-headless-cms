package com.quarkus.cms.media.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quarkus.cms.media.entity.CmsFolder;

import java.time.Instant;
import java.util.List;

/**
 * Data transfer object for media library folders.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FolderDto {

  public Long id;
  public String name;
  public String path;
  public Long parentId;
  public int fileCount;
  public List<FolderDto> children;
  public Instant createdAt;

  public static FolderDto from(CmsFolder folder) {
    FolderDto dto = new FolderDto();
    dto.id = folder.id;
    dto.name = folder.name;
    dto.path = folder.path;
    dto.parentId = folder.parentId;
    dto.createdAt = folder.createdAt;
    return dto;
  }
}
