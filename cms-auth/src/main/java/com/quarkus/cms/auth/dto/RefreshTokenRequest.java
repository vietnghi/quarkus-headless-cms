package com.quarkus.cms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for refreshing an access token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefreshTokenRequest {

  @NotBlank(message = "Refresh token is required")
  public String refreshToken;

  public RefreshTokenRequest() {}
}
