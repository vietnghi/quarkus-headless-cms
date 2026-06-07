package com.quarkus.cms.webhooks.repository;

import com.quarkus.cms.webhooks.entity.CmsWebhook;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

/** Repository for {@link CmsWebhook} webhook registration operations. */
@ApplicationScoped
public class CmsWebhookRepository {

  /** Registers a new webhook. */
  @Transactional
  public CmsWebhook create(
      String name, String url, List<String> events, Map<String, String> headers, Long createdById) {
    CmsWebhook webhook = new CmsWebhook();
    webhook.name = name;
    webhook.url = url;
    webhook.events = events;
    if (headers != null) {
      webhook.headers = headers;
    }
    webhook.createdById = createdById;
    webhook.persist();
    Log.infof("Registered webhook: %s -> %s (events=%s)", name, url, events);
    return webhook;
  }

  /** Lists all enabled webhooks. */
  public List<CmsWebhook> listEnabled() {
    return CmsWebhook.findEnabled();
  }

  /** Lists all webhooks (including disabled). */
  public List<CmsWebhook> listAll() {
    return CmsWebhook.listAll();
  }

  /** Lists webhooks subscribed to a specific event. */
  public List<CmsWebhook> listByEvent(String event) {
    return CmsWebhook.findByEvent(event);
  }

  /** Updates a webhook's configuration. */
  @Transactional
  public CmsWebhook update(
      Long webhookId, String name, String url, List<String> events, Map<String, String> headers) {
    CmsWebhook webhook = CmsWebhook.findById(webhookId);
    if (webhook == null) {
      throw new IllegalArgumentException("Webhook not found: " + webhookId);
    }
    if (name != null) {
      webhook.name = name;
    }
    if (url != null) {
      webhook.url = url;
    }
    if (events != null) {
      webhook.events = events;
    }
    if (headers != null) {
      webhook.headers = headers;
    }
    webhook.persist();
    Log.infof("Updated webhook: %d", webhookId);
    return webhook;
  }

  /** Enables a webhook. */
  @Transactional
  public void enable(Long webhookId) {
    CmsWebhook webhook = CmsWebhook.findById(webhookId);
    if (webhook == null) {
      throw new IllegalArgumentException("Webhook not found: " + webhookId);
    }
    webhook.isEnabled = true;
    webhook.persist();
    Log.infof("Enabled webhook: %d", webhookId);
  }

  /** Disables a webhook. */
  @Transactional
  public void disable(Long webhookId) {
    CmsWebhook webhook = CmsWebhook.findById(webhookId);
    if (webhook == null) {
      throw new IllegalArgumentException("Webhook not found: " + webhookId);
    }
    webhook.isEnabled = false;
    webhook.persist();
    Log.infof("Disabled webhook: %d", webhookId);
  }

  /** Deletes a webhook permanently. */
  @Transactional
  public void delete(Long webhookId) {
    CmsWebhook.deleteById(webhookId);
    Log.infof("Deleted webhook: %d", webhookId);
  }
}
