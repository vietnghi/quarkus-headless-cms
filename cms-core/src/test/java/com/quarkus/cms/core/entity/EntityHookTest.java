package com.quarkus.cms.core.entity;

import jakarta.inject.Qualifier;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EntityHook} — the CDI qualifier annotation for entity lifecycle events.
 */
class EntityHookTest {

    @Test
    void shouldBeAnnotatedWithQualifier() {
        assertTrue(EntityHook.class.isAnnotationPresent(Qualifier.class),
                "EntityHook must be annotated with @Qualifier to be a valid CDI qualifier");
    }

    @Test
    void shouldHavePhaseAttribute() throws NoSuchMethodException {
        Object defaultValue = EntityHook.class
                .getDeclaredMethod("phase")
                .getDefaultValue();
        assertNull(defaultValue, "phase() attribute should not have a default value — it must be specified by the user");
    }

    @Test
    void shouldAcceptBeforePhase() {
        // Create a synthetic qualifier for BEFORE phase
        EntityHook hook = new EntityHook() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return EntityHook.class;
            }

            @Override
            public EntityEvent.Phase phase() {
                return EntityEvent.Phase.BEFORE;
            }
        };
        assertEquals(EntityEvent.Phase.BEFORE, hook.phase());
    }

    @Test
    void shouldAcceptAfterPhase() {
        // Create a synthetic qualifier for AFTER phase
        EntityHook hook = new EntityHook() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return EntityHook.class;
            }

            @Override
            public EntityEvent.Phase phase() {
                return EntityEvent.Phase.AFTER;
            }
        };
        assertEquals(EntityEvent.Phase.AFTER, hook.phase());
    }

    @Test
    void shouldBeRuntimeRetained() {
        assertTrue(EntityHook.class
                .getAnnotation(java.lang.annotation.Retention.class)
                .value() == java.lang.annotation.RetentionPolicy.RUNTIME,
                "EntityHook must have RUNTIME retention for CDI to process it");
    }

    @Test
    void shouldTargetMethodAndParameter() {
        java.lang.annotation.Target target = EntityHook.class
                .getAnnotation(java.lang.annotation.Target.class);
        assertNotNull(target, "EntityHook must have @Target");
        boolean hasMethod = false;
        boolean hasParameter = false;
        for (java.lang.annotation.ElementType et : target.value()) {
            if (et == java.lang.annotation.ElementType.METHOD) hasMethod = true;
            if (et == java.lang.annotation.ElementType.PARAMETER) hasParameter = true;
        }
        assertTrue(hasMethod, "EntityHook must target METHOD");
        assertTrue(hasParameter, "EntityHook must target PARAMETER");
    }
}
