# Lifecycle Hooks — Observer Guide

This document describes how to create custom lifecycle observers that react to CMS content events using the CDI event system.

## Architecture

The CMS fires CDI events at twelve lifecycle points — a **before** and **after** variant for each of six content operations:

| Operation | Before Event | After Event |
|-----------|-------------|------------|
| Create | `beforeCreate` | `afterCreate` |
| Update | `beforeUpdate` | `afterUpdate` |
| Delete | `beforeDelete` | `afterDelete` |
| Find One | `beforeFindOne` | `afterFindOne` |
| Find Many | `beforeFindMany` | `afterFindMany` |
| Publish | `beforePublish` | `afterPublish` |
| Unpublish | `beforeUnpublish` | `afterUnpublish` |

- **Before events** are synchronous and run in the same transaction as the operation. Use them for validation, data transformation, or to abort an operation by throwing an exception.
- **After events** fire after the operation has completed. Use them for notifications, audit logging, cache invalidation, and webhook dispatch.

## The LifecycleEvent Object

Every event is an instance of `com.quarkus.cms.webhooks.event.LifecycleEvent` with these fields:

| Field | Type | Description |
|-------|------|-------------|
| `eventType` | `EventType` enum | `CREATE`, `UPDATE`, `DELETE`, `FIND_ONE`, `FIND_MANY`, `PUBLISH`, `UNPUBLISH` |
| `phase` | `Phase` enum | `BEFORE` or `AFTER` |
| `contentType` | `String` | The content type UID (e.g. `api::article.article`) |
| `documentId` | `String` | The document ID (null for before-create and find-many) |
| `locale` | `String` | Locale code (e.g. `en`) |
| `data` | `Map<String, Object>` | The entry's field data (null for delete/find-many) |
| `userId` | `Long` | The ID of the user who initiated the operation |
| `timestamp` | `Instant` | When the event was created |

## Creating a Custom Observer

Use Jakarta CDI's `@Observes` annotation on a method parameter of type `LifecycleEvent`:

```java
import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ArticleValidator {

    /**
     * Validates article content before creation.
     * Throwing an exception here will prevent the entry from being persisted.
     */
    public void beforeArticleCreate(@Observes LifecycleEvent event) {
        if (event.getEventType() != EventType.CREATE || event.getPhase() != Phase.BEFORE) {
            return; // only interested in before-create
        }
        if (!"api::article.article".equals(event.getContentType())) {
            return; // only interested in articles
        }
        if (event.getData() == null) {
            return;
        }

        String title = (String) event.getData().get("title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Article title is required");
        }
        if (title.length() < 3) {
            throw new IllegalArgumentException("Article title must be at least 3 characters");
        }

        Log.infof("Validated article creation: %s", title);
    }
}
```

### Filtering by Event Type

Use `@Observes` with a qualifier or check fields in the observer method:

```java
public void onContentPublished(@Observes LifecycleEvent event) {
    if (event.getEventType() != EventType.PUBLISH || event.getPhase() != Phase.AFTER) {
        return;
    }
    // Send notification, invalidate cache, etc.
    Log.infof("Content published: %s/%s", event.getContentType(), event.getDocumentId());
}
```

### Using the @LifecycleHook Annotation

As an alternative to manual filtering, apply the `@LifecycleHook` interceptor binding:

```java
import com.quarkus.cms.webhooks.interceptor.LifecycleHook;
import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;

@ApplicationScoped
public class AuditLogger {

    @LifecycleHook(eventType = EventType.CREATE, phase = Phase.AFTER)
    @LifecycleHook(eventType = EventType.UPDATE, phase = Phase.AFTER)
    @LifecycleHook(eventType = EventType.DELETE, phase = Phase.AFTER)
    public void logContentChange(@Observes LifecycleEvent event) {
        Log.infof("AUDIT: %s %s on %s/%s by user %d",
            event.getEventType(),
            event.getPhase(),
            event.getContentType(),
            event.getDocumentId(),
            event.getUserId());
    }
}
```

## Async Observers

For long-running side effects (cache warming, external API calls, analytics), use `@ObservesAsync`:

```java
public void warmCache(@ObservesAsync LifecycleEvent event) {
    // This runs in a separate thread — do not throw exceptions here
    if (event.getEventType() == EventType.PUBLISH && event.getPhase() == Phase.AFTER) {
        // Refresh CDN, rebuild search index, etc.
    }
}
```

**Important:** Async observers cannot prevent the operation from completing and should never throw exceptions. Use synchronous `@Observes` for validation that can block the operation.

## Vert.x Event Bus Integration

In addition to CDI events, every lifecycle event is published to the Vert.x event bus:

```
Address format: cms.lifecycle.<eventType>.<phase>
Examples:       cms.lifecycle.create.before
                cms.lifecycle.update.after
                cms.lifecycle.publish.after
```

Non-CDI consumers (Quarkus verticles, message consumers) can react to these:

```java
@ConsumeEvent("cms.lifecycle.publish.after")
public void onPublishAfter(LifecycleEvent event) {
    // React to publish event from a Vert.x verticle
}
```

## Testing Your Observer

Extend your observer test with `@QuarkusTest` and inject the `Event<LifecycleEvent>` to fire events manually:

```java
@QuarkusTest
class ArticleValidatorTest {

    @Inject
    Event<LifecycleEvent> cdiEvent;

    @Test
    void shouldRejectBlankTitle() {
        LifecycleEvent event = new LifecycleEvent(
            EventType.CREATE, Phase.BEFORE,
            "api::article.article", null, "en",
            Map.of("title", ""), 1L);

        assertThrows(IllegalArgumentException.class, () -> cdiEvent.fire(event));
    }
}
```

## Related Documentation

- **Webhook Configuration**: See `AdminWebhooksResource` (REST API at `/admin/webhooks`)
- **Webhook Delivery Logs**: See `CmsWebhookDelivery` entity and the `/admin/webhooks/{id}/deliveries` endpoint
- **Strapi v5 Payload Format**: See `WebhookPayload` DTO and `WebhookPayloadBuilder`
