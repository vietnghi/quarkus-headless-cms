package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.quarkus.cms.auth.entity.CmsApiToken;
import java.time.Instant;

/**
 * API token DTO for admin API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "name", "type", "description", "lastUsedAt", "expiresAt",
    "isActive", "createdById", "createdAt", "updatedAt"})
public class ApiTokenDto {

  public Long id;
  public String name;
  public String type;
  public String description;
  public Instant lastUsedAt;
  public Instant expiresAt;
  public boolean isActive;
  public Long createdById;
  public Instant createdAt;
  public Instant updatedAt;

  /** The raw token value — only included on creation, never on list/get. */
  public String accessKey;

  public ApiTokenDto() {}

  /** Creates an ApiTokenDto from an entity (without the raw token). */
  public static ApiTokenDto from(CmsApiToken token) {
    ApiTokenDto dto = new ApiTokenDto();
    dto.id = token.id;
    dto.name = token.name;
    dto.type = token.type;
    dto.description = token.description;
    dto.lastUsedAt = token.lastUsedAt;
    dto.expiresAt = token.expiresAt;
    dto.isActive = token.isActive;
    dto.createdById = token.createdBy != null ? token.createdBy.id : null;
    dto.createdAt = token.createdAt;
    dto.updatedAt = token.updatedAt;
    return dto;
  }
}
