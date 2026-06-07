package com.quarkus.cms.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.plugin.admin.AdminPageDefinition;
import com.quarkus.cms.plugin.content.ContentTypeExtension;
import com.quarkus.cms.plugin.seo.SeoPlugin;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit tests for the example {@link SeoPlugin}. */
class SeoPluginTest {

  private final SeoPlugin seoPlugin = new SeoPlugin();

  @Test
  void testMetadata() {
    PluginMetadata meta = seoPlugin.getMetadata();
    assertEquals("cms-seo-plugin", meta.getName());
    assertEquals("1.0.0", meta.getVersion());
    assertEquals("SEO Plugin", meta.getDisplayName());
  }

  @Test
  void testContentTypeExtensions() {
    List<ContentTypeExtension> extensions = seoPlugin.getContentTypeExtensions();
    assertEquals(1, extensions.size());

    ContentTypeExtension ext = extensions.get(0);
    assertEquals("*", ext.getTargetContentType());
    assertFalse(ext.isNewContentType());

    List<FieldDefinition> fields = ext.getAdditionalFields();
    assertEquals(3, fields.size());

    // Verify metaTitle field
    FieldDefinition metaTitle = fields.get(0);
    assertEquals("metaTitle", metaTitle.getName());
    assertEquals(FieldType.STRING, metaTitle.getType());
    assertFalse(metaTitle.isRequired());
    assertEquals(Integer.valueOf(70), metaTitle.getMaxLength());

    // Verify metaDescription field
    FieldDefinition metaDesc = fields.get(1);
    assertEquals("metaDescription", metaDesc.getName());
    assertEquals(FieldType.TEXT, metaDesc.getType());

    // Verify metaKeywords field
    FieldDefinition metaKeywords = fields.get(2);
    assertEquals("metaKeywords", metaKeywords.getName());
    assertEquals(FieldType.STRING, metaKeywords.getType());
  }

  @Test
  void testAdminPanelExtensions() {
    List<com.quarkus.cms.plugin.admin.AdminPanelExtension> adminExts =
        seoPlugin.getAdminPanelExtensions();
    assertEquals(1, adminExts.size());

    List<AdminPageDefinition> pages = adminExts.get(0).getAdminPages();
    assertEquals(1, pages.size());
    assertEquals("seo", pages.get(0).getId());
    assertEquals("SEO Settings", pages.get(0).getDisplayName());
    assertEquals("/admin/plugins/seo", pages.get(0).getRoutePath());
  }

  @Test
  void testHooks() {
    List<com.quarkus.cms.plugin.hook.PluginHook> hooks = seoPlugin.getHooks();
    assertEquals(1, hooks.size());
    assertEquals("cms-seo-plugin.auto-description", hooks.get(0).getHookId());
  }

  @Test
  void testPluginIsEnabled() {
    assertTrue(seoPlugin.isEnabled());
  }
}
