package com.quarkus.cms.runtime;

import io.quarkus.runtime.annotations.Recorder;

/**
 * Runtime recorder for the Quarkus Headless CMS extension. Acts as the bridge between build-time
 * processing and runtime initialization.
 *
 * <p>The deployment module calls methods on this recorder during the RUNTIME_INIT phase to set up
 * runtime services and pass build-time decisions to the running application.
 */
@Recorder
public class CmsRecorder {

  /**
   * Initializes CMS runtime services. Called during the RUNTIME_INIT phase after the
   * configuration is fully loaded.
   */
  public void initialize() {
    // Runtime initialization logic — schema scanning, service registration, etc.
    // This is where build-time decisions are applied to the running application.
  }
}
