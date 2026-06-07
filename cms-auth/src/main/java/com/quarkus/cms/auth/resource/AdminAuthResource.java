package com.quarkus.cms.auth.resource;

import com.quarkus.cms.auth.dto.AuthResponse;
import com.quarkus.cms.auth.dto.ForgotPasswordRequest;
import com.quarkus.cms.auth.dto.LoginRequest;
import com.quarkus.cms.auth.dto.RefreshTokenRequest;
import com.quarkus.cms.auth.dto.RegisterRequest;
import com.quarkus.cms.auth.dto.ResetPasswordRequest;
import com.quarkus.cms.auth.dto.UserDto;
import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.security.CmsJwtGenerator;
import com.quarkus.cms.auth.security.CmsJwtGenerator.TokenPair;
import com.quarkus.cms.auth.service.AuthService;
import com.quarkus.cms.auth.service.AuthService.LoginResult;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import io.quarkus.logging.Log;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin authentication endpoints matching Strapi's admin auth API.
 *
 * <p>Handles login, registration, token refresh, password reset, and email confirmation. All
 * endpoints are unauthenticated (public) except refresh-token which requires a valid refresh token.
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Admin Auth", description = "Authentication endpoints for admin users")
public class AdminAuthResource {

  @Inject AuthService authService;

  @Inject CmsJwtGenerator jwtGenerator;

  @Inject JWTParser jwtParser;

  /**
   * Login with username/email and password.
   *
   * <p>Returns access and refresh JWT tokens plus the user profile.
   */
  @POST
  @Path("/login")
  @Operation(summary = "Login", description = "Authenticate with username/email and password")
  @APIResponse(
      responseCode = "200",
      description = "Login successful",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = AuthResponse.class)))
  @APIResponse(
      responseCode = "401",
      description = "Invalid credentials",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response login(@Valid LoginRequest request) {
    try {
      LoginResult result = authService.login(request.identifier, request.password);
      UserDto userDto = UserDto.from(result.user());
      AuthResponse response = new AuthResponse(result.accessToken(), result.refreshToken(), userDto);
      return Response.ok(response).build();
    } catch (SecurityException e) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(StrapiErrorResponse.of(401, "UnauthorizedError", e.getMessage()))
          .build();
    }
  }

  /**
   * Register a new admin user.
   *
   * <p>The user is created inactive until email confirmation (if configured). Returns the user
   * profile.
   */
  @POST
  @Path("/register")
  @Operation(summary = "Register", description = "Create a new admin user account")
  @APIResponse(
      responseCode = "200",
      description = "Registration successful",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = AuthResponse.class)))
  @APIResponse(
      responseCode = "400",
      description = "Validation error",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response register(@Valid RegisterRequest request) {
    try {
      CmsUser user =
          authService.register(
              request.username,
              request.email,
              request.password,
              request.firstName,
              request.lastName);
      TokenPair tokens = jwtGenerator.generateTokens(user);
      UserDto userDto = UserDto.from(user);
      AuthResponse response =
          new AuthResponse(tokens.accessToken(), tokens.refreshToken(), userDto);
      return Response.ok(response).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }

  /**
   * Refresh an access token using a valid refresh token.
   */
  @POST
  @Path("/refresh-token")
  @Operation(summary = "Refresh token", description = "Obtain a new access token using a refresh token")
  @APIResponse(
      responseCode = "200",
      description = "Token refreshed",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = AuthResponse.class)))
  @APIResponse(
      responseCode = "401",
      description = "Invalid refresh token",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response refreshToken(@Valid RefreshTokenRequest request) {
    try {
      JsonWebToken jwt = jwtParser.parse(request.refreshToken);
      Long userId = Long.parseLong(jwt.getSubject());
      String newAccessToken = authService.refreshAccessToken(userId);
      CmsUser user = CmsUser.findById(userId);

      if (user == null) {
        throw new SecurityException("User not found");
      }

      AuthResponse response = new AuthResponse(newAccessToken, request.refreshToken, UserDto.from(user));
      return Response.ok(response).build();
    } catch (ParseException | NumberFormatException | SecurityException e) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(StrapiErrorResponse.of(401, "UnauthorizedError", "Invalid refresh token"))
          .build();
    }
  }

  /**
   * Initiate password reset flow.
   */
  @POST
  @Path("/forgot-password")
  @Operation(summary = "Forgot password", description = "Request a password reset email")
  @APIResponse(responseCode = "200", description = "Password reset initiated")
  public Response forgotPassword(@Valid ForgotPasswordRequest request) {
    authService.initiatePasswordReset(request.email);
    return Response.ok(Map.of("ok", true)).build();
  }

  /**
   * Complete password reset with token.
   */
  @POST
  @Path("/reset-password")
  @Operation(summary = "Reset password", description = "Complete password reset with token")
  @APIResponse(responseCode = "200", description = "Password reset successful")
  @APIResponse(
      responseCode = "400",
      description = "Invalid or expired token",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response resetPassword(@Valid ResetPasswordRequest request) {
    try {
      authService.completePasswordReset(request.email, request.resetToken, request.newPassword);
      return Response.ok(Map.of("ok", true)).build();
    } catch (SecurityException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }

  /**
   * Confirm email address.
   */
  @POST
  @Path("/email-confirmation")
  @Operation(summary = "Confirm email", description = "Confirm a user's email address")
  @APIResponse(responseCode = "200", description = "Email confirmed")
  @APIResponse(
      responseCode = "400",
      description = "Invalid or expired token",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response confirmEmail(Map<String, String> body) {
    String email = body.get("email");
    String confirmationToken = body.get("confirmationToken");

    if (email == null || confirmationToken == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              StrapiErrorResponse.of(
                  400, "ValidationError", "Email and confirmationToken are required"))
          .build();
    }

    try {
      authService.confirmEmail(email, confirmationToken);
      return Response.ok(Map.of("ok", true)).build();
    } catch (SecurityException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }
}
