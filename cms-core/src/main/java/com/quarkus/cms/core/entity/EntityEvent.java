package com.quarkus.cms.core.entity;

import com.quarkus.cms.core.domain.CmsEntry;

import java.time.Instant;
import java.util.Map;

/**
 * CDI event payload fired at entity CRUD lifecycle boundaries.
 *
 * <p>Observers can filter on {@code eventType} and {@code phase} to react at specific points
 * during an entity's lifecycle — before creation (to validate or transform data), after creation
 * (to trigger side-effects), before/after update, delete, publish, unpublish, find, etc.
 *
 * <p>This is the entity-level hook mechanism, distinct from the webhook {@code LifecycleEvent}
 * which targets external webhook dispatch. {@code EntityEvent} is fired on the CDI event bus
 * and can be observed by any {@code @Observes EntityEvent} method in the same application.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @ApplicationScoped
 * public class MyPlugin {
 *     void onBeforeCreate(@Observes @EntityHook(phase = EntityEvent.Phase.BEFORE) EntityEvent event) {
 *         // validate or transform event.getData() before persistence
 *     }
 * }
 * }</pre>
 */
public class EntityEvent {

    public enum EventType {
        CREATE,
        UPDATE,
        DELETE,
        PUBLISH,
        UNPUBLISH,
        DISCARD_DRAFT,
        FIND_ONE,
        FIND_MANY
    }

    public enum Phase {
        BEFORE,
        AFTER
    }

    private final EventType eventType;
    private final Phase phase;
    private final String contentType;
    private final String documentId;
    private final String locale;
    private final Map<String, Object> data;
    private final Long userId;
    private final CmsEntry entry;
    private final Instant timestamp;

    public EntityEvent(
            EventType eventType,
            Phase phase,
            String contentType,
            String documentId,
            String locale,
            Map<String, Object> data,
            Long userId,
            CmsEntry entry) {
        this.eventType = eventType;
        this.phase = phase;
        this.contentType = contentType;
        this.documentId = documentId;
        this.locale = locale;
        this.data = data;
        this.userId = userId;
        this.entry = entry;
        this.timestamp = Instant.now();
    }

    // ---- Factory methods ----

    /** Creates a BEFORE CREATE event with entry data before persistence. */
    public static EntityEvent beforeCreate(String contentType, Map<String, Object> data, String locale, Long userId) {
        return new EntityEvent(EventType.CREATE, Phase.BEFORE, contentType, null, locale, data, userId, null);
    }

    /** Creates an AFTER CREATE event with the persisted entry. */
    public static EntityEvent afterCreate(String contentType, CmsEntry entry, Map<String, Object> data, String locale, Long userId) {
        return new EntityEvent(EventType.CREATE, Phase.AFTER, contentType, entry.documentId, locale, data, userId, entry);
    }

    /** Creates a BEFORE UPDATE event. */
    public static EntityEvent beforeUpdate(String contentType, String documentId, Map<String, Object> data, String locale, Long userId) {
        return new EntityEvent(EventType.UPDATE, Phase.BEFORE, contentType, documentId, locale, data, userId, null);
    }

    /** Creates an AFTER UPDATE event with the updated entry. */
    public static EntityEvent afterUpdate(String contentType, CmsEntry entry, Map<String, Object> data, String locale, Long userId) {
        return new EntityEvent(EventType.UPDATE, Phase.AFTER, contentType, entry.documentId, locale, data, userId, entry);
    }

    /** Creates a BEFORE DELETE event. */
    public static EntityEvent beforeDelete(String contentType, String documentId, String locale, Long userId) {
        return new EntityEvent(EventType.DELETE, Phase.BEFORE, contentType, documentId, locale, null, userId, null);
    }

    /** Creates an AFTER DELETE event. */
    public static EntityEvent afterDelete(String contentType, String documentId, String locale, Long userId) {
        return new EntityEvent(EventType.DELETE, Phase.AFTER, contentType, documentId, locale, null, userId, null);
    }

    /** Creates a BEFORE PUBLISH event. */
    public static EntityEvent beforePublish(String contentType, String documentId, String locale, Long userId) {
        return new EntityEvent(EventType.PUBLISH, Phase.BEFORE, contentType, documentId, locale, null, userId, null);
    }

    /** Creates an AFTER PUBLISH event with the published entry. */
    public static EntityEvent afterPublish(String contentType, CmsEntry entry, String locale, Long userId) {
        return new EntityEvent(EventType.PUBLISH, Phase.AFTER, contentType, entry.documentId, locale, entry.data, userId, entry);
    }

    /** Creates a BEFORE UNPUBLISH event. */
    public static EntityEvent beforeUnpublish(String contentType, String documentId, String locale, Long userId) {
        return new EntityEvent(EventType.UNPUBLISH, Phase.BEFORE, contentType, documentId, locale, null, userId, null);
    }

    /** Creates an AFTER UNPUBLISH event. */
    public static EntityEvent afterUnpublish(String contentType, String documentId, String locale, Long userId) {
        return new EntityEvent(EventType.UNPUBLISH, Phase.AFTER, contentType, documentId, locale, null, userId, null);
    }

    /** Creates a BEFORE FIND_ONE event. */
    public static EntityEvent beforeFindOne(String contentType, String documentId, String locale) {
        return new EntityEvent(EventType.FIND_ONE, Phase.BEFORE, contentType, documentId, locale, null, null, null);
    }

    /** Creates an AFTER FIND_ONE event. */
    public static EntityEvent afterFindOne(String contentType, String documentId, Map<String, Object> data, String locale, CmsEntry entry) {
        return new EntityEvent(EventType.FIND_ONE, Phase.AFTER, contentType, documentId, locale, data, null, entry);
    }

    /** Creates a BEFORE FIND_MANY event. */
    public static EntityEvent beforeFindMany(String contentType) {
        return new EntityEvent(EventType.FIND_MANY, Phase.BEFORE, contentType, null, null, null, null, null);
    }

    /** Creates an AFTER FIND_MANY event. */
    public static EntityEvent afterFindMany(String contentType) {
        return new EntityEvent(EventType.FIND_MANY, Phase.AFTER, contentType, null, null, null, null, null);
    }

    // ---- Accessors ----

    public EventType getEventType() { return eventType; }
    public Phase getPhase() { return phase; }
    public String getContentType() { return contentType; }
    public String getDocumentId() { return documentId; }
    public String getLocale() { return locale; }
    public Map<String, Object> getData() { return data; }
    public Long getUserId() { return userId; }
    public CmsEntry getEntry() { return entry; }
    public Instant getTimestamp() { return timestamp; }

    /** Returns the event key string (e.g. "entry.create.before"). */
    public String toEventKey() {
        return "entry." + eventType.name().toLowerCase() + "." + phase.name().toLowerCase();
    }

    @Override
    public String toString() {
        return "EntityEvent{" +
                "eventType=" + eventType +
                ", phase=" + phase +
                ", contentType='" + contentType + '\'' +
                ", documentId='" + documentId + '\'' +
                ", locale='" + locale + '\'' +
                '}';
    }
}
