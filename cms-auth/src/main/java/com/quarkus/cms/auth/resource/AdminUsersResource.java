package com.quarkus.cms.auth.resource;

import com.quarkus.cms.auth.dto.RegisterRequest;
import com.quarkus.cms.auth.dto.UpdateUserRequest;
import com.quarkus.cms.auth.dto.UserDto;
import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.repository.CmsUserRepository;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin user management endpoints matching Strapi's admin user API.
 *
 * <p>Provides CRUD operations for CMS admin users. All endpoints require authentication and the
 * "admin::users" permission.
 */
@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Admin Users", description = "User management endpoints")
public class AdminUsersResource {

  @Inject CmsUserRepository userRepository;

  /**
   * List all active users.
   */
  @GET
  @PermissionCheck("admin::users.read")
  @Operation(summary = "List users", description = "Returns all active admin users")
  @APIResponse(
      responseCode = "200",
      description = "List of users",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiCollectionResponse.class)))
  public Response listUsers() {
    List<CmsUser> users = userRepository.listActive();
    List<UserDto> dtos = users.stream().map(UserDto::from).toList();
    return Response.ok(new StrapiCollectionResponse<>(dtos)).build();
  }

  /**
   * Get a single user by ID.
   */
  @GET
  @Path("/{id}")
  @PermissionCheck("admin::users.read")
  @Operation(summary = "Get user", description = "Returns a single user by ID")
  @APIResponse(
      responseCode = "200",
      description = "User found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  @APIResponse(
      responseCode = "404",
      description = "User not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response getUser(@PathParam("id") Long id) {
    CmsUser user = CmsUser.findById(id);
    if (user == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", "User not found: " + id))
          .build();
    }
    return Response.ok(new StrapiSingleResponse<>(UserDto.from(user))).build();
  }

  /**
   * Create a new user (admin-level).
   */
  @POST
  @PermissionCheck("admin::users.create")
  @Operation(summary = "Create user", description = "Create a new admin user")
  @APIResponse(
      responseCode = "201",
      description = "User created",
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
  public Response createUser(@Valid RegisterRequest request) {
    try {
      CmsUser user =
          userRepository.create(
              request.username,
              request.email,
              request.password,
              request.firstName,
              request.lastName);

      // Assign the Authenticated role by default
      CmsRole authenticatedRole = CmsRole.findByCode("Authenticated");
      if (authenticatedRole != null) {
        user.roles.add(authenticatedRole);
        user.persist();
      }

      Log.infof("Admin created user: %s", user.username);
      return Response.status(Response.Status.CREATED)
          .entity(new StrapiSingleResponse<>(UserDto.from(user)))
          .build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }

  /**
   * Update a user's profile.
   */
  @PUT
  @Path("/{id}")
  @PermissionCheck("admin::users.update")
  @Operation(summary = "Update user", description = "Update a user's profile fields")
  @APIResponse(
      responseCode = "200",
      description = "User updated",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  @APIResponse(
      responseCode = "404",
      description = "User not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response updateUser(
      @PathParam("id") Long id, @Valid UpdateUserRequest request) {
    CmsUser user = CmsUser.findById(id);
    if (user == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", "User not found: " + id))
          .build();
    }

    try {
      userRepository.updateProfile(id, request.firstName, request.lastName, null);

      if (request.preferredLocale != null) {
        user.preferredLocale = request.preferredLocale;
      }
      if (request.isActive != null) {
        user.isActive = request.isActive;
      }
      if (request.isBlocked != null) {
        user.isBlocked = request.isBlocked;
      }

      // Update role assignments if provided
      if (request.roleIds != null) {
        user.roles.clear();
        for (Long roleId : request.roleIds) {
          CmsRole role = CmsRole.findById(roleId);
          if (role != null) {
            user.roles.add(role);
          }
        }
      }

      user.persist();
      Log.infof("Updated user: %d", user.id);
      return Response.ok(new StrapiSingleResponse<>(UserDto.from(user))).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(StrapiErrorResponse.of(400, "ValidationError", e.getMessage()))
          .build();
    }
  }

  /**
   * Delete (deactivate) a user.
   */
  @DELETE
  @Path("/{id}")
  @PermissionCheck("admin::users.delete")
  @Operation(summary = "Delete user", description = "Deactivate a user account")
  @APIResponse(responseCode = "200", description = "User deactivated")
  @APIResponse(
      responseCode = "404",
      description = "User not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response deleteUser(@PathParam("id") Long id) {
    try {
      userRepository.deactivate(id);
      return Response.ok(Map.of("id", id, "deactivated", true)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
          .build();
    }
  }

  /**
   * Block a user (prevent login).
   */
  @POST
  @Path("/{id}/block")
  @PermissionCheck("admin::users.update")
  @Operation(summary = "Block user", description = "Block a user from logging in")
  @APIResponse(responseCode = "200", description = "User blocked")
  public Response blockUser(@PathParam("id") Long id) {
    try {
      userRepository.block(id);
      return Response.ok(Map.of("id", id, "blocked", true)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
          .build();
    }
  }

  /**
   * Unblock a user.
   */
  @POST
  @Path("/{id}/unblock")
  @PermissionCheck("admin::users.update")
  @Operation(summary = "Unblock user", description = "Unblock a previously blocked user")
  @APIResponse(responseCode = "200", description = "User unblocked")
  public Response unblockUser(@PathParam("id") Long id) {
    try {
      userRepository.unblock(id);
      return Response.ok(Map.of("id", id, "unblocked", true)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", e.getMessage()))
          .build();
    }
  }
}
