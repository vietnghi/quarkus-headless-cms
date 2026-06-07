package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.quarkus.cms.auth.entity.CmsRole;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Role DTO for admin API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "code", "name", "description", "permissions", "createdAt", "updatedAt"})
public class RoleDto {

  public Long id;
  public String code;
  public String name;
  public String description;
  public List<PermissionDto> permissions;
  public Instant createdAt;
  public Instant updatedAt;

  public RoleDto() {}

  /** Creates a RoleDto from a CmsRole entity. */
  public static RoleDto from(CmsRole role) {
    RoleDto dto = new RoleDto();
    dto.id = role.id;
    dto.code = role.code;
    dto.name = role.name;
    dto.description = role.description;
    dto.permissions =
        role.permissions.stream().map(PermissionDto::from).collect(Collectors.toList());
    dto.createdAt = role.createdAt;
    dto.updatedAt = role.updatedAt;
    return dto;
  }
}
