package com.quarkus.cms.plugin;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.util.Map;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for the CMS Plugin System.
 *
 * <p>All properties are prefixed with {@code quarkus.cms.plugin}. Plugins can define their own
 * config keys under {@code quarkus.cms.plugin.<plugin-name>.*}.
 */
@ConfigMapping(prefix = "quarkus.cms.plugin")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface PluginConfig {

  /** Whether the plugin system is enabled. When disabled, no plugins are loaded. */
  @WithDefault("true")
  boolean enabled();

  /**
   * Directory to scan for external plugin JAR files. Default: {@code plugins} (relative to the
   * application working directory).
   */
  @WithName("directory")
  @WithDefault("plugins")
  String pluginDirectory();

  /**
   * Whether to scan the classpath for plugins via {@link java.util.ServiceLoader}. When enabled,
   * all {@code META-INF/services/com.quarkus.cms.plugin.CmsPlugin} service files are discovered and
   * loaded.
   */
  @WithName("classpath-scanning-enabled")
  @WithDefault("true")
  boolean classpathScanningEnabled();

  /**
   * Whether to scan a configurable directory for external plugin JARs. When enabled, JAR files in
   * {@link #pluginDirectory()} are loaded with an isolated classloader.
   */
  @WithName("external-jars-enabled")
  @WithDefault("false")
  boolean externalJarsEnabled();

  /**
   * Comma-separated list of plugin names to exclude from loading. Useful for disabling specific
   * plugins without removing them.
   */
  @WithName("exclude")
  Optional<String> exclude();

  /**
   * Per-plugin configuration map. Keys are plugin names, values are plugin-specific configuration
   * strings.
   *
   * <p>Example:
   *
   * <pre>{@code
   * quarkus.cms.plugin.config.seo-plugin.api-key=abc123
   * quarkus.cms.plugin.config.seo-plugin.cache-ttl=3600
   * }</pre>
   */
  @WithName("config")
  Map<String, String> pluginConfig();
}
