package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.quarkus.cms.auth.entity.CmsPermission;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Permission DTO for admin API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "action", "subject", "fields", "conditions", "createdAt"})
public class PermissionDto {

  public Long id;
  public String action;
  public String subject;
  public List<String> fields;
  public Map<String, Object> conditions;
  public Instant createdAt;

  public PermissionDto() {}

  /** Creates a PermissionDto from a CmsPermission entity. */
  public static PermissionDto from(CmsPermission perm) {
    PermissionDto dto = new PermissionDto();
    dto.id = perm.id;
    dto.action = perm.action;
    dto.subject = perm.subject;
    dto.fields = perm.fields;
    dto.conditions = perm.conditions;
    dto.createdAt = perm.createdAt;
    return dto;
  }
}
