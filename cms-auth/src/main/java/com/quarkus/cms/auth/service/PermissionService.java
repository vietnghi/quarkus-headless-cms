package com.quarkus.cms.auth.service;

import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dynamic permission evaluator following the Strapi CASL-based RBAC model.
 *
 * <p>Permissions are evaluated per-endpoint based on the action (e.g.,
 * "api::article.article.read") and an optional subject. Each role carries a set of permission
 * grants; a user has all permissions from all assigned roles.
 *
 * <h3>Strapi action format:</h3>
 *
 * <pre>{@code
 * api::<content-type>.<action>          // e.g. api::article.article.create
 * plugin::<plugin>.<action>             // e.g. plugin::users-permissions.auth.login
 * admin::<section>.<action>             // e.g. admin::users.read
 * }</pre>
 *
 * <h3>Actions per content type:</h3>
 *
 * <ul>
 *   <li>{@code create} — create new entries
 *   <li>{@code read} — read entries
 *   <li>{@code update} — update existing entries
 *   <li>{@code delete} — delete entries
 *   <li>{@code publish} — publish draft entries
 * </ul>
 */
@ApplicationScoped
public class PermissionService {

  /**
   * Checks whether a user is permitted to perform the given action.
   *
   * <p>Users with the "Administrator" role are always permitted (super-admin). For other users,
   * their roles' permissions are checked for a matching action and optional subject.
   *
   * @param user the authenticated user (may be null for public/unauthenticated)
   * @param action the action string (e.g. "api::article.article.read")
   * @return true if permitted
   */
  public boolean isPermitted(CmsUser user, String action) {
    return isPermitted(user, action, null);
  }

  /**
   * Checks whether a user is permitted to perform the given action on a given subject.
   *
   * @param user the authenticated user (may be null for public/unauthenticated)
   * @param action the action string
   * @param subject an optional subject qualifier (content-type UID, plugin ID, etc.)
   * @return true if permitted
   */
  public boolean isPermitted(CmsUser user, String action, String subject) {
    // Public / unauthenticated — deny by default unless action is public
    if (user == null) {
      return isPublicAction(action);
    }

    // Super-admin (Administrator role code) — always permitted
    if (hasRole(user, "Administrator")) {
      return true;
    }

    // Check assigned roles for matching permissions
    for (CmsRole role : user.roles) {
      for (CmsPermission perm : role.permissions) {
        if (matchesPermission(perm, action, subject)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Collects all permission actions granted to a user through their roles.
   *
   * @param user the authenticated user
   * @return set of action strings
   */
  public List<String> getGrantedActions(CmsUser user) {
    if (user == null) {
      return List.of();
    }

    return user.roles.stream()
        .flatMap(role -> role.permissions.stream())
        .map(perm -> perm.action)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Collects all permission grants for a user with their optional conditions.
   *
   * @param user the authenticated user
   * @return list of permission grant entries
   */
  public List<PermissionGrant> getPermissionGrants(CmsUser user) {
    if (user == null) {
      return List.of();
    }

    List<PermissionGrant> grants = new ArrayList<>();
    for (CmsRole role : user.roles) {
      for (CmsPermission perm : role.permissions) {
        grants.add(new PermissionGrant(perm.action, perm.subject, perm.conditions));
      }
    }
    return grants;
  }

  /** Check if a permission entity matches the requested action and subject. */
  private boolean matchesPermission(CmsPermission perm, String action, String subject) {
    // Exact action match required
    if (!perm.action.equals(action)) {
      return false;
    }

    // If no subject constraint on the permission, it matches any subject
    if (perm.subject == null || perm.subject.isEmpty()) {
      return true;
    }

    // If the caller specified a subject, it must match the permission's subject
    if (subject != null) {
      return perm.subject.equals(subject);
    }

    // Permission has subject constraint but caller didn't specify — allow
    return true;
  }

  /** Actions always permitted for unauthenticated users. */
  private boolean isPublicAction(String action) {
    // Public actions that don't require authentication
    return action.startsWith("plugin::users-permissions.auth.")
        || action.equals("admin::login")
        || action.equals("admin::register")
        || action.equals("admin::forgot-password")
        || action.equals("admin::reset-password")
        || action.equals("admin::email-confirmation");
  }

  /** Check if user has a role with the given code. */
  private boolean hasRole(CmsUser user, String roleCode) {
    return user.roles.stream().anyMatch(r -> roleCode.equals(r.code));
  }

  /** Immutable permission grant snapshot. */
  public record PermissionGrant(
      String action, String subject, Map<String, Object> conditions) {}
}
