package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for completing password reset.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResetPasswordRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  public String email;

  @NotBlank(message = "Reset token is required")
  public String resetToken;

  @NotBlank(message = "New password is required")
  @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
  public String newPassword;

  public ResetPasswordRequest() {}
}
