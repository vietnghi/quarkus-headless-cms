package com.quarkus.cms.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.All;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

import java.util.List;

/**
 * Provides a properly-configured {@link JacksonJsonFormatMapper} using the
 * Quarkus-managed {@link ObjectMapper}, ensuring Hibernate's JSONB columns
 * have access to Jackson during application startup.
 * <p>
 * Without this producer, Hibernate's default JacksonJsonFormatMapper may be
 * created with a null ObjectMapper when CDI beans aren't fully available yet
 * (e.g. during startup event observers).
 */
@ApplicationScoped
public class JacksonConfig {

    @Produces
    @Singleton
    @SuppressWarnings("unused")
    JacksonJsonFormatMapper jacksonJsonFormatMapper(ObjectMapper objectMapper) {
        return new JacksonJsonFormatMapper(objectMapper);
    }
}
