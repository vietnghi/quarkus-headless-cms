package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * User registration request body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterRequest {

  @NotBlank(message = "Username is required")
  @Size(min = 3, max = 100, message = "Username must be 3-100 characters")
  public String username;

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  public String email;

  @NotBlank(message = "Password is required")
  @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
  public String password;

  @Size(max = 100)
  public String firstName;

  @Size(max = 100)
  public String lastName;

  public RegisterRequest() {}
}
