package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for initiating password reset.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForgotPasswordRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  public String email;

  public ForgotPasswordRequest() {}
}
