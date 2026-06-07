package com.quarkus.cms.deployment;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.QuarkusUnitTest;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Basic verification test for the Quarkus Headless CMS extension.
 *
 * <p>Uses {@link QuarkusUnitTest} to test the extension's build processor in an isolated Quarkus
 * application with minimal dependencies.
 *
 * <p>TODO: Add more tests as feature modules are implemented: - Verify all build items are produced
 * - Test configuration injection - Test recorder initialization - Test CDI bean resolution
 */
class CmsProcessorTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () -> ShrinkWrap.create(JavaArchive.class).addClass(CmsProcessor.class))
          .withConfigurationResource("application.properties");

  @Test
  void extensionBuildsSuccessfully() {
    // The fact that we got here means QuarkusUnitTest booted
    // successfully with the CmsProcessor registered. This validates:
    // - The deployment module compiles
    // - The build processor can be discovered
    // - No missing dependencies
    assertTrue(true, "Extension should load without errors");
  }
}
