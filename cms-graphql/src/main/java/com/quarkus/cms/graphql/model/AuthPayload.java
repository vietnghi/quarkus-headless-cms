package com.quarkus.cms.graphql.model;

/**
 * GraphQL type representing the result of a successful login mutation.
 *
 * <p>Returns a JWT access token and user info, matching the Strapi v5 auth payload shape.
 */
public class AuthPayload {

  private final String jwt;
  private final UserInfo user;

  public AuthPayload(String jwt, UserInfo user) {
    this.jwt = jwt;
    this.user = user;
  }

  public String getJwt() {
    return jwt;
  }

  public UserInfo getUser() {
    return user;
  }

  /** Minimal user information returned after login. */
  public static class UserInfo {

    private final Long id;
    private final String username;
    private final String email;
    private final java.util.Set<String> roles;

    public UserInfo(Long id, String username, String email, java.util.Set<String> roles) {
      this.id = id;
      this.username = username;
      this.email = email;
      this.roles = roles;
    }

    public Long getId() {
      return id;
    }

    public String getUsername() {
      return username;
    }

    public String getEmail() {
      return email;
    }

    public java.util.Set<String> getRoles() {
      return roles;
    }
  }
}
