package com.quarkus.cms.runtime;

/**
 * Marker interface / entry-point for the CMS extension runtime.
 *
 * <p>This class serves as the canonical reference point for the extension. The deployment module
 * references this class to locate the runtime module's classes and resources at build time.
 */
public final class CmsExtension {

  private CmsExtension() {
    // Utility class — prevent instantiation
  }
}
