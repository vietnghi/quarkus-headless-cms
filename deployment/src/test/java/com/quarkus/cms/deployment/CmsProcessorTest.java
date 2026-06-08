package com.quarkus.cms.deployment;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.deployment.builditem.FeatureBuildItem;

import org.junit.jupiter.api.Test;

/**
 * Basic unit test for the {@link CmsProcessor} extension.
 */
class CmsProcessorTest {

  @Test
  void featureBuildItemIsCreated() {
    var item = new CmsProcessor().feature();
    assertNotNull(item);
    assertEquals("quarkus-headless-cms", item.getName());
  }
}
