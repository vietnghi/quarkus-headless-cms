package com.quarkus.cms.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.plugin.admin.AdminPageDefinition;
import com.quarkus.cms.plugin.content.ContentTypeExtension;
import com.quarkus.cms.plugin.endpoint.EndpointRegistration;
import com.quarkus.cms.plugin.hook.PluginHook;
import com.quarkus.cms.plugin.hook.PluginHookRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PluginRegistry}. */
class PluginRegistryTest {

  private PluginRegistry registry;
  private PluginHookRegistry hookRegistry;

  @BeforeEach
  void setUp() {
    hookRegistry = new PluginHookRegistry();
    registry = new TestPluginRegistry(hookRegistry);
  }

  @Test
  void testRegisterAndActivatePlugin() {
    registry.registerPlugins(List.of(new TestPlugin()));
    registry.activateAllPlugins();

    assertEquals(1, registry.getTotalCount());
    assertEquals(1, registry.getActiveCount());
    assertEquals(PluginRegistry.PluginState.ACTIVE, registry.getPluginState("test-plugin"));
    assertNotNull(registry.getPlugin("test-plugin"));
    assertNotNull(registry.getPluginMetadata("test-plugin"));
  }

  @Test
  void testContentTypeExtensionRegistration() {
    registry.registerPlugins(List.of(new TestPlugin()));
    registry.activateAllPlugins();

    List<ContentTypeExtension> extensions = registry.getAllContentTypeExtensions();
    assertFalse(extensions.isEmpty());
    assertEquals("*", extensions.get(0).getTargetContentType());
  }

  @Test
  void testEndpointRegistration() {
    registry.registerPlugins(List.of(new TestPlugin()));
    registry.activateAllPlugins();

    List<EndpointRegistration> endpoints = registry.getAllEndpointRegistrations();
    assertFalse(endpoints.isEmpty());
    assertEquals("GET", endpoints.get(0).getMethod());
  }

  @Test
  void testAdminPageRegistration() {
    registry.registerPlugins(List.of(new TestPlugin()));
    registry.activateAllPlugins();

    List<AdminPageDefinition> pages = registry.getAllAdminPages();
    assertFalse(pages.isEmpty());
    assertEquals("test-page", pages.get(0).getId());
  }

  @Test
  void testHookRegistration() {
    registry.registerPlugins(List.of(new TestPlugin()));
    registry.activateAllPlugins();

    assertEquals(1, hookRegistry.size());
  }

  @Test
  void testDisabledPluginNotActivated() {
    CmsPlugin disabledPlugin =
        new CmsPlugin() {
          @Override
          public PluginMetadata getMetadata() {
            return PluginMetadata.builder("disabled-plugin", "1.0.0").build();
          }

          @Override
          public void register(PluginRegistrationContext context) {}

          @Override
          public void unregister() {}

          @Override
          public boolean isEnabled() {
            return false;
          }
        };

    registry.registerPlugins(List.of(disabledPlugin));
    registry.activateAllPlugins();

    assertEquals(PluginRegistry.PluginState.DISABLED, registry.getPluginState("disabled-plugin"));
    assertEquals(0, registry.getActiveCount());
  }

  @Test
  void testShutdownDeactivatesPlugins() {
    registry.registerPlugins(List.of(new TestPlugin()));
    registry.activateAllPlugins();
    assertEquals(1, registry.getActiveCount());

    registry.shutdown();

    assertEquals(0, registry.getTotalCount());
    assertEquals(0, registry.getActiveCount());
    assertTrue(registry.getAllContentTypeExtensions().isEmpty());
  }

  @Test
  void testPluginSystemDisabled() {
    PluginRegistry disabledRegistry = new TestPluginRegistry(hookRegistry, false);

    // Even if we register plugins, they shouldn't activate if the system is disabled
    disabledRegistry.registerPlugins(List.of(new TestPlugin()));
    disabledRegistry.activateAllPlugins();

    // Plugins are registered but the system reports disabled
    assertFalse(disabledRegistry.isEnabled());
  }

  // ---- Test plugin implementation ----

  static class TestPlugin implements CmsPlugin {
    private static final PluginMetadata METADATA =
        PluginMetadata.builder("test-plugin", "1.0.0")
            .displayName("Test Plugin")
            .description("A plugin for testing")
            .author("Test")
            .build();

    @Override
    public PluginMetadata getMetadata() {
      return METADATA;
    }

    @Override
    public void register(PluginRegistrationContext context) {
      context.registerContentTypeExtension(
          new ContentTypeExtension() {
            @Override
            public String getTargetContentType() {
              return "*";
            }

            @Override
            public List<com.quarkus.cms.core.schema.model.FieldDefinition> getAdditionalFields() {
              return List.of();
            }
          });
      context.registerEndpoint(EndpointRegistration.builder("GET", "/test", ctx -> "ok").build());
      context.registerAdminPage(
          AdminPageDefinition.builder("test-page")
              .displayName("Test")
              .componentPath("/test/Page")
              .build());
      context.registerHook(
          new PluginHook() {
            @Override
            public String getHookId() {
              return "test-hook";
            }
          });
    }

    @Override
    public void unregister() {
      /* no-op */
    }
  }

  // ---- Test-only PluginRegistry subclass ----

  static class TestPluginRegistry extends PluginRegistry {

    TestPluginRegistry(PluginHookRegistry hookRegistry) {
      this(hookRegistry, true);
    }

    TestPluginRegistry(PluginHookRegistry hookRegistry, boolean enabled) {
      this.hookRegistry = hookRegistry;
      this.config = createTestConfig(enabled, true, false);
    }

    static PluginConfig createTestConfig(
        boolean enabled, boolean classpathScan, boolean externalJars) {
      return new PluginConfig() {
        @Override
        public boolean enabled() {
          return enabled;
        }

        @Override
        public String pluginDirectory() {
          return "plugins";
        }

        @Override
        public boolean classpathScanningEnabled() {
          return classpathScan;
        }

        @Override
        public boolean externalJarsEnabled() {
          return externalJars;
        }

        @Override
        public Optional<String> exclude() {
          return Optional.empty();
        }

        @Override
        public Map<String, String> pluginConfig() {
          return Collections.emptyMap();
        }
      };
    }
  }
}
