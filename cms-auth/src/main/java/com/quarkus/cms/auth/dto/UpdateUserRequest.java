package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for updating a user's profile or role assignments.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateUserRequest {

  @Size(max = 100)
  public String firstName;

  @Size(max = 100)
  public String lastName;

  @Size(max = 10)
  public String preferredLocale;

  public Boolean isActive;

  public Boolean isBlocked;

  /** List of role IDs to assign to the user. */
  public List<Long> roleIds;

  public UpdateUserRequest() {}
}
