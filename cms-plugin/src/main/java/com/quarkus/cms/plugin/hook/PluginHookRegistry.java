package com.quarkus.cms.plugin.hook;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for plugin lifecycle hooks.
 *
 * <p>Maintains a mapping of plugin names to their hooks and provides the execution engine that
 * dispatches lifecycle events to all registered hooks. Integrated with the existing {@link
 * com.quarkus.cms.webhooks.service.LifecycleEventBus} via CDI event observation.
 */
@ApplicationScoped
public class PluginHookRegistry {

  private final Map<String, List<PluginHook>> pluginHooks = new ConcurrentHashMap<>();
  private final List<PluginHook> allHooks = new CopyOnWriteArrayList<>();

  /** Registers a hook contributed by a plugin. */
  public void registerHook(String pluginName, PluginHook hook) {
    pluginHooks.computeIfAbsent(pluginName, k -> new CopyOnWriteArrayList<>()).add(hook);
    allHooks.add(hook);
  }

  /** Unregisters all hooks for a given plugin. */
  public void unregisterPlugin(String pluginName) {
    List<PluginHook> removed = pluginHooks.remove(pluginName);
    if (removed != null) {
      allHooks.removeAll(removed);
    }
  }

  /** Returns all registered hooks across all plugins. */
  public List<PluginHook> getAllHooks() {
    return List.copyOf(allHooks);
  }

  /** Returns hooks for a specific plugin. */
  public List<PluginHook> getPluginHooks(String pluginName) {
    return List.copyOf(pluginHooks.getOrDefault(pluginName, List.of()));
  }

  /** Returns the number of registered hooks. */
  public int size() {
    return allHooks.size();
  }

  /** Clears all hooks. */
  public void clear() {
    pluginHooks.clear();
    allHooks.clear();
  }

  // ---- Hook execution methods ----

  public void fireBeforeCreate(HookContext context) {
    allHooks.forEach(h -> h.beforeCreate(context));
  }

  public void fireAfterCreate(HookContext context) {
    allHooks.forEach(h -> h.afterCreate(context));
  }

  public void fireBeforeUpdate(HookContext context) {
    allHooks.forEach(h -> h.beforeUpdate(context));
  }

  public void fireAfterUpdate(HookContext context) {
    allHooks.forEach(h -> h.afterUpdate(context));
  }

  public void fireBeforeDelete(HookContext context) {
    allHooks.forEach(h -> h.beforeDelete(context));
  }

  public void fireAfterDelete(HookContext context) {
    allHooks.forEach(h -> h.afterDelete(context));
  }

  public void fireBeforePublish(HookContext context) {
    allHooks.forEach(h -> h.beforePublish(context));
  }

  public void fireAfterPublish(HookContext context) {
    allHooks.forEach(h -> h.afterPublish(context));
  }

  public void fireBeforeUnpublish(HookContext context) {
    allHooks.forEach(h -> h.beforeUnpublish(context));
  }

  public void fireAfterUnpublish(HookContext context) {
    allHooks.forEach(h -> h.afterUnpublish(context));
  }

  public void fireBeforeFindOne(HookContext context) {
    allHooks.forEach(h -> h.beforeFindOne(context));
  }

  public void fireAfterFindOne(HookContext context) {
    allHooks.forEach(h -> h.afterFindOne(context));
  }

  public void fireBeforeFindMany(HookContext context) {
    allHooks.forEach(h -> h.beforeFindMany(context));
  }

  public void fireAfterFindMany(HookContext context) {
    allHooks.forEach(h -> h.afterFindMany(context));
  }
}
