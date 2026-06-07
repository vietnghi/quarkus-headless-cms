package com.quarkus.cms.webhooks.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Webhook registration for event-driven HTTP callbacks.
 *
 * <p>Each webhook listens for one or more lifecycle events (e.g. "entry.create", "entry.publish")
 * and fires an HTTP POST to the configured URL with a JSON payload containing event details.
 */
@Entity
@Table(
    name = "cms_webhooks",
    indexes = {@Index(name = "idx_webhooks_enabled", columnList = "is_enabled")})
public class CmsWebhook extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  public String name;

  @NotBlank
  @Size(max = 2048)
  @Column(nullable = false, length = 2048)
  public String url;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "events", columnDefinition = "jsonb", nullable = false)
  public List<String> events = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "headers", columnDefinition = "jsonb")
  public Map<String, String> headers = new HashMap<>();

  @Column(name = "is_enabled", nullable = false)
  public boolean isEnabled = true;

  /** HMAC secret key for signing webhook payloads. If null/empty, payloads are unsigned. */
  @Size(max = 512)
  @Column(name = "secret", length = 512)
  public String secret;

  /** Maximum retry attempts for failed deliveries (default: 3). */
  @Column(name = "max_retries")
  public int maxRetries = 3;

  /** Timeout in milliseconds for each delivery attempt (default: 10000). */
  @Column(name = "timeout_ms")
  public int timeoutMs = 10000;

  @Column(name = "created_by_id")
  public Long createdById;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  /** Lists all enabled webhooks. */
  public static java.util.List<CmsWebhook> findEnabled() {
    return list("isEnabled = true");
  }

  /** Lists all webhooks subscribed to a specific event. */
  public static java.util.List<CmsWebhook> findByEvent(String event) {
    return list("isEnabled = true and events like ?1", "%" + event + "%");
  }

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
