package com.quarkus.cms.auth.repository;

import com.quarkus.cms.auth.entity.CmsUser;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Repository for {@link CmsUser} admin user operations.
 *
 * <p>Manages user lifecycle (create, update, block, delete) and password hashing using bcrypt.
 */
@ApplicationScoped
public class CmsUserRepository {

  /** Creates a new admin user with a bcrypt-hashed password. */
  @Transactional
  public CmsUser create(
      String username, String email, String rawPassword, String firstName, String lastName) {
    if (CmsUser.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists: " + username);
    }

    CmsUser user = new CmsUser();
    user.username = username;
    user.email = email;
    user.passwordHash = BcryptUtil.bcryptHash(rawPassword);
    user.firstName = firstName;
    user.lastName = lastName;
    user.persist();
    Log.infof("Created user: %s", username);
    return user;
  }

  /** Finds an active user by username. */
  public CmsUser findByUsername(String username) {
    return CmsUser.findByUsername(username);
  }

  /** Finds an active user by email. */
  public CmsUser findByEmail(String email) {
    return CmsUser.findByEmail(email);
  }

  /** Returns all active users. */
  public java.util.List<CmsUser> listActive() {
    return CmsUser.findActive();
  }

  /** Updates user profile fields. */
  @Transactional
  public CmsUser updateProfile(Long userId, String firstName, String lastName, String email) {
    CmsUser user = CmsUser.findById(userId);
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + userId);
    }
    if (firstName != null) {
      user.firstName = firstName;
    }
    if (lastName != null) {
      user.lastName = lastName;
    }
    if (email != null) {
      user.email = email;
    }
    user.persist();
    return user;
  }

  /** Changes the user's password. */
  @Transactional
  public void changePassword(Long userId, String newRawPassword) {
    CmsUser user = CmsUser.findById(userId);
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + userId);
    }
    user.passwordHash = BcryptUtil.bcryptHash(newRawPassword);
    user.persist();
    Log.infof("Password changed for user: %d", userId);
  }

  /** Blocks a user (prevents login). */
  @Transactional
  public void block(Long userId) {
    CmsUser user = CmsUser.findById(userId);
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + userId);
    }
    user.isBlocked = true;
    user.persist();
    Log.infof("Blocked user: %d", userId);
  }

  /** Unblocks a user. */
  @Transactional
  public void unblock(Long userId) {
    CmsUser user = CmsUser.findById(userId);
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + userId);
    }
    user.isBlocked = false;
    user.persist();
    Log.infof("Unblocked user: %d", userId);
  }

  /** Records a login timestamp. */
  @Transactional
  public void recordLogin(Long userId) {
    CmsUser user = CmsUser.findById(userId);
    if (user != null) {
      user.lastLoginAt = Instant.now();
      user.persist();
    }
  }

  /** Soft-deletes a user by deactivating. */
  @Transactional
  public void deactivate(Long userId) {
    CmsUser user = CmsUser.findById(userId);
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + userId);
    }
    user.isActive = false;
    user.persist();
    Log.infof("Deactivated user: %d", userId);
  }
}
