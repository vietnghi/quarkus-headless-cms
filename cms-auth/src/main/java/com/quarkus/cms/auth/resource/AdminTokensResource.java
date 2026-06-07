package com.quarkus.cms.auth.resource;

import com.quarkus.cms.auth.dto.ApiTokenDto;
import com.quarkus.cms.auth.dto.CreateApiTokenRequest;
import com.quarkus.cms.auth.entity.CmsApiToken;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.auth.service.ApiTokenService;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin API token management endpoints.
 *
 * <p>Provides CRUD for API tokens used for client-side (non-admin) authentication. Tokens can be
 * full-access or custom-scoped.
 */
@Path("/admin/api-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Admin API Tokens", description = "API token management endpoints")
public class AdminTokensResource {

  @Inject ApiTokenService apiTokenService;

  @Inject SecurityIdentity identity;

  /**
   * List all active API tokens.
   */
  @GET
  @PermissionCheck("admin::api-tokens.read")
  @Operation(summary = "List tokens", description = "Returns all active API tokens")
  @APIResponse(
      responseCode = "200",
      description = "List of tokens",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiCollectionResponse.class)))
  public Response listTokens() {
    List<CmsApiToken> tokens = apiTokenService.listActive();
    List<ApiTokenDto> dtos = tokens.stream().map(ApiTokenDto::from).toList();
    return Response.ok(new StrapiCollectionResponse<>(dtos)).build();
  }

  /**
   * Get a single API token by ID.
   */
  @GET
  @Path("/{id}")
  @PermissionCheck("admin::api-tokens.read")
  @Operation(summary = "Get token", description = "Returns a single API token by ID")
  @APIResponse(
      responseCode = "200",
      description = "Token found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  @APIResponse(
      responseCode = "404",
      description = "Token not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response getToken(@PathParam("id") Long id) {
    CmsApiToken token = CmsApiToken.findById(id);
    if (token == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", "API token not found: " + id))
          .build();
    }
    return Response.ok(new StrapiSingleResponse<>(ApiTokenDto.from(token))).build();
  }

  /**
   * Create a new API token. Returns the raw token value (only shown once).
   */
  @POST
  @PermissionCheck("admin::api-tokens.create")
  @Operation(
      summary = "Create token",
      description =
          "Create a new API token. The raw token value is returned only once — store it securely.")
  @APIResponse(
      responseCode = "201",
      description = "Token created with raw accessKey",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  @APIResponse(
      responseCode = "400",
      description = "Validation error",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response createToken(@Valid CreateApiTokenRequest request) {
    // Resolve the creating user from security context
    com.quarkus.cms.auth.entity.CmsUser createdBy =
        resolveCurrentUser();
    if (createdBy == null) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(
              StrapiErrorResponse.of(
                  401, "UnauthorizedError", "Must be authenticated to create tokens"))
          .build();
    }

    Instant expiresAt = null;
    if (request.expiresAt != null && !request.expiresAt.isBlank()) {
      try {
        expiresAt = Instant.parse(request.expiresAt);
      } catch (Exception e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                StrapiErrorResponse.of(
                    400, "ValidationError", "Invalid expiresAt format. Use ISO-8601."))
            .build();
      }
    }

    String rawToken =
        apiTokenService.createToken(
            request.name, request.type, request.description, createdBy, expiresAt);

    // Find the created token to include in response
    List<CmsApiToken> active = apiTokenService.listActive();
    CmsApiToken created = null;
    for (CmsApiToken t : active) {
      if (t.name.equals(request.name) && t.createdBy != null
          && t.createdBy.id.equals(createdBy.id)) {
        created = t;
        break;
      }
    }

    ApiTokenDto dto = created != null ? ApiTokenDto.from(created) : new ApiTokenDto();
    dto.accessKey = rawToken;

    Log.infof("Created API token: %s", request.name);
    return Response.status(Response.Status.CREATED)
        .entity(new StrapiSingleResponse<>(dto))
        .build();
  }

  /**
   * Revoke (deactivate) an API token.
   */
  @DELETE
  @Path("/{id}")
  @PermissionCheck("admin::api-tokens.delete")
  @Operation(summary = "Revoke token", description = "Revoke (deactivate) an API token")
  @APIResponse(responseCode = "200", description = "Token revoked")
  @APIResponse(
      responseCode = "404",
      description = "Token not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response revokeToken(@PathParam("id") Long id) {
    try {
      apiTokenService.revoke(id);
      return Response.ok(Map.of("id", id, "revoked", true)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
          .build();
    }
  }

  /**
   * Resolves the current CMS user from the security context.
   */
  private com.quarkus.cms.auth.entity.CmsUser resolveCurrentUser() {
    if (identity == null || identity.isAnonymous()) {
      return null;
    }
    if (identity.getPrincipal() instanceof JsonWebToken jwt) {
      String subject = jwt.getSubject();
      if (subject != null) {
        try {
          Long userId = Long.parseLong(subject);
          return com.quarkus.cms.auth.entity.CmsUser.findById(userId);
        } catch (NumberFormatException e) {
          Log.debugf("JWT subject is not a numeric user ID: %s", subject);
        }
      }
    }
    return null;
  }
}
