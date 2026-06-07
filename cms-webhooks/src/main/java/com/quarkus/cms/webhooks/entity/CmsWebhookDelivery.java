package com.quarkus.cms.webhooks.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Delivery log for webhook dispatches.
 *
 * <p>Each row records a single delivery attempt to a webhook endpoint, including the HTTP status,
 * response body snippet, errors, and timing. This is the audit trail for webhook dispatch
 * reliability.
 */
@Entity
@Table(
    name = "cms_webhook_deliveries",
    indexes = {
      @Index(name = "idx_deliveries_webhook", columnList = "webhook_id"),
      @Index(name = "idx_deliveries_event", columnList = "event_type"),
      @Index(name = "idx_deliveries_status", columnList = "status"),
      @Index(name = "idx_deliveries_created", columnList = "created_at")
    })
public class CmsWebhookDelivery extends PanacheEntityBase {

  public enum DeliveryStatus {
    PENDING,
    SUCCESS,
    FAILED,
    RETRYING
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "webhook_id", nullable = false)
  public Long webhookId;

  @Column(name = "webhook_name", length = 255)
  public String webhookName;

  @Column(name = "event_type", nullable = false, length = 100)
  public String eventType;

  @Column(name = "document_id", length = 36)
  public String documentId;

  @Column(name = "content_type", length = 100)
  public String contentType;

  @Column(name = "target_url", nullable = false, length = 2048)
  public String targetUrl;

  @Column(name = "status", nullable = false, length = 20)
  public String status = DeliveryStatus.PENDING.name();

  @Column(name = "http_status")
  public Integer httpStatus;

  @Column(name = "response_body", length = 4096)
  public String responseBody;

  @Column(name = "error_message", length = 2048)
  public String errorMessage;

  @Column(name = "attempt_number", nullable = false)
  public int attemptNumber = 1;

  @Column(name = "max_attempts", nullable = false)
  public int maxAttempts = 3;

  @Column(name = "duration_ms")
  public Long durationMs;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  /** Returns all deliveries for a specific webhook, newest first. */
  public static java.util.List<CmsWebhookDelivery> findByWebhook(Long webhookId) {
    return list("webhookId = ?1 order by createdAt desc", webhookId);
  }

  /** Returns the most recent delivery for a webhook. */
  public static CmsWebhookDelivery findLatest(Long webhookId) {
    return find("webhookId = ?1 order by createdAt desc", webhookId).firstResult();
  }

  /** Returns deliveries with a specific status, for retry management. */
  public static java.util.List<CmsWebhookDelivery> findByStatus(String status) {
    return list("status = ?1 order by createdAt desc", status);
  }

  /** Counts deliveries for a webhook since a given time. */
  public static long countSince(Long webhookId, Instant since) {
    return count("webhookId = ?1 and createdAt >= ?2", webhookId, since);
  }
}
