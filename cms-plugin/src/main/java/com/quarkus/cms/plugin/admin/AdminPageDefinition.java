package com.quarkus.cms.plugin.admin;

import java.util.Map;

/**
 * Describes an admin panel page registered by a plugin.
 *
 * <p>Each page has a unique ID, a display name, a React component path (for the admin UI), and
 * optional configuration such as permissions and route options.
 */
public class AdminPageDefinition {

  private final String id;
  private final String displayName;
  private final String componentPath;
  private final String routePath;
  private final Map<String, Object> options;

  private AdminPageDefinition(Builder builder) {
    if (builder.id == null || builder.id.isBlank()) {
      throw new IllegalArgumentException("Admin page id is required");
    }
    this.id = builder.id;
    this.displayName = builder.displayName;
    this.componentPath = builder.componentPath;
    this.routePath = builder.routePath;
    this.options = builder.options == null ? Map.of() : Map.copyOf(builder.options);
  }

  /** Unique page identifier within the plugin namespace, e.g. {@code "seo"} */
  public String getId() {
    return id;
  }

  /** Human-readable display name shown in the admin navigation, e.g. {@code "SEO Settings"}. */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Path to the admin UI React component, e.g. {@code "/plugins/seo/SettingsPage"}. The admin UI
   * resolves this to render the plugin's page.
   */
  public String getComponentPath() {
    return componentPath;
  }

  /**
   * Route path in the admin panel, e.g. {@code "/admin/plugins/seo"}. If null, the id is used to
   * derive the route.
   */
  public String getRoutePath() {
    return routePath;
  }

  /** Additional options (permissions, layout hints, etc.). */
  public Map<String, Object> getOptions() {
    return options;
  }

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public static class Builder {
    private String id;
    private String displayName;
    private String componentPath;
    private String routePath;
    private Map<String, Object> options;

    Builder(String id) {
      this.id = id;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder componentPath(String componentPath) {
      this.componentPath = componentPath;
      return this;
    }

    public Builder routePath(String routePath) {
      this.routePath = routePath;
      return this;
    }

    public Builder options(Map<String, Object> options) {
      this.options = options;
      return this;
    }

    public AdminPageDefinition build() {
      return new AdminPageDefinition(this);
    }
  }
}
