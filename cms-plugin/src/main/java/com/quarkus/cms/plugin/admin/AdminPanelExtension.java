package com.quarkus.cms.plugin.admin;

import java.util.List;

/**
 * SPI interface for plugins that contribute admin panel pages or routes.
 *
 * <p>Each admin panel extension defines a page that appears in the admin UI sidebar, along with the
 * React component path and any required permissions. This mirrors Strapi's admin panel plugin API
 * where plugins can register custom pages in the admin interface.
 */
public interface AdminPanelExtension {

  /** Returns a list of admin page definitions contributed by this plugin. */
  List<AdminPageDefinition> getAdminPages();

  /**
   * Returns custom menu items to inject into the admin sidebar navigation. Each entry maps a menu
   * label to its path and optional icon.
   */
  default List<AdminMenuItem> getMenuItems() {
    return List.of();
  }

  /** A menu item in the admin sidebar. */
  record AdminMenuItem(
      String label, String path, String icon, int order, List<String> permissions) {

    public AdminMenuItem {
      if (label == null || label.isBlank()) throw new IllegalArgumentException("label is required");
      if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");
      if (permissions == null) permissions = List.of();
    }

    public AdminMenuItem(String label, String path, String icon) {
      this(label, path, icon, 100, List.of());
    }
  }
}
