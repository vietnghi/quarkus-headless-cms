package com.quarkus.cms.webhooks.service;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

import com.quarkus.cms.webhooks.dto.WebhookPayload;
import com.quarkus.cms.webhooks.entity.CmsWebhook;
import com.quarkus.cms.webhooks.entity.CmsWebhookDelivery;
import com.quarkus.cms.webhooks.entity.CmsWebhookDelivery.DeliveryStatus;
import com.quarkus.cms.webhooks.event.LifecycleEvent;

import io.quarkus.logging.Log;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;

/**
 * Non-blocking webhook dispatcher using Vert.x HTTP client.
 *
 * <p>Dispatches HTTP POST requests to webhook subscribers with configurable retry logic
 * (exponential backoff). Each dispatch runs asynchronously on the Vert.x event loop, ensuring it
 * never blocks the main request thread.
 *
 * <h3>Retry behaviour</h3>
 *
 * On failure (non-2xx response or connection error), the dispatcher retries with exponential
 * backoff: first retry after 2s, second after 4s, third after 8s, etc., up to the webhook's {@code
 * maxRetries} setting. Each attempt is logged as a separate {@link CmsWebhookDelivery} row.
 */
@ApplicationScoped
public class WebhookDispatcher {

  private final Vertx vertx;
  private final WebhookPayloadBuilder payloadBuilder;
  private final WebhookSecurityService securityService;
  private WebClient client;

  @Inject
  public WebhookDispatcher(
      Vertx vertx, WebhookPayloadBuilder payloadBuilder, WebhookSecurityService securityService) {
    this.vertx = vertx;
    this.payloadBuilder = payloadBuilder;
    this.securityService = securityService;
  }

  @PostConstruct
  void init() {
    this.client = WebClient.create(vertx);
  }

  @PreDestroy
  void cleanup() {
    if (this.client != null) {
      this.client.close();
    }
  }

  /**
   * Dispatches a webhook event to a single subscriber asynchronously.
   *
   * @param webhook the webhook configuration
   * @param event the lifecycle event that triggered this dispatch
   * @param entryData the affected entry's data (nullable for DELETE events)
   */
  public void dispatch(CmsWebhook webhook, LifecycleEvent event, Map<String, Object> entryData) {
    WebhookPayload payload = payloadBuilder.build(event, entryData);
    String jsonBody = payloadBuilder.toJson(payload);
    String signature = securityService.sign(webhook.secret, jsonBody);

    deliverWithRetry(webhook, event, jsonBody, signature, 1);
  }

  // ---- Internal retry logic ----

  private void deliverWithRetry(
      CmsWebhook webhook, LifecycleEvent event, String jsonBody, String signature, int attempt) {

    CmsWebhookDelivery delivery = createDeliveryLog(webhook, event, attempt);

    Instant start = Instant.now();

    var request =
        client
            .postAbs(webhook.url)
            .putHeader("Content-Type", "application/json")
            .putHeader("User-Agent", "Quarkus-CMS-Webhook/1.0");

    // Add custom headers from webhook config
    if (webhook.headers != null) {
      for (Map.Entry<String, String> header : webhook.headers.entrySet()) {
        request.putHeader(header.getKey(), header.getValue());
      }
    }

    // Add HMAC signature header
    if (signature != null) {
      request.putHeader(WebhookSecurityService.SIGNATURE_HEADER, signature);
    }

    request
        .sendBuffer(io.vertx.mutiny.core.buffer.Buffer.buffer(jsonBody))
        .onItem()
        .transform(
            resp -> {
              long duration = Duration.between(start, Instant.now()).toMillis();
              int httpStatus = resp.statusCode();
              String responseBody = resp.bodyAsString();
              if (responseBody != null && responseBody.length() > 4096) {
                responseBody = responseBody.substring(0, 4096);
              }

              if (httpStatus >= 200 && httpStatus < 300) {
                updateDeliverySuccess(delivery, httpStatus, responseBody, duration);
                Log.debugf(
                    "Webhook %s delivered to %s: HTTP %d (attempt %d, %dms)",
                    webhook.name, webhook.url, httpStatus, attempt, duration);
              } else {
                handleFailure(
                    webhook,
                    event,
                    jsonBody,
                    signature,
                    attempt,
                    delivery,
                    httpStatus,
                    responseBody,
                    duration);
              }
              return resp;
            })
        .onFailure()
        .invoke(
            throwable -> {
              long duration = Duration.between(start, Instant.now()).toMillis();
              String errorMsg = throwable.getMessage();
              if (errorMsg != null && errorMsg.length() > 2048) {
                errorMsg = errorMsg.substring(0, 2048);
              }
              handleFailure(
                  webhook, event, jsonBody, signature, attempt, delivery, null, errorMsg, duration);
            })
        .subscribe()
        .with(
            resp -> {},
            throwable ->
                Log.errorf(
                    "Unhandled error dispatching webhook %s: %s",
                    webhook.name, throwable.getMessage()));
  }

  private void handleFailure(
      CmsWebhook webhook,
      LifecycleEvent event,
      String jsonBody,
      String signature,
      int attempt,
      CmsWebhookDelivery delivery,
      Integer httpStatus,
      String response,
      long duration) {

    if (attempt < webhook.maxRetries) {
      updateDeliveryRetrying(delivery, httpStatus, response, duration);

      // Exponential backoff: 2s, 4s, 8s, 16s, ...
      long delayMs = (long) Math.pow(2, attempt) * 1000L;
      Log.warnf(
          "Webhook %s delivery failed (attempt %d/%d), retrying in %dms",
          webhook.name, attempt, webhook.maxRetries, delayMs);

      vertx.setTimer(
          delayMs, ignored -> deliverWithRetry(webhook, event, jsonBody, signature, attempt + 1));
    } else {
      updateDeliveryFailed(delivery, httpStatus, response, duration);
      Log.errorf(
          "Webhook %s delivery FAILED after %d attempts: %s",
          webhook.name,
          webhook.maxRetries,
          response != null ? "HTTP " + httpStatus : "connection error");
    }
  }

  // ---- Delivery log persistence ----

  @Transactional(REQUIRES_NEW)
  CmsWebhookDelivery createDeliveryLog(CmsWebhook webhook, LifecycleEvent event, int attempt) {
    CmsWebhookDelivery d = new CmsWebhookDelivery();
    d.webhookId = webhook.id;
    d.webhookName = webhook.name;
    d.eventType = event.toEventKey();
    d.documentId = event.getDocumentId();
    d.contentType = event.getContentType();
    d.targetUrl = webhook.url;
    d.status = DeliveryStatus.PENDING.name();
    d.attemptNumber = attempt;
    d.maxAttempts = webhook.maxRetries;
    d.persist();
    return d;
  }

  @Transactional(REQUIRES_NEW)
  void updateDeliverySuccess(CmsWebhookDelivery d, int httpStatus, String response, long duration) {
    d.status = DeliveryStatus.SUCCESS.name();
    d.httpStatus = httpStatus;
    d.responseBody = response;
    d.durationMs = duration;
    d.persist();
  }

  @Transactional(REQUIRES_NEW)
  void updateDeliveryRetrying(
      CmsWebhookDelivery d, Integer httpStatus, String response, long duration) {
    d.status = DeliveryStatus.RETRYING.name();
    d.httpStatus = httpStatus;
    d.responseBody = response;
    d.durationMs = duration;
    d.persist();
  }

  @Transactional(REQUIRES_NEW)
  void updateDeliveryFailed(CmsWebhookDelivery d, Integer httpStatus, String error, long duration) {
    d.status = DeliveryStatus.FAILED.name();
    d.httpStatus = httpStatus;
    if (httpStatus != null) {
      d.responseBody = error;
    } else {
      d.errorMessage = error;
    }
    d.durationMs = duration;
    d.persist();
  }
}
