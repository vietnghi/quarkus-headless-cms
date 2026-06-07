package com.quarkus.cms.plugin.hook;

/**
 * SPI interface for plugin lifecycle hooks.
 *
 * <p>Plugins can implement this interface to hook into content lifecycle events. Hooks are invoked
 * before and after CRUD operations on content entries.
 *
 * <p>This mirrors Strapi's lifecycle hook system where plugins can register lifecycle functions
 * that execute at specific phases.
 *
 * <p>Example (SEO plugin auto-generating meta descriptions):
 *
 * <pre>{@code
 * public class SeoHook implements PluginHook {
 *   public void beforeCreate(HookContext ctx) {
 *     // Auto-generate meta description from content
 *   }
 * }
 * }</pre>
 */
public interface PluginHook {

  /** Unique identifier for this hook within the plugin. */
  String getHookId();

  /** Called before a content entry is created. */
  default void beforeCreate(HookContext context) {}

  /** Called after a content entry is created. */
  default void afterCreate(HookContext context) {}

  /** Called before a content entry is updated. */
  default void beforeUpdate(HookContext context) {}

  /** Called after a content entry is updated. */
  default void afterUpdate(HookContext context) {}

  /** Called before a content entry is deleted. */
  default void beforeDelete(HookContext context) {}

  /** Called after a content entry is deleted. */
  default void afterDelete(HookContext context) {}

  /** Called before a content entry is published. */
  default void beforePublish(HookContext context) {}

  /** Called after a content entry is published. */
  default void afterPublish(HookContext context) {}

  /** Called before a content entry is unpublished. */
  default void beforeUnpublish(HookContext context) {}

  /** Called after a content entry is unpublished. */
  default void afterUnpublish(HookContext context) {}

  /** Called before a content entry is queried (find one). */
  default void beforeFindOne(HookContext context) {}

  /** Called after a content entry is queried (find one). */
  default void afterFindOne(HookContext context) {}

  /** Called before content entries are queried (find many). */
  default void beforeFindMany(HookContext context) {}

  /** Called after content entries are queried (find many). */
  default void afterFindMany(HookContext context) {}
}
