package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.quarkus.cms.auth.entity.CmsUser;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public user profile DTO (safe for API responses — excludes password hash).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "username", "email", "firstName", "lastName", "isActive",
    "isBlocked", "preferredLocale", "lastLoginAt", "roles", "createdAt", "updatedAt"})
public class UserDto {

  public Long id;
  public String username;
  public String email;
  public String firstName;
  public String lastName;
  public boolean isActive;
  public boolean isBlocked;
  public String preferredLocale;
  public Instant lastLoginAt;
  public Set<RoleDto> roles;
  public Instant createdAt;
  public Instant updatedAt;

  public UserDto() {}

  /** Creates a UserDto from a CmsUser entity. */
  public static UserDto from(CmsUser user) {
    UserDto dto = new UserDto();
    dto.id = user.id;
    dto.username = user.username;
    dto.email = user.email;
    dto.firstName = user.firstName;
    dto.lastName = user.lastName;
    dto.isActive = user.isActive;
    dto.isBlocked = user.isBlocked;
    dto.preferredLocale = user.preferredLocale;
    dto.lastLoginAt = user.lastLoginAt;
    dto.roles = user.roles.stream().map(RoleDto::from).collect(Collectors.toSet());
    dto.createdAt = user.createdAt;
    dto.updatedAt = user.updatedAt;
    return dto;
  }
}
