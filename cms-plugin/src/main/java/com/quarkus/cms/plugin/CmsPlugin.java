package com.quarkus.cms.plugin;

import com.quarkus.cms.plugin.admin.AdminPanelExtension;
import com.quarkus.cms.plugin.content.ContentTypeExtension;
import com.quarkus.cms.plugin.endpoint.EndpointRegistration;
import com.quarkus.cms.plugin.hook.PluginHook;

import java.util.List;

/**
 * Main SPI interface for CMS plugins.
 *
 * <p>Every CMS plugin must implement this interface. Plugins are discovered via {@link
 * java.util.ServiceLoader} or through CDI bean injection at startup. The {@link PluginRegistry}
 * manages the lifecycle of all registered plugins.
 *
 * <p>Each plugin provides:
 *
 * <ul>
 *   <li>{@link PluginMetadata} — name, version, author, description
 *   <li>Lifecycle callbacks — {@link #register(PluginRegistrationContext)} and {@link
 *       #unregister()}
 *   <li>Optional content type extensions — additional fields on existing or new content types
 *   <li>Optional API endpoint registrations
 *   <li>Optional admin panel page registrations
 *   <li>Optional lifecycle hooks (before/after content operations)
 * </ul>
 *
 * <p>This mirrors Strapi's plugin architecture where each plugin can extend the CMS in multiple
 * dimensions simultaneously.
 */
public interface CmsPlugin {

  /**
   * Returns the plugin's metadata (name, version, description, author, etc.). This is called once
   * during registration and cached by the registry.
   */
  PluginMetadata getMetadata();

  /**
   * Called by {@link PluginRegistry} when the plugin is registered and activated. Use this callback
   * to register content type extensions, endpoints, admin pages, or hooks.
   *
   * @param context the registration context providing access to the registry and config
   */
  void register(PluginRegistrationContext context);

  /**
   * Called by {@link PluginRegistry} when the plugin is unregistered or deactivated. Clean up any
   * registered resources, listeners, or state.
   */
  void unregister();

  /**
   * Optional content type extensions that this plugin contributes. These can add fields to existing
   * content types or register entirely new content types.
   */
  default List<ContentTypeExtension> getContentTypeExtensions() {
    return List.of();
  }

  /**
   * Optional API endpoint registrations that this plugin contributes. Each registration defines a
   * path, HTTP method, and handler logic.
   */
  default List<EndpointRegistration> getEndpointRegistrations() {
    return List.of();
  }

  /**
   * Optional admin panel extensions that this plugin contributes. Each defines an admin panel page
   * or route.
   */
  default List<AdminPanelExtension> getAdminPanelExtensions() {
    return List.of();
  }

  /**
   * Optional lifecycle hooks that this plugin contributes. Hooks are invoked before and after
   * content operations (create, update, delete, publish, etc.).
   */
  default List<PluginHook> getHooks() {
    return List.of();
  }

  /**
   * Whether this plugin is enabled. The registry checks this before calling {@link
   * #register(PluginRegistrationContext)}. A disabled plugin is registered but not activated.
   */
  default boolean isEnabled() {
    return true;
  }
}
