package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating an API token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateApiTokenRequest {

  @NotBlank(message = "Token name is required")
  @Size(max = 255)
  public String name;

  @Size(max = 50)
  public String type = "full-access";

  public String description;

  /** ISO-8601 timestamp for token expiry. */
  public String expiresAt;

  public CreateApiTokenRequest() {}
}
