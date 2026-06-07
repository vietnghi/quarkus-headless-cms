package com.quarkus.cms.auth.provider;

import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.repository.CmsUserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;

/**
 * Default local authentication provider using username/password with bcrypt-hashed passwords.
 *
 * <p>Handles the standard CMS admin login flow. Credentials are validated against the {@code
 * cms_users} table. Successful authentication returns the user entity with roles loaded.
 */
@ApplicationScoped
public class LocalAuthenticationProvider implements AuthenticationProvider {

  @Inject CmsUserRepository userRepository;

  @Override
  public String getProviderName() {
    return "local";
  }

  @Override
  public CmsUser authenticate(Map<String, String> credentials) {
    String identifier = credentials.get("identifier");
    String password = credentials.get("password");

    if (identifier == null || identifier.isBlank()) {
      throw new SecurityException("Identifier (username or email) is required");
    }
    if (password == null || password.isBlank()) {
      throw new SecurityException("Password is required");
    }

    // Try username first, then email
    CmsUser user = userRepository.findByUsername(identifier);
    if (user == null) {
      user = userRepository.findByEmail(identifier);
    }

    if (user == null) {
      throw new SecurityException("Invalid credentials");
    }

    if (!user.isActive || user.isBlocked) {
      throw new SecurityException("Account is inactive or blocked");
    }

    if (!BcryptUtil.matches(password, user.passwordHash)) {
      throw new SecurityException("Invalid credentials");
    }

    userRepository.recordLogin(user.id);
    Log.infof("Local auth successful for: %s", user.username);
    return user;
  }

  @Override
  @Transactional
  public CmsUser createOrLinkUser(Map<String, String> providerProfile) {
    String username = providerProfile.get("username");
    String email = providerProfile.get("email");
    String password = providerProfile.get("password");
    String firstName = providerProfile.get("firstName");
    String lastName = providerProfile.get("lastName");

    if (username == null || email == null || password == null) {
      throw new IllegalArgumentException(
          "Username, email, and password are required for local registration");
    }

    CmsUser user = userRepository.create(username, email, password, firstName, lastName);

    // Assign "Authenticated" role
    CmsRole authenticatedRole = CmsRole.findByCode("Authenticated");
    if (authenticatedRole != null) {
      user.roles.add(authenticatedRole);
      user.persist();
    }

    Log.infof("Local registration successful for: %s", username);
    return user;
  }

  @Override
  public boolean supportsRegistration() {
    return true;
  }

  @Override
  public boolean supportsPasswordReset() {
    return true;
  }
}
