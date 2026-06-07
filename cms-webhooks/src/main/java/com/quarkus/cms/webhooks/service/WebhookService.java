package com.quarkus.cms.webhooks.service;

import com.quarkus.cms.webhooks.dto.WebhookConfig;
import com.quarkus.cms.webhooks.entity.CmsWebhook;
import com.quarkus.cms.webhooks.entity.CmsWebhookDelivery;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service for managing webhook configurations — create, read, update, delete, and enable/disable.
 *
 * <p>Delegates persistence to the Panache active record pattern on {@link CmsWebhook} and provides
 * a clean business-logic layer above the entities.
 */
@ApplicationScoped
public class WebhookService {

  /** Registers a new webhook. */
  @Transactional
  public CmsWebhook create(WebhookConfig config, Long createdById) {
    CmsWebhook webhook = new CmsWebhook();
    webhook.name = config.getName();
    webhook.url = config.getUrl();
    webhook.events = config.getEvents() != null ? config.getEvents() : List.of();
    webhook.headers = config.getHeaders() != null ? config.getHeaders() : Map.of();
    webhook.secret = config.getSecret();
    webhook.isEnabled = config.isEnabled();
    webhook.maxRetries = config.getMaxRetries();
    webhook.timeoutMs = config.getTimeoutMs();
    webhook.createdById = createdById;
    webhook.persist();

    Log.infof("Created webhook '%s' -> %s (events=%s)", webhook.name, webhook.url, webhook.events);
    return webhook;
  }

  /** Lists all webhooks (including disabled). */
  public List<CmsWebhook> listAll() {
    return CmsWebhook.listAll();
  }

  /** Lists all enabled webhooks. */
  public List<CmsWebhook> listEnabled() {
    return CmsWebhook.findEnabled();
  }

  /** Finds webhooks subscribed to a specific event. */
  public List<CmsWebhook> listByEvent(String event) {
    return CmsWebhook.findByEvent(event);
  }

  /** Finds a single webhook by ID. */
  public CmsWebhook findById(Long id) {
    return CmsWebhook.findById(id);
  }

  /** Updates an existing webhook. */
  @Transactional
  public CmsWebhook update(Long webhookId, WebhookConfig config) {
    CmsWebhook webhook = CmsWebhook.findById(webhookId);
    if (webhook == null) {
      throw new IllegalArgumentException("Webhook not found: " + webhookId);
    }
    if (config.getName() != null) {
      webhook.name = config.getName();
    }
    if (config.getUrl() != null) {
      webhook.url = config.getUrl();
    }
    if (config.getEvents() != null) {
      webhook.events = config.getEvents();
    }
    if (config.getHeaders() != null) {
      webhook.headers = config.getHeaders();
    }
    if (config.getSecret() != null) {
      webhook.secret = config.getSecret();
    }
    webhook.isEnabled = config.isEnabled();
    webhook.maxRetries = config.getMaxRetries();
    webhook.timeoutMs = config.getTimeoutMs();
    webhook.persist();

    Log.infof("Updated webhook %d: '%s'", webhookId, webhook.name);
    return webhook;
  }

  /** Enables a webhook. */
  @Transactional
  public void enable(Long webhookId) {
    CmsWebhook webhook = requireWebhook(webhookId);
    webhook.isEnabled = true;
    webhook.persist();
    Log.infof("Enabled webhook %d", webhookId);
  }

  /** Disables a webhook. */
  @Transactional
  public void disable(Long webhookId) {
    CmsWebhook webhook = requireWebhook(webhookId);
    webhook.isEnabled = false;
    webhook.persist();
    Log.infof("Disabled webhook %d", webhookId);
  }

  /** Deletes a webhook permanently. */
  @Transactional
  public void delete(Long webhookId) {
    CmsWebhook.deleteById(webhookId);
    Log.infof("Deleted webhook %d", webhookId);
  }

  /** Gets the delivery log for a webhook, newest first. */
  public List<CmsWebhookDelivery> getDeliveryLog(Long webhookId) {
    return CmsWebhookDelivery.findByWebhook(webhookId);
  }

  /** Gets the most recent delivery for a webhook. */
  public CmsWebhookDelivery getLatestDelivery(Long webhookId) {
    return CmsWebhookDelivery.findLatest(webhookId);
  }

  /** Gets the count of deliveries for a webhook since a given time. */
  public long getDeliveryCountSince(Long webhookId, java.time.Instant since) {
    return CmsWebhookDelivery.countSince(webhookId, since);
  }

  private CmsWebhook requireWebhook(Long id) {
    CmsWebhook webhook = CmsWebhook.findById(id);
    if (webhook == null) {
      throw new IllegalArgumentException("Webhook not found: " + id);
    }
    return webhook;
  }
}
