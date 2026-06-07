package com.quarkus.cms.plugin;

import com.quarkus.cms.plugin.admin.AdminPageDefinition;
import com.quarkus.cms.plugin.content.ContentTypeExtension;
import com.quarkus.cms.plugin.endpoint.EndpointRegistration;
import com.quarkus.cms.plugin.hook.PluginHook;

import java.util.Map;

/**
 * Context provided to a plugin during its {@link CmsPlugin#register(PluginRegistrationContext)}
 * callback.
 *
 * <p>Plugins use this context to:
 *
 * <ul>
 *   <li>Register content type extensions that add fields to content types
 *   <li>Register custom API endpoints
 *   <li>Register admin panel pages/routes
 *   <li>Register lifecycle hooks
 *   <li>Access plugin-specific configuration from {@code application.properties}
 * </ul>
 */
public class PluginRegistrationContext {

  private final PluginRegistry registry;
  private final String pluginName;
  private final Map<String, String> config;

  public PluginRegistrationContext(
      PluginRegistry registry, String pluginName, Map<String, String> config) {
    this.registry = registry;
    this.pluginName = pluginName;
    this.config = config;
  }

  /** Returns the plugin's unique name. */
  public String getPluginName() {
    return pluginName;
  }

  /**
   * Returns plugin-specific configuration properties. These are sourced from {@code
   * application.properties} under the {@code quarkus.cms.plugin.<plugin-name>.*} namespace.
   */
  public Map<String, String> getConfig() {
    return config;
  }

  /** Returns a specific config value, or the default if not set. */
  public String getConfig(String key, String defaultValue) {
    return config.getOrDefault(key, defaultValue);
  }

  /** Registers a content type extension contributed by this plugin. */
  public void registerContentTypeExtension(ContentTypeExtension extension) {
    registry.registerContentTypeExtension(pluginName, extension);
  }

  /** Registers a custom API endpoint contributed by this plugin. */
  public void registerEndpoint(EndpointRegistration endpoint) {
    registry.registerEndpoint(pluginName, endpoint);
  }

  /** Registers an admin panel page contributed by this plugin. */
  public void registerAdminPage(AdminPageDefinition page) {
    registry.registerAdminPage(pluginName, page);
  }

  /** Registers a lifecycle hook contributed by this plugin. */
  public void registerHook(PluginHook hook) {
    registry.registerHook(pluginName, hook);
  }

  /** Returns the central plugin registry. */
  public PluginRegistry getRegistry() {
    return registry;
  }
}
