package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Authentication response with JWT tokens and user profile.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"accessToken", "refreshToken", "user"})
public class AuthResponse {

  public String accessToken;
  public String refreshToken;
  public UserDto user;

  public AuthResponse() {}

  public AuthResponse(String accessToken, String refreshToken, UserDto user) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.user = user;
  }
}
