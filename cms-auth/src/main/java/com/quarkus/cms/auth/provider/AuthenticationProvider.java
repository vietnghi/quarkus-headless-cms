package com.quarkus.cms.auth.provider;

import com.quarkus.cms.auth.entity.CmsUser;

/**
 * Pluggable authentication provider interface.
 *
 * <p>Implementations handle credential verification for a specific authentication method. The
 * built-in {@code LocalAuthenticationProvider} handles username/password auth; custom
 * implementations can support OAuth2, OpenID Connect, SAML, LDAP, etc.
 *
 * <h3>Provider resolution:</h3>
 *
 * <p>Providers are discovered via CDI and selected by the {@code provider} request parameter or the
 * {@code provider} claim in the JWT. If no provider is specified, the local provider is used.
 */
public interface AuthenticationProvider {

  /** Returns the unique provider name (e.g. "local", "google", "github"). */
  String getProviderName();

  /**
   * Authenticates a user using this provider's credentials.
   *
   * @param credentials provider-specific credential map (e.g. username + password, OAuth2 token)
   * @return the authenticated user entity
   * @throws SecurityException if authentication fails
   */
  CmsUser authenticate(java.util.Map<String, String> credentials);

  /**
   * Creates or links a user account via this provider.
   *
   * @param providerProfile provider-specific user profile data
   * @return the created or linked CMS user
   */
  CmsUser createOrLinkUser(java.util.Map<String, String> providerProfile);

  /**
   * Whether this provider supports user registration.
   *
   * @return true if users can be created through this provider
   */
  default boolean supportsRegistration() {
    return false;
  }

  /**
   * Whether this provider supports password reset.
   *
   * @return true if password reset is supported
   */
  default boolean supportsPasswordReset() {
    return false;
  }
}
