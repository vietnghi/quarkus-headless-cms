package com.quarkus.cms.plugin;

import com.quarkus.cms.plugin.admin.AdminPageDefinition;
import com.quarkus.cms.plugin.content.ContentTypeExtension;
import com.quarkus.cms.plugin.endpoint.EndpointRegistration;
import com.quarkus.cms.plugin.hook.PluginHookRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

/**
 * High-level service for plugin management and administration.
 *
 * <p>Provides orchestration methods that wrap the {@link PluginRegistry} for use by admin REST
 * endpoints and programmatic callers.
 */
@ApplicationScoped
public class PluginManager {

  @Inject PluginRegistry registry;

  @Inject PluginHookRegistry hookRegistry;

  /** Returns all registered plugin metadata. */
  public List<PluginMetadata> listPlugins() {
    return registry.getAllPluginMetadata();
  }

  /** Returns detailed info about a specific plugin. */
  public PluginDetail getPluginDetail(String pluginName) {
    CmsPlugin plugin = registry.getPlugin(pluginName);
    if (plugin == null) return null;

    return new PluginDetail(
        registry.getPluginMetadata(pluginName),
        registry.getPluginState(pluginName).name(),
        registry.getPluginContentTypeExtensions(pluginName),
        registry.getAllEndpointRegistrations().stream()
            .filter(e -> e.toString().contains(pluginName)) // approximation
            .toList(),
        registry.getAllAdminPages(),
        hookRegistry.getPluginHooks(pluginName));
  }

  /** Returns all content type extensions across all plugins. */
  public List<ContentTypeExtension> getContentTypeExtensions() {
    return registry.getAllContentTypeExtensions();
  }

  /** Returns all endpoint registrations across all plugins. */
  public List<EndpointRegistration> getEndpointRegistrations() {
    return registry.getAllEndpointRegistrations();
  }

  /** Returns all admin page definitions across all plugins. */
  public List<AdminPageDefinition> getAdminPages() {
    return registry.getAllAdminPages();
  }

  /** Returns the number of active plugins. */
  public int getActivePluginCount() {
    return registry.getActiveCount();
  }

  /** Returns the total number of registered plugins. */
  public int getTotalPluginCount() {
    return registry.getTotalCount();
  }

  /** Returns the plugin system status summary. */
  public Map<String, Object> getSystemStatus() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("enabled", registry.isEnabled());
    status.put("totalPlugins", registry.getTotalCount());
    status.put("activePlugins", registry.getActiveCount());
    status.put("contentTypeExtensions", registry.getAllContentTypeExtensions().size());
    status.put("endpointRegistrations", registry.getAllEndpointRegistrations().size());
    status.put("adminPages", registry.getAllAdminPages().size());
    status.put("hooks", hookRegistry.size());
    return Collections.unmodifiableMap(status);
  }

  /** Record class for plugin detail view. */
  public record PluginDetail(
      PluginMetadata metadata,
      String state,
      List<ContentTypeExtension> contentTypeExtensions,
      List<EndpointRegistration> endpointRegistrations,
      List<AdminPageDefinition> adminPages,
      List<com.quarkus.cms.plugin.hook.PluginHook> hooks) {}
}
