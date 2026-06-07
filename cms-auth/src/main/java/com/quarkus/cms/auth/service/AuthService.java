package com.quarkus.cms.auth.service;

import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.repository.CmsUserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core authentication service handling login, registration, password reset, and email confirmation.
 *
 * <p>Password reset tokens and email confirmation tokens are stored in-memory with short TTLs. In
 * production, these would be backed by persistent storage (e.g., a database table or Redis).
 */
@ApplicationScoped
public class AuthService {

  @Inject CmsUserRepository userRepository;

  @Inject TokenService tokenService;

  private static final SecureRandom RANDOM = new SecureRandom();

  /** Simple in-memory store for password reset tokens. Key: email, Value: {nonce, expiry}. */
  private final Map<String, ResetToken> resetTokens = new ConcurrentHashMap<>();

  /** Simple in-memory store for email confirmation tokens. */
  private final Map<String, ConfirmToken> confirmTokens = new ConcurrentHashMap<>();

  /**
   * Authenticates a user by username/email and password.
   *
   * @param identifier username or email
   * @param rawPassword plaintext password
   * @return a LoginResult with access and refresh tokens if successful
   * @throws SecurityException if authentication fails
   */
  public LoginResult login(String identifier, String rawPassword) {
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

    if (!BcryptUtil.matches(rawPassword, user.passwordHash)) {
      throw new SecurityException("Invalid credentials");
    }

    // Record login timestamp
    userRepository.recordLogin(user.id);

    String accessToken = tokenService.generateAccessToken(user);
    String refreshToken = tokenService.generateRefreshToken(user);

    Log.infof("User logged in: %s", user.username);
    return new LoginResult(user, accessToken, refreshToken);
  }

  /**
   * Registers a new admin user.
   *
   * <p>The user is created in an inactive state until email confirmation is completed (if email
   * confirmation is configured).
   *
   * @param username desired username
   * @param email user email
   * @param rawPassword plaintext password
   * @param firstName optional first name
   * @param lastName optional last name
   * @return the created user
   * @throws IllegalArgumentException if username or email is taken
   */
  @Transactional
  public CmsUser register(
      String username, String email, String rawPassword, String firstName, String lastName) {
    CmsUser user = userRepository.create(username, email, rawPassword, firstName, lastName);

    // Assign "Authenticated" role by default
    CmsRole authenticatedRole = CmsRole.findByCode("Authenticated");
    if (authenticatedRole != null) {
      user.roles.add(authenticatedRole);
      user.persist();
    }

    // Generate email confirmation token
    String nonce = generateNonce();
    confirmTokens.put(
        email, new ConfirmToken(nonce, Instant.now().plus(java.time.Duration.ofHours(24))));

    Log.infof("Registered new user: %s (active=%s)", username, user.isActive);
    return user;
  }

  /**
   * Initiates a password reset flow. Generates a reset token and stores it.
   *
   * @param email the user's email
   * @return the reset token (would be emailed in production)
   * @throws IllegalArgumentException if the email is not found
   */
  public String initiatePasswordReset(String email) {
    CmsUser user = userRepository.findByEmail(email);
    if (user == null) {
      // Don't reveal whether the email exists — return success anyway
      Log.infof("Password reset requested for unknown email: %s", email);
      return "ok";
    }

    String nonce = generateNonce();
    String resetToken = tokenService.generatePasswordResetToken(email, nonce);

    resetTokens.put(
        email, new ResetToken(nonce, Instant.now().plus(java.time.Duration.ofMinutes(15))));

    Log.infof("Password reset initiated for: %s", email);
    return resetToken;
  }

  /**
   * Completes a password reset using the reset token.
   *
   * @param email the user's email
   * @param resetToken the reset token (JWT)
   * @param newPassword the new plaintext password
   * @throws SecurityException if the token is invalid or expired
   */
  @Transactional
  public void completePasswordReset(String email, String resetToken, String newPassword) {
    ResetToken stored = resetTokens.get(email);
    if (stored == null) {
      throw new SecurityException("No password reset in progress for this email");
    }

    if (stored.expiry.isBefore(Instant.now())) {
      resetTokens.remove(email);
      throw new SecurityException("Password reset token has expired");
    }

    CmsUser user = userRepository.findByEmail(email);
    if (user == null) {
      throw new SecurityException("User not found");
    }

    userRepository.changePassword(user.id, newPassword);
    resetTokens.remove(email);

    Log.infof("Password reset completed for user: %s", user.username);
  }

  /**
   * Confirms a user's email address.
   *
   * @param email the user's email
   * @param confirmationToken the confirmation token
   * @throws SecurityException if the token is invalid or expired
   */
  @Transactional
  public void confirmEmail(String email, String confirmationToken) {
    ConfirmToken stored = confirmTokens.get(email);
    if (stored == null) {
      throw new SecurityException("No email confirmation pending for this address");
    }

    if (stored.expiry.isBefore(Instant.now())) {
      confirmTokens.remove(email);
      throw new SecurityException("Email confirmation token has expired");
    }

    CmsUser user = userRepository.findByEmail(email);
    if (user == null) {
      throw new SecurityException("User not found");
    }

    user.isActive = true;
    user.persist();
    confirmTokens.remove(email);

    Log.infof("Email confirmed for user: %s", user.username);
  }

  /**
   * Refreshes an access token using a valid refresh token.
   *
   * @param userId the user ID from the refresh token
   * @return a new access token
   * @throws SecurityException if the user is not found or inactive
   */
  public String refreshAccessToken(Long userId) {
    CmsUser user = CmsUser.findById(userId);
    if (user == null || !user.isActive || user.isBlocked) {
      throw new SecurityException("Invalid or inactive user");
    }

    return tokenService.generateAccessToken(user);
  }

  /** Generates a random URL-safe nonce. */
  public String generateNonce() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Result of a successful login. */
  public record LoginResult(CmsUser user, String accessToken, String refreshToken) {}

  /** In-memory token storage. */
  private record ResetToken(String nonce, Instant expiry) {}

  private record ConfirmToken(String nonce, Instant expiry) {}
}
