package com.quarkus.cms.auth.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for auth DTO serialization and mapping.
 */
class DtoMappingTest {

  @Test
  void userDtoShouldMapFromUser() {
    CmsUser user = new CmsUser();
    user.id = 1L;
    user.username = "testuser";
    user.email = "test@example.com";
    user.firstName = "Test";
    user.lastName = "User";
    user.isActive = true;
    user.preferredLocale = "en";
    user.createdAt = Instant.now();
    user.updatedAt = Instant.now();

    CmsRole role = new CmsRole();
    role.id = 1L;
    role.code = "Editor";
    role.name = "Editor";
    user.roles = new java.util.HashSet<>(List.of(role));

    UserDto dto = UserDto.from(user);

    assertEquals(1L, dto.id);
    assertEquals("testuser", dto.username);
    assertEquals("test@example.com", dto.email);
    assertEquals("Test", dto.firstName);
    assertEquals("User", dto.lastName);
    assertTrue(dto.isActive);
    assertEquals(1, dto.roles.size());
  }

  @Test
  void roleDtoShouldMapFromRole() {
    CmsRole role = new CmsRole();
    role.id = 1L;
    role.code = "Administrator";
    role.name = "Administrator";
    role.description = "Super admin";

    CmsPermission perm = new CmsPermission();
    perm.id = 1L;
    perm.action = "admin::users.read";
    perm.role = role;
    role.permissions.add(perm);

    RoleDto dto = RoleDto.from(role);

    assertEquals(1L, dto.id);
    assertEquals("Administrator", dto.code);
    assertEquals("Administrator", dto.name);
    assertEquals("Super admin", dto.description);
    assertEquals(1, dto.permissions.size());
    assertEquals("admin::users.read", dto.permissions.get(0).action);
  }

  @Test
  void loginRequestShouldValidate() {
    LoginRequest req = new LoginRequest("user", "pass");
    assertEquals("user", req.identifier);
    assertEquals("pass", req.password);
  }

  @Test
  void registerRequestShouldHoldData() {
    RegisterRequest req = new RegisterRequest();
    req.username = "newuser";
    req.email = "new@example.com";
    req.password = "password123";
    req.firstName = "New";
    req.lastName = "User";

    assertEquals("newuser", req.username);
    assertEquals("new@example.com", req.email);
    assertEquals("password123", req.password);
  }

  @Test
  void authResponseShouldContainTokens() {
    UserDto user = new UserDto();
    user.username = "test";

    AuthResponse resp = new AuthResponse("token1", "token2", user);
    assertEquals("token1", resp.accessToken);
    assertEquals("token2", resp.refreshToken);
    assertEquals("test", resp.user.username);
  }

  @Test
  void createRoleRequestShouldContainPermissions() {
    CreateRoleRequest req = new CreateRoleRequest();
    req.code = "Manager";
    req.name = "Manager";

    CreateRoleRequest.PermissionInput input = new CreateRoleRequest.PermissionInput();
    input.action = "admin::users.read";
    input.subject = null;

    req.permissions = List.of(input);

    assertEquals("Manager", req.code);
    assertEquals(1, req.permissions.size());
    assertEquals("admin::users.read", req.permissions.get(0).action);
  }
}
