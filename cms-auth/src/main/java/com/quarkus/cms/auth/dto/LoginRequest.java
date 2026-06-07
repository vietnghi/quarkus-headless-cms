package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request body.
 *
 * <p>Accepts either username or email as the identifier, plus the password.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginRequest {

  @NotBlank(message = "Identifier (username or email) is required")
  public String identifier;

  @NotBlank(message = "Password is required")
  public String password;

  /** Optional provider name. Defaults to "local". */
  public String provider;

  public LoginRequest() {}

  public LoginRequest(String identifier, String password) {
    this.identifier = identifier;
    this.password = password;
  }
}
