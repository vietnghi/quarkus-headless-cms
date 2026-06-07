package com.quarkus.cms.auth.repository;

import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Repository for {@link CmsPermission} RBAC permission grant operations.
 *
 * <p>Each permission declares an action (e.g. "api::article.article.create") on an optional
 * subject with optional field-level and condition constraints. Permissions are always attached
 * to a role and follow it through cascading operations.
 */
@ApplicationScoped
public class CmsPermissionRepository {

  /** Finds a permission by its ID. */
  public CmsPermission findById(Long id) {
    return CmsPermission.findById(id);
  }

  /** Lists all permissions for a given role. */
  public List<CmsPermission> findByRole(Long roleId) {
    return CmsPermission.findByRole(roleId);
  }

  /** Lists all permissions that grant a specific action. */
  public List<CmsPermission> findByAction(String action) {
    return CmsPermission.findByAction(action);
  }

  /**
   * Creates a new permission grant attached to a role.
   *
   * @param roleId     the role to attach the permission to
   * @param action     the action string (e.g. "api::article.article.read")
   * @param subject    optional subject qualifier (content-type UID)
   * @param fields     optional field-level restrictions (null = all fields)
   * @param conditions optional JSON conditions map
   * @return the persisted permission
   */
  @Transactional
  public CmsPermission create(
      Long roleId, String action, String subject,
      List<String> fields, Map<String, Object> conditions) {
    CmsRole role = CmsRole.findById(roleId);
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
    Log.debugf("Created permission %s on role %s", action, role.code);
    return perm;
  }

  /**
   * Creates a permission with a pre-resolved role reference.
   *
   * @param role       the role entity to attach the permission to
   * @param action     the action string
   * @param subject    optional subject qualifier
   * @param fields     optional field-level restrictions
   * @param conditions optional JSON conditions map
   * @return the persisted permission
   */
  @Transactional
  public CmsPermission createForRole(
      CmsRole role, String action, String subject,
      List<String> fields, Map<String, Object> conditions) {
    CmsPermission perm = new CmsPermission();
    perm.action = action;
    perm.subject = subject;
    perm.fields = fields;
    perm.conditions = conditions;
    perm.role = role;
    perm.persist();
    role.permissions.add(perm);
    return perm;
  }

  /**
   * Updates the field/condition constraints of an existing permission.
   * The action and subject are immutable once created.
   */
  @Transactional
  public CmsPermission updateConstraints(
      Long permissionId, List<String> fields, Map<String, Object> conditions) {
    CmsPermission perm = findById(permissionId);
    if (perm == null) {
      throw new IllegalArgumentException("Permission not found: " + permissionId);
    }
    if (fields != null) {
      perm.fields = fields;
    }
    if (conditions != null) {
      perm.conditions = conditions;
    }
    perm.persist();
    Log.debugf("Updated permission constraints: %d", permissionId);
    return perm;
  }

  /** Deletes a permission grant permanently. */
  @Transactional
  public void delete(Long permissionId) {
    CmsPermission perm = findById(permissionId);
    if (perm == null) {
      throw new IllegalArgumentException("Permission not found: " + permissionId);
    }
    perm.delete();
    Log.infof("Deleted permission: %d", permissionId);
  }

  /** Deletes all permissions for a given role. */
  @Transactional
  public void deleteByRole(Long roleId) {
    long count = CmsPermission.delete("role.id", roleId);
    Log.debugf("Deleted %d permissions for role %d", count, roleId);
  }
}
