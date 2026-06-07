package com.quarkus.cms.plugin.internal;

import com.quarkus.cms.plugin.CmsPlugin;

import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers CMS plugin implementations using the Java {@link ServiceLoader} mechanism.
 *
 * <p>Scans the classpath for all {@code META-INF/services/com.quarkus.cms.plugin.CmsPlugin} service
 * files and instantiates the listed implementations. This is the primary discovery mechanism for
 * plugins that are packaged as part of the application (either in the main classpath or as library
 * dependencies with the service file).
 *
 * <p>Plugins can also be discovered via CDI by injecting {@code Instance<CmsPlugin>}.
 */
public class PluginScanner {

  /**
   * Discovers all plugins via {@link ServiceLoader} on the given classloader.
   *
   * @param classLoader the classloader to scan (application classloader)
   * @return list of discovered plugin instances
   */
  public List<CmsPlugin> scanClasspath(ClassLoader classLoader) {
    List<CmsPlugin> plugins = new ArrayList<>();

    try {
      ServiceLoader<CmsPlugin> loader = ServiceLoader.load(CmsPlugin.class, classLoader);
      for (CmsPlugin plugin : loader) {
        plugins.add(plugin);
        Log.infof("Discovered plugin via ServiceLoader: %s", plugin.getMetadata().getName());
      }
    } catch (Exception e) {
      Log.errorf("Failed to scan for plugins via ServiceLoader: %s", e.getMessage());
    }

    return plugins;
  }

  /**
   * Discovers plugins from an external (isolated) classloader. The external classloader should have
   * been created by {@link PluginClassLoader}.
   *
   * @param externalClassLoader the isolated classloader containing plugin JARs
   * @return list of discovered plugin instances
   */
  public List<CmsPlugin> scanExternal(ClassLoader externalClassLoader) {
    List<CmsPlugin> plugins = new ArrayList<>();

    try {
      ServiceLoader<CmsPlugin> loader = ServiceLoader.load(CmsPlugin.class, externalClassLoader);
      for (CmsPlugin plugin : loader) {
        plugins.add(plugin);
        Log.infof("Discovered external plugin: %s", plugin.getMetadata().getName());
      }
    } catch (Exception e) {
      Log.errorf("Failed to scan for external plugins: %s", e.getMessage());
    }

    return plugins;
  }
}
