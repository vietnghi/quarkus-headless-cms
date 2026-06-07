package com.quarkus.cms.plugin;

import java.util.Map;

/**
 * Metadata describing a CMS plugin, following a marketplace-style format.
 *
 * <p>Each plugin provides metadata that includes its unique name, version, display name,
 * description, author information, and optional configuration. This mirrors Strapi's plugin
 * package.json metadata convention.
 */
public class PluginMetadata {

  private final String name;
  private final String version;
  private final String displayName;
  private final String description;
  private final String author;
  private final String license;
  private final String homepage;
  private final Map<String, Object> options;
  private final Map<String, String> dependencies;

  private PluginMetadata(Builder builder) {
    if (builder.name == null || builder.name.isBlank()) {
      throw new IllegalArgumentException("Plugin name is required");
    }
    if (builder.version == null || builder.version.isBlank()) {
      throw new IllegalArgumentException("Plugin version is required");
    }
    this.name = builder.name;
    this.version = builder.version;
    this.displayName = builder.displayName;
    this.description = builder.description;
    this.author = builder.author;
    this.license = builder.license;
    this.homepage = builder.homepage;
    this.options = builder.options == null ? Map.of() : Map.copyOf(builder.options);
    this.dependencies = builder.dependencies == null ? Map.of() : Map.copyOf(builder.dependencies);
  }

  /** Unique plugin identifier, e.g. {@code "cms-seo-plugin"}. */
  public String getName() {
    return name;
  }

  /** Semantic version string, e.g. {@code "1.0.0"}. */
  public String getVersion() {
    return version;
  }

  /** Human-readable display name, e.g. {@code "SEO Plugin"}. */
  public String getDisplayName() {
    return displayName;
  }

  /** Short description of what the plugin does. */
  public String getDescription() {
    return description;
  }

  /** Plugin author name or organization. */
  public String getAuthor() {
    return author;
  }

  /** SPDX license identifier, e.g. {@code "MIT"}. */
  public String getLicense() {
    return license;
  }

  /** URL to plugin homepage or repository. */
  public String getHomepage() {
    return homepage;
  }

  /** Plugin-specific configuration options. */
  public Map<String, Object> getOptions() {
    return options;
  }

  /** Plugin dependency names to versions. */
  public Map<String, String> getDependencies() {
    return dependencies;
  }

  public static Builder builder(String name, String version) {
    return new Builder(name, version);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PluginMetadata that)) return false;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "PluginMetadata{name='" + name + "', version='" + version + "'}";
  }

  public static class Builder {
    private String name;
    private String version;
    private String displayName;
    private String description;
    private String author;
    private String license;
    private String homepage;
    private Map<String, Object> options;
    private Map<String, String> dependencies;

    Builder(String name, String version) {
      this.name = name;
      this.version = version;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder author(String author) {
      this.author = author;
      return this;
    }

    public Builder license(String license) {
      this.license = license;
      return this;
    }

    public Builder homepage(String homepage) {
      this.homepage = homepage;
      return this;
    }

    public Builder options(Map<String, Object> options) {
      this.options = options;
      return this;
    }

    public Builder dependencies(Map<String, String> dependencies) {
      this.dependencies = dependencies;
      return this;
    }

    public PluginMetadata build() {
      return new PluginMetadata(this);
    }
  }
}
