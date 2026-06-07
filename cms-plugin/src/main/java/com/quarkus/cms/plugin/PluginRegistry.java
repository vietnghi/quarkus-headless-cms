package com.quarkus.cms.plugin;

import com.quarkus.cms.plugin.admin.AdminPageDefinition;
import com.quarkus.cms.plugin.content.ContentTypeExtension;
import com.quarkus.cms.plugin.endpoint.EndpointRegistration;
import com.quarkus.cms.plugin.hook.PluginHook;
import com.quarkus.cms.plugin.hook.PluginHookRegistry;
import com.quarkus.cms.plugin.internal.PluginClassLoader;
import com.quarkus.cms.plugin.internal.PluginScanner;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Central registry for the CMS Plugin System.
 *
 * <p>Manages the full lifecycle of all plugins — discovery, registration, activation, and
 * deactivation. Supports three plugin sources:
 *
 * <ol>
 *   <li><b>CDI beans</b> — injected via {@code Instance<CmsPlugin>}
 *   <li><b>ServiceLoader</b> — classpath scanning via {@link java.util.ServiceLoader}
 *   <li><b>External JARs</b> — classloader-isolated JAR files from a configurable directory
 * </ol>
 *
 * <p>Each plugin goes through the following lifecycle:
 *
 * <ol>
 *   <li>Discovery — plugin implementation is found via one of the three sources
 *   <li>Registration — {@link CmsPlugin#register(PluginRegistrationContext)} is called
 *   <li>Activation — contributed extensions, endpoints, admin pages, and hooks are registered
 *   <li>Runtime — plugin services operate normally
 *   <li>Deactivation — {@link CmsPlugin#unregister()} is called on shutdown or removal
 * </ol>
 */
@ApplicationScoped
public class PluginRegistry {

  // Injected CDI plugins
  @Inject Instance<CmsPlugin> cdiPlugins;

  @Inject PluginConfig config;

  @Inject PluginHookRegistry hookRegistry;

  // Core plugin state
  private final Map<String, CmsPlugin> plugins = new ConcurrentHashMap<>();
  private final Map<String, PluginMetadata> metadataMap = new ConcurrentHashMap<>();
  private final Map<String, PluginState> stateMap = new ConcurrentHashMap<>();

  // Plugin contributions
  private final Map<String, List<ContentTypeExtension>> contentTypeExtensions =
      new ConcurrentHashMap<>();
  private final Map<String, List<EndpointRegistration>> endpointRegistrations =
      new ConcurrentHashMap<>();
  private final Map<String, List<AdminPageDefinition>> adminPages = new ConcurrentHashMap<>();

  // External classloader
  private PluginClassLoader externalClassLoader;
  private final PluginScanner scanner = new PluginScanner();

  public enum PluginState {
    DISCOVERED,
    REGISTERED,
    ACTIVE,
    DISABLED,
    ERROR
  }

  @PostConstruct
  void initialize() {
    if (!config.enabled()) {
      Log.info("CMS Plugin System is disabled");
      return;
    }
    Log.info("Initializing CMS Plugin System");
  }

  /** Startup observer that loads all plugins after CDI is initialized. */
  void onStartup(@Observes StartupEvent event) {
    if (!config.enabled()) return;
    loadAllPlugins();
  }

  /** Loads all plugins from all discovery sources. */
  public synchronized void loadAllPlugins() {
    Log.info("Loading CMS plugins...");

    // 1. Discover CDI plugins
    List<CmsPlugin> cdiDiscovered = new ArrayList<>();
    for (CmsPlugin plugin : cdiPlugins) {
      cdiDiscovered.add(plugin);
    }
    Log.infof("Discovered %d CDI plugin(s)", cdiDiscovered.size());
    registerPlugins(cdiDiscovered);

    // 2. Discover ServiceLoader plugins (classpath scanning)
    if (config.classpathScanningEnabled()) {
      List<CmsPlugin> serviceLoaderPlugins = scanner.scanClasspath(getClass().getClassLoader());
      Log.infof("Discovered %d ServiceLoader plugin(s)", serviceLoaderPlugins.size());
      registerPlugins(serviceLoaderPlugins);
    }

    // 3. Discover external JAR plugins (classloader isolation)
    if (config.externalJarsEnabled()) {
      loadExternalPlugins();
    }

    // Activate all registered plugins
    activateAllPlugins();

    Log.infof("Plugin system initialized: %d plugin(s) active", getActiveCount());
  }

  /** Loads plugins from external JAR files in the configured directory. */
  private void loadExternalPlugins() {
    String dir = config.pluginDirectory();
    Path pluginDir = Path.of(dir);

    try {
      externalClassLoader = new PluginClassLoader(pluginDir, getClass().getClassLoader());
      if (externalClassLoader.getJarCount() > 0) {
        List<CmsPlugin> externalPlugins =
            scanner.scanExternal(externalClassLoader.getClassLoader());
        Log.infof("Discovered %d external plugin(s) from %s", externalPlugins.size(), dir);
        registerPlugins(externalPlugins);
      }
    } catch (IOException e) {
      Log.errorf("Failed to load external plugins from %s: %s", dir, e.getMessage());
    }
  }

  /** Registers a collection of discovered plugins. */
  void registerPlugins(List<CmsPlugin> pluginList) {
    Set<String> excluded = parseExcludedPlugins();

    for (CmsPlugin plugin : pluginList) {
      try {
        PluginMetadata meta = plugin.getMetadata();
        String name = meta.getName();

        if (plugins.containsKey(name)) {
          Log.debugf("Plugin already registered, skipping: %s", name);
          continue;
        }

        if (excluded.contains(name)) {
          Log.infof("Plugin excluded by configuration: %s", name);
          stateMap.put(name, PluginState.DISABLED);
          continue;
        }

        plugins.put(name, plugin);
        metadataMap.put(name, meta);
        stateMap.put(name, PluginState.DISCOVERED);
        Log.debugf("Discovered plugin: %s v%s", name, meta.getVersion());

      } catch (Exception e) {
        Log.errorf("Failed to discover plugin: %s", e.getMessage());
      }
    }
  }

  /** Activates all discovered plugins. */
  void activateAllPlugins() {
    for (Map.Entry<String, CmsPlugin> entry : plugins.entrySet()) {
      String name = entry.getKey();
      CmsPlugin plugin = entry.getValue();

      if (stateMap.get(name) == PluginState.DISABLED) continue;

      try {
        activatePlugin(name, plugin);
      } catch (Exception e) {
        Log.errorf("Failed to activate plugin '%s': %s", name, e.getMessage());
        stateMap.put(name, PluginState.ERROR);
      }
    }
  }

  /**
   * Activates a single plugin by calling its register() method and collecting all contributed
   * extensions, endpoints, admin pages, and hooks.
   */
  private void activatePlugin(String name, CmsPlugin plugin) {
    if (!plugin.isEnabled()) {
      stateMap.put(name, PluginState.DISABLED);
      Log.infof("Plugin disabled by implementation: %s", name);
      return;
    }

    // Build per-plugin config
    Map<String, String> pluginConfig = buildPluginConfig(name);

    // Create registration context
    PluginRegistrationContext context = new PluginRegistrationContext(this, name, pluginConfig);

    // Call plugin's register callback
    plugin.register(context);

    // Collect content type extensions
    for (ContentTypeExtension ext : plugin.getContentTypeExtensions()) {
      registerContentTypeExtension(name, ext);
    }

    // Collect endpoint registrations
    for (EndpointRegistration ep : plugin.getEndpointRegistrations()) {
      registerEndpoint(name, ep);
    }

    // Collect admin panel extensions
    for (com.quarkus.cms.plugin.admin.AdminPanelExtension adminExt :
        plugin.getAdminPanelExtensions()) {
      for (AdminPageDefinition page : adminExt.getAdminPages()) {
        registerAdminPage(name, page);
      }
    }

    // Collect hooks
    for (PluginHook hook : plugin.getHooks()) {
      registerHook(name, hook);
    }

    stateMap.put(name, PluginState.ACTIVE);
    Log.infof("Plugin activated: %s v%s", name, plugin.getMetadata().getVersion());
  }

  /** Builds the per-plugin configuration map from the global plugin config. */
  private Map<String, String> buildPluginConfig(String pluginName) {
    Map<String, String> result = new HashMap<>();
    // Config keys from quarkus.cms.plugin.config.<plugin-name>.*
    String prefix = pluginName + ".";
    for (Map.Entry<String, String> entry : config.pluginConfig().entrySet()) {
      String key = entry.getKey();
      if (key.equals(pluginName) || key.startsWith(prefix)) {
        String configKey = key.substring(prefix.length());
        result.put(configKey, entry.getValue());
      }
    }
    return Collections.unmodifiableMap(result);
  }

  /** Parses the comma-separated exclude list. */
  private Set<String> parseExcludedPlugins() {
    return config
        .exclude()
        .map(
            s ->
                Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(x -> !x.isEmpty())
                    .collect(Collectors.toSet()))
        .orElse(Set.of());
  }

  // ---- Contribution registration methods ----

  /** Registers a content type extension contributed by a plugin. */
  public void registerContentTypeExtension(String pluginName, ContentTypeExtension extension) {
    contentTypeExtensions
        .computeIfAbsent(pluginName, k -> new CopyOnWriteArrayList<>())
        .add(extension);
    Log.debugf(
        "Plugin '%s' registered content type extension for: %s",
        pluginName, extension.getTargetContentType());
  }

  /** Registers an endpoint contributed by a plugin. */
  public void registerEndpoint(String pluginName, EndpointRegistration endpoint) {
    endpointRegistrations
        .computeIfAbsent(pluginName, k -> new CopyOnWriteArrayList<>())
        .add(endpoint);
    Log.debugf(
        "Plugin '%s' registered endpoint: %s %s",
        pluginName, endpoint.getMethod(), endpoint.getPath());
  }

  /** Registers an admin page contributed by a plugin. */
  public void registerAdminPage(String pluginName, AdminPageDefinition page) {
    adminPages.computeIfAbsent(pluginName, k -> new CopyOnWriteArrayList<>()).add(page);
    Log.debugf(
        "Plugin '%s' registered admin page: %s (%s)",
        pluginName, page.getDisplayName(), page.getRoutePath());
  }

  /** Registers a hook contributed by a plugin. */
  public void registerHook(String pluginName, PluginHook hook) {
    hookRegistry.registerHook(pluginName, hook);
  }

  // ---- Query methods ----

  /** Returns all registered plugins. */
  public Map<String, CmsPlugin> getAllPlugins() {
    return Collections.unmodifiableMap(plugins);
  }

  /** Returns a plugin by name. */
  public CmsPlugin getPlugin(String name) {
    return plugins.get(name);
  }

  /** Returns metadata for all plugins. */
  public List<PluginMetadata> getAllPluginMetadata() {
    return List.copyOf(metadataMap.values());
  }

  /** Returns metadata for a specific plugin. */
  public PluginMetadata getPluginMetadata(String name) {
    return metadataMap.get(name);
  }

  /** Returns the state of a plugin. */
  public PluginState getPluginState(String name) {
    return stateMap.getOrDefault(name, PluginState.ERROR);
  }

  /** Returns all content type extensions across all plugins. */
  public List<ContentTypeExtension> getAllContentTypeExtensions() {
    return contentTypeExtensions.values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toUnmodifiableList());
  }

  /** Returns all endpoint registrations across all plugins. */
  public List<EndpointRegistration> getAllEndpointRegistrations() {
    return endpointRegistrations.values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toUnmodifiableList());
  }

  /** Returns all admin page definitions across all plugins. */
  public List<AdminPageDefinition> getAllAdminPages() {
    return adminPages.values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toUnmodifiableList());
  }

  /** Returns content type extensions for a specific plugin. */
  public List<ContentTypeExtension> getPluginContentTypeExtensions(String pluginName) {
    return List.copyOf(contentTypeExtensions.getOrDefault(pluginName, List.of()));
  }

  /** Returns the number of active plugins. */
  public int getActiveCount() {
    return (int) stateMap.values().stream().filter(s -> s == PluginState.ACTIVE).count();
  }

  /** Returns the total number of registered plugins. */
  public int getTotalCount() {
    return plugins.size();
  }

  /** Returns whether the plugin system is active. */
  public boolean isEnabled() {
    return config.enabled();
  }

  /** Gracefully shuts down all plugins by calling unregister() on each. */
  public synchronized void shutdown() {
    Log.info("Shutting down CMS Plugin System...");

    for (Map.Entry<String, CmsPlugin> entry : plugins.entrySet()) {
      String name = entry.getKey();
      try {
        if (stateMap.get(name) == PluginState.ACTIVE) {
          entry.getValue().unregister();
          hookRegistry.unregisterPlugin(name);
          stateMap.put(name, PluginState.REGISTERED);
          Log.debugf("Plugin deactivated: %s", name);
        }
      } catch (Exception e) {
        Log.errorf("Error shutting down plugin '%s': %s", name, e.getMessage());
      }
    }

    // Close external classloader
    if (externalClassLoader != null) {
      try {
        externalClassLoader.close();
      } catch (IOException e) {
        Log.errorf("Error closing external plugin classloader: %s", e.getMessage());
      }
    }

    plugins.clear();
    metadataMap.clear();
    stateMap.clear();
    contentTypeExtensions.clear();
    endpointRegistrations.clear();
    adminPages.clear();
    hookRegistry.clear();

    Log.info("CMS Plugin System shutdown complete");
  }
}
