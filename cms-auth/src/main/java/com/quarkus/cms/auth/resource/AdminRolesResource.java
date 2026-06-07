package com.quarkus.cms.auth.resource;

import com.quarkus.cms.auth.dto.CreateRoleRequest;
import com.quarkus.cms.auth.dto.RoleDto;
import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.core.security.PermissionCheck;
import com.quarkus.cms.rest.dto.StrapiCollectionResponse;
import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import com.quarkus.cms.rest.dto.StrapiSingleResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
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
 * Admin role and permission management endpoints matching Strapi's admin roles API.
 *
 * <p>Provides CRUD for roles and their permission grants.
 */
@Path("/admin/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Admin Roles", description = "Role and permission management endpoints")
public class AdminRolesResource {

  /**
   * List all roles with their permissions.
   */
  @GET
  @PermissionCheck("admin::roles.read")
  @Operation(summary = "List roles", description = "Returns all roles with their permissions")
  @APIResponse(
      responseCode = "200",
      description = "List of roles",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiCollectionResponse.class)))
  public Response listRoles() {
    List<CmsRole> roles = CmsRole.listAllRoles();
    List<RoleDto> dtos = roles.stream().map(RoleDto::from).toList();
    return Response.ok(new StrapiCollectionResponse<>(dtos)).build();
  }

  /**
   * Get a single role by ID with its permissions.
   */
  @GET
  @Path("/{id}")
  @PermissionCheck("admin::roles.read")
  @Operation(summary = "Get role", description = "Returns a single role by ID")
  @APIResponse(
      responseCode = "200",
      description = "Role found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  @APIResponse(
      responseCode = "404",
      description = "Role not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response getRole(@PathParam("id") Long id) {
    CmsRole role = CmsRole.findById(id);
    if (role == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", "Role not found: " + id))
          .build();
    }
    return Response.ok(new StrapiSingleResponse<>(RoleDto.from(role))).build();
  }

  /**
   * Create a new role with permissions.
   */
  @POST
  @PermissionCheck("admin::roles.create")
  @Transactional
  @Operation(summary = "Create role", description = "Create a new role with permission grants")
  @APIResponse(
      responseCode = "201",
      description = "Role created",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  @APIResponse(
      responseCode = "400",
      description = "Validation error or duplicate code",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response createRole(@Valid CreateRoleRequest request) {
    // Check for duplicate code
    CmsRole existing = CmsRole.findByCode(request.code);
    if (existing != null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              StrapiErrorResponse.of(
                  400, "ValidationError", "Role code already exists: " + request.code))
          .build();
    }

    CmsRole role = new CmsRole();
    role.code = request.code;
    role.name = request.name;
    role.description = request.description;

    // Attach permissions
    if (request.permissions != null) {
      for (CreateRoleRequest.PermissionInput input : request.permissions) {
        CmsPermission perm = new CmsPermission();
        perm.action = input.action;
        perm.subject = input.subject;
        perm.fields = input.fields;
        perm.conditions = input.conditions;
        perm.role = role;
        role.permissions.add(perm);
      }
    }

    role.persist();
    Log.infof("Created role: %s", role.code);
    return Response.status(Response.Status.CREATED)
        .entity(new StrapiSingleResponse<>(RoleDto.from(role)))
        .build();
  }

  /**
   * Update a role and its permissions.
   */
  @PUT
  @Path("/{id}")
  @PermissionCheck("admin::roles.update")
  @Transactional
  @Operation(summary = "Update role", description = "Update a role's metadata and permissions")
  @APIResponse(
      responseCode = "200",
      description = "Role updated",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiSingleResponse.class)))
  @APIResponse(
      responseCode = "404",
      description = "Role not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response updateRole(
      @PathParam("id") Long id, @Valid CreateRoleRequest request) {
    CmsRole role = CmsRole.findById(id);
    if (role == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", "Role not found: " + id))
          .build();
    }

    role.name = request.name;
    if (request.description != null) {
      role.description = request.description;
    }

    // Replace permissions entirely
    if (request.permissions != null) {
      // Clear existing
      CmsPermission.delete("role.id", role.id);
      role.permissions.clear();

      for (CreateRoleRequest.PermissionInput input : request.permissions) {
        CmsPermission perm = new CmsPermission();
        perm.action = input.action;
        perm.subject = input.subject;
        perm.fields = input.fields;
        perm.conditions = input.conditions;
        perm.role = role;
        role.permissions.add(perm);
      }
    }

    role.persist();
    Log.infof("Updated role: %s", role.code);
    return Response.ok(new StrapiSingleResponse<>(RoleDto.from(role))).build();
  }

  /**
   * Delete a role.
   */
  @DELETE
  @Path("/{id}")
  @PermissionCheck("admin::roles.delete")
  @Transactional
  @Operation(summary = "Delete role", description = "Permanently delete a role and its permissions")
  @APIResponse(responseCode = "200", description = "Role deleted")
  @APIResponse(
      responseCode = "404",
      description = "Role not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = StrapiErrorResponse.class)))
  public Response deleteRole(@PathParam("id") Long id) {
    boolean deleted = CmsRole.deleteById(id);
    if (!deleted) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(StrapiErrorResponse.of(404, "NotFoundError", "Role not found: " + id))
          .build();
    }
    Log.infof("Deleted role: %d", id);
    return Response.ok(Map.of("id", id, "deleted", true)).build();
  }
}
