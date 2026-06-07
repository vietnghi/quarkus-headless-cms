package com.quarkus.cms.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PermissionService RBAC evaluation.
 */
class PermissionServiceTest {

  private PermissionService permissionService;
  private CmsUser adminUser;
  private CmsUser editorUser;
  private CmsUser regularUser;

  @BeforeEach
  void setUp() {
    permissionService = new PermissionService();

    // Admin user — has Administrator role (super-admin)
    CmsRole adminRole = new CmsRole();
    adminRole.id = 1L;
    adminRole.code = "Administrator";
    adminRole.name = "Administrator";

    adminUser = new CmsUser();
    adminUser.id = 1L;
    adminUser.username = "admin";
    adminUser.roles = new java.util.HashSet<>(Set.of(adminRole));

    // Editor user — has Editor role with specific permissions
    CmsRole editorRole = new CmsRole();
    editorRole.id = 2L;
    editorRole.code = "Editor";
    editorRole.name = "Editor";

    CmsPermission readPerm = new CmsPermission();
    readPerm.action = "api::article.article.read";
    readPerm.subject = null;
    readPerm.role = editorRole;

    CmsPermission createPerm = new CmsPermission();
    createPerm.action = "api::article.article.create";
    createPerm.subject = null;
    createPerm.role = editorRole;

    CmsPermission updatePerm = new CmsPermission();
    updatePerm.action = "api::article.article.update";
    updatePerm.subject = null;
    updatePerm.role = editorRole;

    editorRole.permissions = new java.util.ArrayList<>(List.of(readPerm, createPerm, updatePerm));

    editorUser = new CmsUser();
    editorUser.id = 2L;
    editorUser.username = "editor";
    editorUser.roles = new java.util.HashSet<>(Set.of(editorRole));

    // Regular user — has Authenticated role with minimal permissions
    CmsRole authRole = new CmsRole();
    authRole.id = 3L;
    authRole.code = "Authenticated";
    authRole.name = "Authenticated";

    CmsPermission profileRead = new CmsPermission();
    profileRead.action = "admin::users.me";
    profileRead.subject = null;
    profileRead.role = authRole;

    authRole.permissions = new java.util.ArrayList<>(List.of(profileRead));

    regularUser = new CmsUser();
    regularUser.id = 3L;
    regularUser.username = "user";
    regularUser.roles = new java.util.HashSet<>(Set.of(authRole));
  }

  @Test
  void adminShouldBePermittedForAnyAction() {
    assertTrue(permissionService.isPermitted(adminUser, "api::article.article.create"));
    assertTrue(permissionService.isPermitted(adminUser, "admin::users.delete"));
    assertTrue(permissionService.isPermitted(adminUser, "plugin::unknown.action"));
  }

  @Test
  void editorShouldBePermittedForGrantedActions() {
    assertTrue(permissionService.isPermitted(editorUser, "api::article.article.read"));
    assertTrue(permissionService.isPermitted(editorUser, "api::article.article.create"));
    assertTrue(permissionService.isPermitted(editorUser, "api::article.article.update"));
  }

  @Test
  void editorShouldBeDeniedForUngrantedActions() {
    assertFalse(permissionService.isPermitted(editorUser, "api::article.article.delete"));
    assertFalse(permissionService.isPermitted(editorUser, "admin::users.read"));
    assertFalse(permissionService.isPermitted(editorUser, "api::product.product.read"));
  }

  @Test
  void regularUserShouldOnlyHaveProfilePermission() {
    assertTrue(permissionService.isPermitted(regularUser, "admin::users.me"));
    assertFalse(permissionService.isPermitted(regularUser, "api::article.article.read"));
    assertFalse(permissionService.isPermitted(regularUser, "admin::users.read"));
  }

  @Test
  void nullUserShouldBeDeniedForProtectedActions() {
    assertFalse(permissionService.isPermitted(null, "api::article.article.read"));
    assertFalse(permissionService.isPermitted(null, "admin::users.read"));
  }

  @Test
  void nullUserShouldBePermittedForPublicActions() {
    assertTrue(permissionService.isPermitted(null, "admin::login"));
    assertTrue(permissionService.isPermitted(null, "admin::register"));
    assertTrue(permissionService.isPermitted(null, "admin::forgot-password"));
    assertTrue(permissionService.isPermitted(null, "admin::reset-password"));
    assertTrue(permissionService.isPermitted(null, "admin::email-confirmation"));
    assertTrue(
        permissionService.isPermitted(null, "plugin::users-permissions.auth.login"));
  }

  @Test
  void shouldReturnGrantedActions() {
    List<String> actions = permissionService.getGrantedActions(editorUser);
    assertTrue(actions.contains("api::article.article.read"));
    assertTrue(actions.contains("api::article.article.create"));
    assertTrue(actions.contains("api::article.article.update"));
    assertFalse(actions.contains("api::article.article.delete"));
  }

  @Test
  void shouldReturnPermissionGrants() {
    List<PermissionService.PermissionGrant> grants =
        permissionService.getPermissionGrants(editorUser);
    assertEquals(3, grants.size());
    assertTrue(grants.stream().anyMatch(g -> g.action().equals("api::article.article.read")));
  }

  @Test
  void nullUserShouldHaveEmptyGrants() {
    assertEquals(0, permissionService.getGrantedActions(null).size());
    assertEquals(0, permissionService.getPermissionGrants(null).size());
  }
}
