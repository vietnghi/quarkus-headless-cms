package com.quarkus.cms.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.plugin.hook.PluginHook;
import com.quarkus.cms.plugin.hook.PluginHookRegistry;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the Plugin Hook Registry. */
class PluginHookRegistryTest {

  private PluginHookRegistry hookRegistry;

  @BeforeEach
  void setUp() {
    hookRegistry = new PluginHookRegistry();
  }

  @Test
  void testRegisterAndRetrieveHooks() {
    TestHook hook1 = new TestHook("hook1");
    TestHook hook2 = new TestHook("hook2");

    hookRegistry.registerHook("plugin-a", hook1);
    hookRegistry.registerHook("plugin-b", hook2);

    assertEquals(2, hookRegistry.size());
    assertEquals(1, hookRegistry.getPluginHooks("plugin-a").size());
    assertEquals(1, hookRegistry.getPluginHooks("plugin-b").size());
    assertEquals(0, hookRegistry.getPluginHooks("plugin-c").size());
  }

  @Test
  void testUnregisterPlugin() {
    hookRegistry.registerHook("plugin-a", new TestHook("hook1"));
    hookRegistry.registerHook("plugin-a", new TestHook("hook2"));
    assertEquals(2, hookRegistry.size());

    hookRegistry.unregisterPlugin("plugin-a");
    assertEquals(0, hookRegistry.size());
  }

  @Test
  void testHookExecution() {
    TestHook hook = new TestHook("test-hook");
    hookRegistry.registerHook("test", hook);

    com.quarkus.cms.plugin.hook.HookContext ctx =
        new com.quarkus.cms.plugin.hook.HookContext(
            "api::article.article",
            null,
            com.quarkus.cms.plugin.hook.HookContext.EventType.CREATE,
            com.quarkus.cms.plugin.hook.HookContext.Phase.BEFORE,
            Map.of("title", "Test"),
            "en",
            null);

    hookRegistry.fireBeforeCreate(ctx);
    assertTrue(hook.beforeCreateCalled);

    hookRegistry.fireAfterCreate(ctx);
    assertTrue(hook.afterCreateCalled);
  }

  @Test
  void testClear() {
    hookRegistry.registerHook("plugin-a", new TestHook("h1"));
    hookRegistry.registerHook("plugin-b", new TestHook("h2"));
    assertEquals(2, hookRegistry.size());

    hookRegistry.clear();
    assertEquals(0, hookRegistry.size());
  }

  // ---- Test hook implementation ----

  static class TestHook implements PluginHook {
    final String id;
    boolean beforeCreateCalled;
    boolean afterCreateCalled;

    TestHook(String id) {
      this.id = id;
    }

    @Override
    public String getHookId() {
      return id;
    }

    @Override
    public void beforeCreate(com.quarkus.cms.plugin.hook.HookContext context) {
      beforeCreateCalled = true;
    }

    @Override
    public void afterCreate(com.quarkus.cms.plugin.hook.HookContext context) {
      afterCreateCalled = true;
    }
  }
}
