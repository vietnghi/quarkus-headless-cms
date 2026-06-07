package com.quarkus.cms.plugin.internal;

import io.quarkus.logging.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Isolated classloader for loading plugins from external JAR files.
 *
 * <p>Each plugin JAR or directory of JARs gets its own {@link URLClassLoader} with the application
 * classloader as its parent. This provides classloader isolation so plugins can have conflicting
 * dependencies.
 *
 * <p>Plugin JARs are discovered by scanning a configurable directory (default: {@code plugins/})
 * for {@code .jar} files.
 */
public class PluginClassLoader implements Closeable {

  private final URLClassLoader classLoader;
  private final List<Path> jarFiles;

  /**
   * Creates a classloader that loads classes from all JAR files in the given directory.
   *
   * @param pluginDirectory the directory to scan for JAR files
   * @param parent the parent classloader (typically the application classloader)
   * @throws IOException if the directory cannot be read
   */
  public PluginClassLoader(Path pluginDirectory, ClassLoader parent) throws IOException {
    this.jarFiles = new ArrayList<>();
    this.classLoader = buildClassLoader(pluginDirectory, parent);
  }

  private URLClassLoader buildClassLoader(Path directory, ClassLoader parent) throws IOException {
    if (!Files.isDirectory(directory)) {
      Log.warnf("Plugin directory does not exist: %s", directory.toAbsolutePath());
      return new URLClassLoader(new URL[0], parent);
    }

    List<URL> urls = new ArrayList<>();
    try (Stream<Path> files = Files.list(directory)) {
      files
          .filter(p -> p.toString().endsWith(".jar"))
          .sorted()
          .forEach(
              p -> {
                try {
                  urls.add(p.toUri().toURL());
                  jarFiles.add(p);
                  Log.infof("Discovered plugin JAR: %s", p.toAbsolutePath());
                } catch (MalformedURLException e) {
                  Log.warnf("Invalid plugin JAR path: %s — %s", p, e.getMessage());
                }
              });
    }

    if (urls.isEmpty()) {
      return new URLClassLoader(new URL[0], parent);
    }

    return new URLClassLoader(urls.toArray(new URL[0]), parent);
  }

  /**
   * Loads a class by name from the plugin classloader.
   *
   * @param className the fully qualified class name
   * @return the loaded class
   * @throws ClassNotFoundException if the class is not found in any plugin JAR
   */
  public Class<?> loadClass(String className) throws ClassNotFoundException {
    return classLoader.loadClass(className);
  }

  /** Returns the underlying URLClassLoader. */
  public URLClassLoader getClassLoader() {
    return classLoader;
  }

  /** Returns the list of JAR files loaded by this classloader. */
  public List<Path> getJarFiles() {
    return List.copyOf(jarFiles);
  }

  /** Returns the number of JAR files loaded. */
  public int getJarCount() {
    return jarFiles.size();
  }

  @Override
  public void close() throws IOException {
    classLoader.close();
  }
}
