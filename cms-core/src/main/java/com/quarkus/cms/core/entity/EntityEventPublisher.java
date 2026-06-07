package com.quarkus.cms.core.entity;

import com.quarkus.cms.core.domain.CmsEntry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * CDI publisher for {@link EntityEvent}s.
 *
 * <p>Service methods at the entity CRUD boundary call the appropriate fire method before and after
 * each operation. Any {@code @Observes EntityEvent} observer in the application picks them up —
 * including lifecycle hook interceptors, audit loggers, cache invalidators, and plugin
 * extensions.
 *
 * <p>This is the entity-level hook that runs in-process (synchronous or async via
 * {@code @ObservesAsync}) and is separate from the webhook dispatch system which targets
 * external consumers.
 */
@ApplicationScoped
public class EntityEventPublisher {

    private final Event<EntityEvent> cdiEvent;

    @Inject
    public EntityEventPublisher(Event<EntityEvent> cdiEvent) {
        this.cdiEvent = cdiEvent;
    }

    // ---- Create ----

    /** Fires a BEFORE CREATE event. */
    public void fireBeforeCreate(String contentType, Map<String, Object> data, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.beforeCreate(contentType, data, locale, userId));
    }

    /** Fires an AFTER CREATE event. */
    public void fireAfterCreate(String contentType, CmsEntry entry, Map<String, Object> data, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.afterCreate(contentType, entry, data, locale, userId));
    }

    // ---- Update ----

    /** Fires a BEFORE UPDATE event. */
    public void fireBeforeUpdate(String contentType, String documentId, Map<String, Object> data, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.beforeUpdate(contentType, documentId, data, locale, userId));
    }

    /** Fires an AFTER UPDATE event. */
    public void fireAfterUpdate(String contentType, CmsEntry entry, Map<String, Object> data, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.afterUpdate(contentType, entry, data, locale, userId));
    }

    // ---- Delete ----

    /** Fires a BEFORE DELETE event. */
    public void fireBeforeDelete(String contentType, String documentId, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.beforeDelete(contentType, documentId, locale, userId));
    }

    /** Fires an AFTER DELETE event. */
    public void fireAfterDelete(String contentType, String documentId, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.afterDelete(contentType, documentId, locale, userId));
    }

    // ---- Publish ----

    /** Fires a BEFORE PUBLISH event. */
    public void fireBeforePublish(String contentType, String documentId, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.beforePublish(contentType, documentId, locale, userId));
    }

    /** Fires an AFTER PUBLISH event. */
    public void fireAfterPublish(String contentType, CmsEntry entry, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.afterPublish(contentType, entry, locale, userId));
    }

    // ---- Unpublish ----

    /** Fires a BEFORE UNPUBLISH event. */
    public void fireBeforeUnpublish(String contentType, String documentId, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.beforeUnpublish(contentType, documentId, locale, userId));
    }

    /** Fires an AFTER UNPUBLISH event. */
    public void fireAfterUnpublish(String contentType, String documentId, String locale, Long userId) {
        cdiEvent.fire(EntityEvent.afterUnpublish(contentType, documentId, locale, userId));
    }

    // ---- Find ----

    /** Fires a BEFORE FIND_ONE event. */
    public void fireBeforeFindOne(String contentType, String documentId, String locale) {
        cdiEvent.fire(EntityEvent.beforeFindOne(contentType, documentId, locale));
    }

    /** Fires an AFTER FIND_ONE event. */
    public void fireAfterFindOne(String contentType, String documentId, Map<String, Object> data, String locale, CmsEntry entry) {
        cdiEvent.fire(EntityEvent.afterFindOne(contentType, documentId, data, locale, entry));
    }

    /** Fires a BEFORE FIND_MANY event. */
    public void fireBeforeFindMany(String contentType) {
        cdiEvent.fire(EntityEvent.beforeFindMany(contentType));
    }

    /** Fires an AFTER FIND_MANY event. */
    public void fireAfterFindMany(String contentType) {
        cdiEvent.fire(EntityEvent.afterFindMany(contentType));
    }
}
