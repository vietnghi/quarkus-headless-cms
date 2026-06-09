package com.quarkus.cms.admin.ui.resource;

import com.quarkus.cms.auth.dto.RoleDto;
import com.quarkus.cms.auth.dto.UserDto;
import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.repository.CmsRoleRepository;
import com.quarkus.cms.auth.repository.CmsUserRepository;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import io.quarkus.logging.Log;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side rendered admin user and role management pages.
 *
 * <p>Provides CRUD UI for managing admin users and RBAC roles, using Qute templates,
 * HTMX, and Alpine.js. All operations call the underlying cms-auth services directly.
 */
@Path("/admin")
@Produces(MediaType.TEXT_HTML)
public class AdminUserManagementUiResource {

    // ---- Injected Services ---- //

    @Inject CmsUserRepository userRepository;
    @Inject CmsRoleRepository roleRepository;
    @Inject SchemaStorageService schemaStorageService;

    // ---- Injected Templates ---- //

    @Inject @Location("admin/users/list.html")        Template usersList;
    @Inject @Location("admin/users/edit.html")         Template userEdit;
    @Inject @Location("admin/roles/list.html")         Template rolesList;
    @Inject @Location("admin/roles/edit.html")         Template roleEdit;

    // ========================================================================
    // USER MANAGEMENT
    // ========================================================================

    @GET @Path("/users-ui")
    public TemplateInstance listUsers(
            @QueryParam("search") String search,
            @QueryParam("role") Long roleId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize) {

        List<CmsUser> allUsers;
        if (search != null && !search.isBlank()) {
            allUsers = CmsUser.list("isActive = true and (username like ?1 or email like ?1 or firstName like ?1 or lastName like ?1)",
                    "%" + search.trim() + "%");
        } else {
            allUsers = userRepository.listActive();
        }

        // Filter by role
        if (roleId != null) {
            allUsers = allUsers.stream()
                    .filter(u -> u.roles.stream().anyMatch(r -> r.id.equals(roleId)))
                    .collect(Collectors.toList());
        }

        // Client-side sort by createdAt descending
        allUsers.sort((a, b) -> b.createdAt.compareTo(a.createdAt));

        int total = allUsers.size();
        int fromIndex = Math.min(page * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<CmsUser> pageUsers = allUsers.subList(fromIndex, toIndex);

        List<UserDto> users = pageUsers.stream().map(UserDto::from).collect(Collectors.toList());
        int pageCount = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;

        List<CmsRole> allRoles = roleRepository.listAll();
        List<RoleDto> roles = allRoles.stream().map(RoleDto::from).collect(Collectors.toList());

        return usersList
                .data("title", "Users")
                .data("users", users)
                .data("roles", roles)
                .data("total", total)
                .data("page", page)
                .data("pageSize", pageSize)
                .data("pageCount", pageCount)
                .data("search", search)
                .data("selectedRoleId", roleId);
    }

    @GET @Path("/users-ui/create")
    public TemplateInstance createUserForm() {
        List<CmsRole> allRoles = roleRepository.listAll();
        List<RoleDto> roles = allRoles.stream().map(RoleDto::from).collect(Collectors.toList());

        return userEdit
                .data("title", "Create User")
                .data("user", null)
                .data("roles", roles)
                .data("isNew", true)
                .data("selectedRoleIds", List.of())
                .data("flashMessage", null)
                .data("errorMessage", null);
    }

    @GET @Path("/users-ui/{id}")
    public TemplateInstance editUserForm(@PathParam("id") Long id) {
        CmsUser user = CmsUser.findById(id);
        if (user == null) {
            throw new jakarta.ws.rs.NotFoundException("User not found: " + id);
        }

        List<CmsRole> allRoles = roleRepository.listAll();
        List<RoleDto> roles = allRoles.stream().map(RoleDto::from).collect(Collectors.toList());
        List<Long> selectedRoleIds = user.roles.stream().map(r -> r.id).collect(Collectors.toList());

        UserDto userDto = UserDto.from(user);

        // Determinate which content types this user has access to via their roles
        List<ContentTypeDefinition> allContentTypes = schemaStorageService.getAllContentTypes();
        Set<String> userPermissions = new HashSet<>();
        for (CmsRole role : user.roles) {
            for (CmsPermission perm : role.permissions) {
                userPermissions.add(perm.action);
            }
        }

        return userEdit
                .data("title", "Edit User: " + (user.firstName != null ? user.firstName + " " + (user.lastName != null ? user.lastName : "") : user.username))
                .data("user", userDto)
                .data("roles", roles)
                .data("isNew", false)
                .data("selectedRoleIds", selectedRoleIds)
                .data("contentTypes", allContentTypes)
                .data("userPermissions", userPermissions)
                .data("flashMessage", null)
                .data("errorMessage", null);
    }

    @POST @Path("/users-ui/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createUser(
            @FormParam("username") String username,
            @FormParam("email") String email,
            @FormParam("password") String password,
            @FormParam("firstName") String firstName,
            @FormParam("lastName") String lastName,
            @FormParam("roleIds") List<Long> roleIds) {

        try {
            CmsUser user = userRepository.create(username, email, password, firstName, lastName);

            // Assign roles
            if (roleIds != null) {
                for (Long roleId : roleIds) {
                    CmsRole role = CmsRole.findById(roleId);
                    if (role != null) {
                        user.roles.add(role);
                    }
                }
                user.persist();
            }

            Log.infof("Admin UI created user: %s", user.username);
            return Response.seeOther(URI.create("/admin/users-ui/" + user.id + "?created=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/users-ui/create?error=" + e.getMessage()))
                    .build();
        }
    }

    @POST @Path("/users-ui/{id}/update")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateUser(
            @PathParam("id") Long id,
            @FormParam("firstName") String firstName,
            @FormParam("lastName") String lastName,
            @FormParam("email") String email,
            @FormParam("preferredLocale") String preferredLocale,
            @FormParam("isActive") String isActive,
            @FormParam("isBlocked") String isBlocked,
            @FormParam("newPassword") String newPassword,
            @FormParam("roleIds") List<Long> roleIds) {

        CmsUser user = CmsUser.findById(id);
        if (user == null) {
            return Response.seeOther(URI.create("/admin/users-ui?error=User%20not%20found"))
                    .build();
        }

        try {
            // Update profile
            userRepository.updateProfile(id, firstName, lastName, email);

            if (preferredLocale != null) {
                user.preferredLocale = preferredLocale;
            }
            if (isActive != null) {
                user.isActive = "true".equals(isActive) || "on".equals(isActive);
            }
            if (isBlocked != null) {
                user.isBlocked = "true".equals(isBlocked) || "on".equals(isBlocked);
            }

            // Update role assignments
            if (roleIds != null) {
                user.roles.clear();
                for (Long roleId : roleIds) {
                    CmsRole role = CmsRole.findById(roleId);
                    if (role != null) {
                        user.roles.add(role);
                    }
                }
            }

            // Change password if provided
            if (newPassword != null && !newPassword.isBlank()) {
                userRepository.changePassword(id, newPassword);
            }

            user.persist();
            Log.infof("Admin UI updated user: %d", id);
            return Response.seeOther(URI.create("/admin/users-ui/" + id + "?saved=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/users-ui/" + id + "?error=" + e.getMessage()))
                    .build();
        }
    }

    @POST @Path("/users-ui/{id}/delete")
    public Response deleteUser(@PathParam("id") Long id) {
        try {
            userRepository.deactivate(id);
            return Response.seeOther(URI.create("/admin/users-ui?deleted=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/users-ui?error=" + e.getMessage()))
                    .build();
        }
    }

    @POST @Path("/users-ui/{id}/block")
    public Response blockUser(@PathParam("id") Long id) {
        try {
            userRepository.block(id);
            return Response.seeOther(URI.create("/admin/users-ui/" + id + "?blocked=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/users-ui/" + id + "?error=" + e.getMessage()))
                    .build();
        }
    }

    @POST @Path("/users-ui/{id}/unblock")
    public Response unblockUser(@PathParam("id") Long id) {
        try {
            userRepository.unblock(id);
            return Response.seeOther(URI.create("/admin/users-ui/" + id + "?unblocked=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/users-ui/" + id + "?error=" + e.getMessage()))
                    .build();
        }
    }

    // ========================================================================
    // ROLE MANAGEMENT
    // ========================================================================

    @GET @Path("/roles-ui")
    public TemplateInstance listRoles() {
        List<CmsRole> allRoles = roleRepository.listAll();
        List<RoleDto> roles = allRoles.stream()
                .map(RoleDto::from)
                .collect(Collectors.toList());

        return rolesList
                .data("title", "Roles")
                .data("roles", roles)
                .data("builtInCodes", Set.of(
                    CmsRoleRepository.ROLE_ADMINISTRATOR,
                    CmsRoleRepository.ROLE_AUTHENTICATED,
                    CmsRoleRepository.ROLE_PUBLIC))
                .data("flashMessage", null)
                .data("errorMessage", null);
    }

    @GET @Path("/roles-ui/create")
    public TemplateInstance createRoleForm() {
        return roleEdit
                .data("title", "Create Role")
                .data("role", null)
                .data("isNew", true)
                .data("permissionActions", getKnownPermissionActions())
                .data("selectedPermissions", List.of())
                .data("flashMessage", null)
                .data("errorMessage", null);
    }

    @GET @Path("/roles-ui/{id}")
    public TemplateInstance editRoleForm(@PathParam("id") Long id) {
        CmsRole role = CmsRole.findById(id);
        if (role == null) {
            throw new jakarta.ws.rs.NotFoundException("Role not found: " + id);
        }

        RoleDto roleDto = RoleDto.from(role);
        List<String> selectedPermissions = role.permissions.stream()
                .map(p -> p.action)
                .collect(Collectors.toList());

        return roleEdit
                .data("title", "Edit Role: " + role.name)
                .data("role", roleDto)
                .data("isNew", false)
                .data("permissionActions", getKnownPermissionActions())
                .data("selectedPermissions", selectedPermissions)
                .data("isBuiltIn", CmsRoleRepository.isBuiltIn(role.code))
                .data("flashMessage", null)
                .data("errorMessage", null);
    }

    @POST @Path("/roles-ui/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createRole(
            @FormParam("code") String code,
            @FormParam("name") String name,
            @FormParam("description") String description,
            @FormParam("permissions") List<String> permissions) {

        try {
            CmsRole role = roleRepository.create(code, name, description);

            // Attach permissions
            if (permissions != null) {
                for (String action : permissions) {
                    if (action != null && !action.isBlank()) {
                        roleRepository.addPermission(role.id, action, null, null, null);
                    }
                }
            }

            Log.infof("Admin UI created role: %s", role.code);
            return Response.seeOther(URI.create("/admin/roles-ui/" + role.id + "?created=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/roles-ui/create?error=" + e.getMessage()))
                    .build();
        }
    }

    @POST @Path("/roles-ui/{id}/update")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateRole(
            @PathParam("id") Long id,
            @FormParam("name") String name,
            @FormParam("description") String description,
            @FormParam("permissions") List<String> permissions) {

        CmsRole role = CmsRole.findById(id);
        if (role == null) {
            return Response.seeOther(URI.create("/admin/roles-ui?error=Role%20not%20found"))
                    .build();
        }

        try {
            roleRepository.update(id, name, description);

            // Replace permissions entirely
            if (permissions != null) {
                // Clear existing
                CmsPermission.delete("role.id", role.id);
                role.permissions.clear();

                for (String action : permissions) {
                    if (action != null && !action.isBlank()) {
                        roleRepository.addPermission(role.id, action, null, null, null);
                    }
                }
            }

            Log.infof("Admin UI updated role: %s", role.code);
            return Response.seeOther(URI.create("/admin/roles-ui/" + id + "?saved=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/roles-ui/" + id + "?error=" + e.getMessage()))
                    .build();
        }
    }

    @POST @Path("/roles-ui/{id}/delete")
    public Response deleteRole(@PathParam("id") Long id) {
        try {
            roleRepository.delete(id);
            return Response.seeOther(URI.create("/admin/roles-ui?deleted=true"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.seeOther(URI.create("/admin/roles-ui?error=" + e.getMessage()))
                    .build();
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Builds a structured list of known permission actions organized by domain.
     * These are displayed as checkboxes in the role edit form.
     */
    private List<PermissionActionGroup> getKnownPermissionActions() {
        List<PermissionActionGroup> groups = new ArrayList<>();

        // Admin permissions
        List<PermissionActionItem> adminPerms = List.of(
                new PermissionActionItem("admin::users.read", "Users", "View users"),
                new PermissionActionItem("admin::users.create", "Users", "Create users"),
                new PermissionActionItem("admin::users.update", "Users", "Update users"),
                new PermissionActionItem("admin::users.delete", "Users", "Delete users"),
                new PermissionActionItem("admin::roles.read", "Roles", "View roles"),
                new PermissionActionItem("admin::roles.create", "Roles", "Create roles"),
                new PermissionActionItem("admin::roles.update", "Roles", "Update roles"),
                new PermissionActionItem("admin::roles.delete", "Roles", "Delete roles"));
        groups.add(new PermissionActionGroup("Administration", "admin", adminPerms));

        // API Token permissions
        List<PermissionActionItem> tokenPerms = List.of(
                new PermissionActionItem("admin::api-tokens.read", "API Tokens", "View API tokens"),
                new PermissionActionItem("admin::api-tokens.create", "API Tokens", "Create API tokens"),
                new PermissionActionItem("admin::api-tokens.update", "API Tokens", "Update API tokens"),
                new PermissionActionItem("admin::api-tokens.delete", "API Tokens", "Delete API tokens"));
        groups.add(new PermissionActionGroup("API Tokens", "api-tokens", tokenPerms));

        // Content Type Builder permissions
        List<PermissionActionItem> ctbPerms = List.of(
                new PermissionActionItem("admin::content-type-builder.read", "Content Type Builder", "View content types"),
                new PermissionActionItem("admin::content-type-builder.create", "Content Type Builder", "Create content types"),
                new PermissionActionItem("admin::content-type-builder.update", "Content Type Builder", "Update content types"),
                new PermissionActionItem("admin::content-type-builder.delete", "Content Type Builder", "Delete content types"));
        groups.add(new PermissionActionGroup("Content Type Builder", "content-type-builder", ctbPerms));

        // Media Library permissions
        List<PermissionActionItem> mediaPerms = List.of(
                new PermissionActionItem("admin::media.read", "Media Library", "View media files"),
                new PermissionActionItem("admin::media.create", "Media Library", "Upload media files"),
                new PermissionActionItem("admin::media.update", "Media Library", "Update media files"),
                new PermissionActionItem("admin::media.delete", "Media Library", "Delete media files"));
        groups.add(new PermissionActionGroup("Media Library", "media", mediaPerms));

        // Audit Log permissions
        List<PermissionActionItem> auditPerms = List.of(
                new PermissionActionItem("admin::audit-logs.read", "Audit Log", "View audit logs"));
        groups.add(new PermissionActionGroup("Audit Log", "audit-log", auditPerms));

        // I18N permissions
        List<PermissionActionItem> i18nPerms = List.of(
                new PermissionActionItem("admin::i18n.read", "Internationalization", "View locales"),
                new PermissionActionItem("admin::i18n.update", "Internationalization", "Manage locales"));
        groups.add(new PermissionActionGroup("Internationalization", "i18n", i18nPerms));

        // Webhooks permissions
        List<PermissionActionItem> webhookPerms = List.of(
                new PermissionActionItem("admin::webhooks.read", "Webhooks", "View webhooks"),
                new PermissionActionItem("admin::webhooks.create", "Webhooks", "Create webhooks"),
                new PermissionActionItem("admin::webhooks.update", "Webhooks", "Update webhooks"),
                new PermissionActionItem("admin::webhooks.delete", "Webhooks", "Delete webhooks"));
        groups.add(new PermissionActionGroup("Webhooks", "webhooks", webhookPerms));

        // Content permissions — dynamic per content type
        List<ContentTypeDefinition> contentTypes = schemaStorageService.getAllContentTypes();
        List<PermissionActionItem> contentPerms = new ArrayList<>();
        for (ContentTypeDefinition ct : contentTypes) {
            String base = "api::" + ct.getUid();
            contentPerms.add(new PermissionActionItem(base + ".create", ct.getDisplayName(), "Create entries"));
            contentPerms.add(new PermissionActionItem(base + ".read", ct.getDisplayName(), "Read entries"));
            contentPerms.add(new PermissionActionItem(base + ".update", ct.getDisplayName(), "Update entries"));
            contentPerms.add(new PermissionActionItem(base + ".delete", ct.getDisplayName(), "Delete entries"));
            if (ct.isDraftAndPublish()) {
                contentPerms.add(new PermissionActionItem(base + ".publish", ct.getDisplayName(), "Publish entries"));
            }
        }
        if (!contentPerms.isEmpty()) {
            groups.add(new PermissionActionGroup("Content Types", "content-types", contentPerms));
        }

        return groups;
    }

    /** A group of related permission actions (displayed as a section in the form). */
    public static class PermissionActionGroup {
        public String label;
        public String id;
        public List<PermissionActionItem> items;

        public PermissionActionGroup() {}

        public PermissionActionGroup(String label, String id, List<PermissionActionItem> items) {
            this.label = label;
            this.id = id;
            this.items = items;
        }
    }

    /** A single permission action item (displayed as a checkbox). */
    public static class PermissionActionItem {
        public String action;
        public String label;
        public String description;

        public PermissionActionItem() {}

        public PermissionActionItem(String action, String label, String description) {
            this.action = action;
            this.label = label;
            this.description = description;
        }
    }
}
