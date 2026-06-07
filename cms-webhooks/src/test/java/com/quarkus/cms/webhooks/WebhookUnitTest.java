package com.quarkus.cms.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quarkus.cms.webhooks.dto.WebhookPayload;
import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;
import com.quarkus.cms.webhooks.service.WebhookPayloadBuilder;
import com.quarkus.cms.webhooks.service.WebhookSecurityService;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Unit tests for webhooks and lifecycle hooks (no DB needed). */
class WebhookUnitTest {

  private final WebhookPayloadBuilder payloadBuilder = new WebhookPayloadBuilder();
  private final WebhookSecurityService securityService = new WebhookSecurityService();

  // ---- LifecycleEvent ----

  @Test
  void lifecycleEvent_shouldConstructWithAllFields() {
    LifecycleEvent event =
        new LifecycleEvent(
            EventType.CREATE,
            Phase.AFTER,
            "api::article.article",
            "doc-123",
            "en",
            Map.of("title", "Hello"),
            42L);

    assertEquals(EventType.CREATE, event.getEventType());
    assertEquals(Phase.AFTER, event.getPhase());
    assertEquals("api::article.article", event.getContentType());
    assertEquals("doc-123", event.getDocumentId());
    assertEquals("en", event.getLocale());
    assertEquals("Hello", event.getData().get("title"));
    assertEquals(42L, event.getUserId());
    assertNotNull(event.getTimestamp());
  }

  @Test
  void lifecycleEvent_toEventKey_shouldReturnStrapiFormat() {
    LifecycleEvent createEvent =
        new LifecycleEvent(EventType.CREATE, Phase.AFTER, "article", null, "en", null, null);
    assertEquals("entry.create", createEvent.toEventKey());

    LifecycleEvent updateEvent =
        new LifecycleEvent(EventType.UPDATE, Phase.BEFORE, "article", null, "en", null, null);
    assertEquals("entry.update", updateEvent.toEventKey());

    LifecycleEvent deleteEvent =
        new LifecycleEvent(EventType.DELETE, Phase.AFTER, "article", null, "en", null, null);
    assertEquals("entry.delete", deleteEvent.toEventKey());

    LifecycleEvent publishEvent =
        new LifecycleEvent(EventType.PUBLISH, Phase.AFTER, "article", null, "en", null, null);
    assertEquals("entry.publish", publishEvent.toEventKey());

    LifecycleEvent unpublishEvent =
        new LifecycleEvent(EventType.UNPUBLISH, Phase.AFTER, "article", null, "en", null, null);
    assertEquals("entry.unpublish", unpublishEvent.toEventKey());

    LifecycleEvent findOneEvent =
        new LifecycleEvent(EventType.FIND_ONE, Phase.AFTER, "article", null, "en", null, null);
    assertEquals("entry.find_one", findOneEvent.toEventKey());

    LifecycleEvent findManyEvent =
        new LifecycleEvent(EventType.FIND_MANY, Phase.AFTER, "article", null, "en", null, null);
    assertEquals("entry.find_many", findManyEvent.toEventKey());
  }

  @Test
  void lifecycleEvent_toString_shouldIncludeKeyFields() {
    LifecycleEvent event =
        new LifecycleEvent(EventType.CREATE, Phase.BEFORE, "article", "doc-1", "en", null, null);
    String str = event.toString();
    assertTrue(str.contains("CREATE"));
    assertTrue(str.contains("BEFORE"));
    assertTrue(str.contains("article"));
    assertTrue(str.contains("doc-1"));
  }

  // ---- WebhookPayloadBuilder ----

  @Test
  void payloadBuilder_shouldBuildStrapiV5Payload() {
    LifecycleEvent event =
        new LifecycleEvent(
            EventType.CREATE,
            Phase.AFTER,
            "api::article.article",
            "doc-123",
            "en",
            Map.of("title", "Hello World", "content", "Lorem ipsum"),
            null);

    WebhookPayload payload = payloadBuilder.build(event, event.getData());

    assertEquals("entry.create", payload.getEvent());
    assertEquals("api::article.article", payload.getModel());
    assertEquals("doc-123", payload.getEntry().get("id"));
    assertEquals("en", payload.getEntry().get("locale"));
    assertEquals("Hello World", payload.getEntry().get("title"));
    assertEquals("Lorem ipsum", payload.getEntry().get("content"));
    assertFalse(payload.getIsRedelivery());
    assertNotNull(payloadBuilder.toJson(payload));
  }

  @Test
  void payloadBuilder_shouldBuildDeletePayloadWithoutData() {
    LifecycleEvent event =
        new LifecycleEvent(EventType.DELETE, Phase.AFTER, "article", "doc-456", "fr", null, 1L);

    WebhookPayload payload = payloadBuilder.build(event, null);

    assertEquals("entry.delete", payload.getEvent());
    assertEquals("doc-456", payload.getEntry().get("id"));
    assertEquals("fr", payload.getEntry().get("locale"));
    assertNull(payload.getEntry().get("title"));
  }

  @Test
  void payloadBuilder_redelivery_shouldSetFlag() {
    LifecycleEvent event =
        new LifecycleEvent(EventType.UPDATE, Phase.AFTER, "article", "doc-1", "en", Map.of(), null);

    WebhookPayload payload = payloadBuilder.buildRedelivery(event, Map.of());
    assertTrue(payload.getIsRedelivery());
  }

  @Test
  void payloadBuilder_toJson_shouldReturnValidJson() {
    LifecycleEvent event =
        new LifecycleEvent(
            EventType.CREATE, Phase.AFTER, "article", "doc-1", "en", Map.of("k", "v"), null);

    String json = payloadBuilder.toJson(payloadBuilder.build(event, Map.of("k", "v")));
    assertNotNull(json);
    assertTrue(json.contains("\"event\""));
    assertTrue(json.contains("entry.create"));
    assertTrue(json.contains("\"model\""));
    assertTrue(json.contains("article"));
  }

  @Test
  void payloadBuilder_shouldHandleNullEntryData() {
    LifecycleEvent event =
        new LifecycleEvent(EventType.DELETE, Phase.AFTER, "article", "doc-del", "en", null, null);

    WebhookPayload payload = payloadBuilder.build(event, null);
    assertEquals("doc-del", payload.getEntry().get("id"));
    assertEquals("en", payload.getEntry().get("locale"));
  }

  // ---- WebhookSecurityService ----

  @Test
  void securityService_shouldSignPayload() {
    String secret = "my-secret-key-123";
    String payload = "{\"event\":\"entry.create\"}";

    String signature = securityService.sign(secret, payload);
    assertNotNull(signature);
    assertFalse(signature.isBlank());
    // Base64 should not contain the raw secret
    assertFalse(signature.contains("my-secret-key"));
  }

  @Test
  void securityService_shouldVerifyValidSignature() {
    String secret = "my-secret-key-123";
    String payload = "{\"event\":\"entry.create\"}";

    String signature = securityService.sign(secret, payload);
    assertTrue(securityService.verify(secret, payload, signature));
  }

  @Test
  void securityService_shouldRejectWrongSecret() {
    String payload = "{\"event\":\"entry.create\"}";
    String signature = securityService.sign("secret-a", payload);
    assertFalse(securityService.verify("secret-b", payload, signature));
  }

  @Test
  void securityService_shouldRejectTamperedPayload() {
    String secret = "my-secret";
    String originalPayload = "{\"event\":\"entry.create\"}";
    String tamperedPayload = "{\"event\":\"entry.delete\"}";

    String signature = securityService.sign(secret, originalPayload);
    assertFalse(securityService.verify(secret, tamperedPayload, signature));
  }

  @Test
  void securityService_shouldReturnNullForBlankSecret() {
    assertNull(securityService.sign("", "payload"));
    assertNull(securityService.sign(null, "payload"));
  }

  @Test
  void securityService_shouldReturnNullForWhitespaceSecret() {
    assertNull(securityService.sign("   ", "payload"));
  }

  @Test
  void securityService_verify_shouldRejectNullSignature() {
    assertFalse(securityService.verify("secret", "payload", null));
  }

  @Test
  void securityService_verify_shouldRejectBlankSignature() {
    assertFalse(securityService.verify("secret", "payload", ""));
  }

  @Test
  void securityService_deterministicSigning_withSameInput() {
    String secret = "consistent-key";
    String payload = "same-payload";

    String sig1 = securityService.sign(secret, payload);
    String sig2 = securityService.sign(secret, payload);
    assertEquals(sig1, sig2);
  }

  // ---- WebhookPayload DTO ----

  @Test
  void webhookPayload_shouldHandleSetterMethods() {
    WebhookPayload payload = new WebhookPayload();
    payload.setEvent("entry.create");
    payload.setModel("article");
    payload.setCreatedAt("2024-01-01T00:00:00Z");
    payload.setIsRedelivery(true);

    assertEquals("entry.create", payload.getEvent());
    assertEquals("article", payload.getModel());
    assertEquals("2024-01-01T00:00:00Z", payload.getCreatedAt());
    assertTrue(payload.getIsRedelivery());
  }
}
