package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Request body for creating or updating a role.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateRoleRequest {

  @NotBlank(message = "Role code is required")
  @Size(max = 100)
  public String code;

  @NotBlank(message = "Role name is required")
  @Size(max = 255)
  public String name;

  public String description;

  /** List of permission grants to attach to the role. */
  public List<PermissionInput> permissions;

  public CreateRoleRequest() {}

  /** Permission input in a role request. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class PermissionInput {
    @NotBlank public String action;
    public String subject;
    public List<String> fields;
    public Map<String, Object> conditions;
  }
}
