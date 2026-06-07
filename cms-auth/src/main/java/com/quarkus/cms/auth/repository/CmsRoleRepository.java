package com.quarkus.cms.auth.repository;

import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Repository for {@link CmsRole} RBAC role operations.
 *
 * <p>Manages role lifecycle (create, update, delete) and permission grants attached to roles.
 * Built-in roles (Administrator, Authenticated, Public) are protected from deletion.
 */
@ApplicationScoped
public class CmsRoleRepository {

  /** Default role codes that mirror Strapi's built-in roles. */
  public static final String ROLE_ADMINISTRATOR = "Administrator";
  public static final String ROLE_AUTHENTICATED = "Authenticated";
  public static final String ROLE_PUBLIC = "Public";

  /** Lists all roles. */
  public List<CmsRole> listAll() {
    return CmsRole.listAllRoles();
  }

  /** Finds a role by its unique code. */
  public CmsRole findByCode(String code) {
    return CmsRole.findByCode(code);
  }

  /** Finds a role by ID. */
  public CmsRole findById(Long id) {
    return CmsRole.findById(id);
  }

  /** Creates a new custom role. */
  @Transactional
  public CmsRole create(String code, String name, String description) {
    if (CmsRole.findByCode(code) != null) {
      throw new IllegalArgumentException("Role already exists: " + code);
    }
    CmsRole role = new CmsRole();
    role.code = code;
    role.name = name;
    role.description = description;
    role.persist();
    Log.infof("Created role: %s", code);
    return role;
  }

  /** Updates a role's metadata. */
  @Transactional
  public CmsRole update(Long roleId, String name, String description) {
    CmsRole role = findById(roleId);
    if (role == null) {
      throw new IllegalArgumentException("Role not found: " + roleId);
    }
    if (name != null) {
      role.name = name;
    }
    if (description != null) {
      role.description = description;
    }
    role.persist();
    return role;
  }

  /** Deletes a custom role (built-in roles cannot be deleted). */
  @Transactional
  public void delete(Long roleId) {
    CmsRole role = findById(roleId);
    if (role == null) {
      throw new IllegalArgumentException("Role not found: " + roleId);
    }
    if (isBuiltIn(role.code)) {
      throw new IllegalArgumentException("Cannot delete built-in role: " + role.code);
    }
    role.delete();
    Log.infof("Deleted role: %s (id=%d)", role.code, roleId);
  }

  /** Adds a permission grant to a role. */
  @Transactional
  public CmsPermission addPermission(
      Long roleId, String action, String subject,
      List<String> fields, Map<String, Object> conditions) {
    CmsRole role = findById(roleId);
    if (role == null) {
      throw new IllegalArgumentException("Role not found: " + roleId);
    }
    CmsPermission perm = new CmsPermission();
    perm.action = action;
    perm.subject = subject;
    perm.fields = fields;
    perm.conditions = conditions;
    perm.role = role;
    perm.persist();
    role.permissions.add(perm);
    Log.infof("Added permission %s to role %s", action, role.code);
    return perm;
  }

  /** Removes a permission grant from a role. */
  @Transactional
  public void removePermission(Long permissionId) {
    CmsPermission perm = CmsPermission.findById(permissionId);
    if (perm == null) {
      throw new IllegalArgumentException("Permission not found: " + permissionId);
    }
    perm.delete();
    Log.infof("Removed permission: %d", permissionId);
  }

  /** Lists all permissions for a role. */
  public List<CmsPermission> listPermissions(Long roleId) {
    return CmsPermission.findByRole(roleId);
  }

  /** Seeds built-in roles if they don't exist. */
  @Transactional
  public void seedDefaultRoles() {
    ensureRole(ROLE_ADMINISTRATOR, "Administrator",
        "Complete access to all features and settings");
    ensureRole(ROLE_AUTHENTICATED, "Authenticated",
        "Default role for authenticated users");
    ensureRole(ROLE_PUBLIC, "Public",
        "Default role for unauthenticated visitors");
  }

  private void ensureRole(String code, String name, String description) {
    if (CmsRole.findByCode(code) == null) {
      CmsRole role = new CmsRole();
      role.code = code;
      role.name = name;
      role.description = description;
      role.persist();
      Log.infof("Seeded built-in role: %s", code);
    }
  }

  /** Checks if a role code is a built-in (non-deletable) role. */
  public static boolean isBuiltIn(String code) {
    return ROLE_ADMINISTRATOR.equals(code)
        || ROLE_AUTHENTICATED.equals(code)
        || ROLE_PUBLIC.equals(code);
  }
}
