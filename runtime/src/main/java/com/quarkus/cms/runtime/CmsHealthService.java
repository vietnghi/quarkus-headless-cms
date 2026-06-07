package com.quarkus.cms.runtime;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Health service for the CMS extension.
 *
 * <p>Provides liveness and readiness checks for the CMS subsystem. Called by the integration test
 * to verify the extension loads correctly.
 *
 * <p>TODO: Implement actual health checks for each feature module - Database connectivity check -
 * Schema registry health - Media storage accessibility - Auth subsystem status
 */
@ApplicationScoped
public class CmsHealthService {

  private final CmsConfig config;

  public CmsHealthService(CmsConfig config) {
    this.config = config;
  }

  /**
   * Returns the health status of the CMS extension.
   *
   * @return a map containing status, cms name, and version
   */
  public java.util.Map<String, String> getHealth() {
    return java.util.Map.of(
        "status", "ok",
        "cms", "quarkus-headless-cms",
        "version", "1.0.0-SNAPSHOT");
  }

  /**
   * Returns whether the CMS extension is enabled.
   *
   * <p>TODO: Integrate with feature toggles for per-module enablement
   */
  public boolean isEnabled() {
    return config.enabled();
  }
}
