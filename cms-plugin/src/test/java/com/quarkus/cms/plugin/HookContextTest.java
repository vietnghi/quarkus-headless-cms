package com.quarkus.cms.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.plugin.hook.HookContext;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link HookContext}. */
class HookContextTest {

  @Test
  void testBasicConstruction() {
    HookContext ctx =
        new HookContext(
            "api::article.article",
            "doc-123",
            HookContext.EventType.CREATE,
            HookContext.Phase.BEFORE,
            Map.of("title", "Hello"),
            "en",
            1L);

    assertEquals("api::article.article", ctx.getContentType());
    assertEquals("doc-123", ctx.getDocumentId());
    assertEquals(HookContext.EventType.CREATE, ctx.getEventType());
    assertEquals(HookContext.Phase.BEFORE, ctx.getPhase());
    assertEquals("en", ctx.getLocale());
    assertEquals(1L, ctx.getUserId());
    assertFalse(ctx.isCancelled());
  }

  @Test
  void testCancelOperation() {
    HookContext ctx =
        new HookContext(
            "api::article.article",
            null,
            HookContext.EventType.CREATE,
            HookContext.Phase.BEFORE,
            Map.of(),
            "en",
            null);

    ctx.cancel("Validation failed");
    assertTrue(ctx.isCancelled());
    assertEquals("Validation failed", ctx.getCancelReason());
  }

  @Test
  void testCancelIgnoredInAfterPhase() {
    HookContext ctx =
        new HookContext(
            "api::article.article",
            null,
            HookContext.EventType.CREATE,
            HookContext.Phase.AFTER,
            Map.of(),
            "en",
            null);

    ctx.cancel("Should be ignored");
    assertFalse(ctx.isCancelled());
  }

  @Test
  void testSetDataInAfterPhase() {
    Map<String, Object> original = Map.of("title", "Original");
    HookContext ctx =
        new HookContext(
            "api::article.article",
            null,
            HookContext.EventType.CREATE,
            HookContext.Phase.AFTER,
            original,
            "en",
            null);

    ctx.setData(Map.of("title", "Modified"));
    assertEquals(original, ctx.getData()); // Should not change in AFTER phase
  }

  @Test
  void testSetDataInBeforePhase() {
    Map<String, Object> original = Map.of("title", "Original");
    HookContext ctx =
        new HookContext(
            "api::article.article",
            null,
            HookContext.EventType.CREATE,
            HookContext.Phase.BEFORE,
            original,
            "en",
            null);

    Map<String, Object> modified = Map.of("title", "Modified");
    ctx.setData(modified);
    assertEquals(modified, ctx.getData()); // Should change in BEFORE phase
  }
}
