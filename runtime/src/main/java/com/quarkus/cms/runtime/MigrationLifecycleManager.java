package com.quarkus.cms.runtime;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Migration lifecycle manager that provides programmatic access to Flyway migration state,
 * schema version tracking, and migration lifecycle events.
 *
 * <p>This service bridges Flyway's migration engine with the CMS application by exposing
 * migration status information for admin dashboards, monitoring, and automated management.
 * It fires CDI events at migration lifecycle boundaries so observers can react to schema
 * changes.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Inject
 * MigrationLifecycleManager migrationManager;
 *
 * // Check pending migrations
 * List<MigrationVersion> pending = migrationManager.getPendingMigrations();
 *
 * // Get current schema version
 * Optional<MigrationVersion> current = migrationManager.getCurrentVersion();
 * }</pre>
 *
 * <h3>Migration lifecycle events</h3>
 * <p>This service fires {@link MigrationLifecycleEvent} on the CDI event bus after each
 * migration batch completes. Observers can filter on the event:
 * <pre>{@code
 * void onMigrationComplete(@Observes MigrationLifecycleEvent event) {
 *     event.getAppliedMigrations().forEach(m -> Log.infof("Applied: %s", m));
 * }
 * }</pre>
 */
@ApplicationScoped
@Startup
public class MigrationLifecycleManager {

    @Inject
    Instance<Flyway> flywayInstance;

    @Inject
    Event<MigrationLifecycleEvent> lifecycleEvent;

    private volatile boolean initialized = false;

    @PostConstruct
    void init() {
        try {
            if (flywayInstance.isResolvable()) {
                Flyway flyway = flywayInstance.get();
                MigrationInfoService info = flyway.info();
                Log.infof("MigrationLifecycleManager initialized. Schema version: %s",
                    info.current() != null ? info.current().getVersion() : "not available");
                initialized = true;
            } else {
                Log.info("MigrationLifecycleManager: Flyway not configured, skipping.");
            }
        } catch (Exception e) {
            Log.warnf("MigrationLifecycleManager: Flyway not available or not configured: %s",
                e.getMessage());
        }
    }

    // ---- Migration querying ---- //

    /**
     * Returns the currently applied schema version, if any.
     */
    public Optional<MigrationVersion> getCurrentVersion() {
        if (!initialized) return Optional.empty();
        try {
            Flyway fw = flyway();
            if (fw == null) return Optional.empty();
            MigrationInfo current = fw.info().current();
            if (current == null) return Optional.empty();
            return Optional.of(toVersion(current));
        } catch (Exception e) {
            Log.errorf("Failed to get current migration version: %s", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns all applied migrations, newest first.
     */
    public List<MigrationVersion> getAppliedMigrations() {
        if (!initialized) return List.of();
        try {
            Flyway fw = flyway();
            if (fw == null) return List.of();
            MigrationInfo[] all = fw.info().all();
            List<MigrationVersion> result = new ArrayList<>();
            for (MigrationInfo info : all) {
                if (info.getState() == MigrationState.SUCCESS
                    || info.getState() == MigrationState.OUTDATED
                    || info.getState() == MigrationState.FUTURE_SUCCESS) {
                    result.add(toVersion(info));
                }
            }
            result.sort(Comparator.comparing(MigrationVersion::version).reversed());
            return result;
        } catch (Exception e) {
            Log.errorf("Failed to get applied migrations: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns migrations that have not yet been applied, ordered by version.
     */
    public List<MigrationVersion> getPendingMigrations() {
        if (!initialized) return List.of();
        try {
            Flyway fw = flyway();
            if (fw == null) return List.of();
            MigrationInfo[] pending = fw.info().pending();
            List<MigrationVersion> result = new ArrayList<>();
            for (MigrationInfo info : pending) {
                result.add(toVersion(info));
            }
            return result;
        } catch (Exception e) {
            Log.errorf("Failed to get pending migrations: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns the full migration history — all known migrations and their states.
     */
    public List<MigrationInfo> getFullMigrationInfo() {
        if (!initialized) return List.of();
        try {
            Flyway fw = flyway();
            if (fw == null) return List.of();
            return List.of(fw.info().all());
        } catch (Exception e) {
            Log.errorf("Failed to get migration info: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns whether all known migrations have been applied.
     */
    public boolean isSchemaUpToDate() {
        if (!initialized) return true;
        try {
            Flyway fw = flyway();
            if (fw == null) return true;
            return fw.info().pending().length == 0;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Programmatically triggers Flyway migration. Fires a {@link MigrationLifecycleEvent}
     * after completion. This is primarily useful in environments where {@code migrate-at-start}
     * is disabled (e.g., dev mode) but explicit migration is still desired.
     *
     * @return the migration result, or empty if Flyway is unavailable
     */
    public Optional<MigrateResult> migrate() {
        if (!initialized) return Optional.empty();
        Flyway fw = flyway();
        if (fw == null) return Optional.empty();
        try {
            MigrateResult result = fw.migrate();
            Log.infof("Flyway migration executed: %d migrations applied",
                result.migrationsExecuted);
            lifecycleEvent.fire(new MigrationLifecycleEvent(
                toVersions(result.migrations.stream()
                    .map(m -> new MigrationVersion(
                        m.version,
                        m.description,
                        m.executionTime,
                        "SUCCESS",
                        Instant.now()))
                    .toList())));
            return Optional.of(result);
        } catch (Exception e) {
            Log.errorf("Flyway migration failed: %s", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the version number of the current schema as a human-readable string.
     */
    public String getSchemaVersionString() {
        return getCurrentVersion()
            .map(v -> v.version())
            .orElse("not initialized");
    }

    // ---- Internal helpers ---- //

    private Flyway flyway() {
        return flywayInstance.isResolvable() ? flywayInstance.get() : null;
    }

    private MigrationVersion toVersion(MigrationInfo info) {
        return new MigrationVersion(
            info.getVersion() != null ? info.getVersion().toString() : "undescribed",
            info.getDescription(),
            info.getInstalledOn() != null
                ? java.time.Duration.between(info.getInstalledOn().toInstant(), Instant.now())
                    .abs().getSeconds()
                : 0L,
            info.getState().getDisplayName(),
            info.getInstalledOn() != null
                ? info.getInstalledOn().toInstant()
                : Instant.EPOCH);
    }

    private List<MigrationVersion> toVersions(List<MigrationVersion> versions) {
        return versions != null ? versions : List.of();
    }

    // ---- Value types ---- //

    /**
     * Lightweight representation of a migration version, suitable for serialization
     * in admin API responses and dashboards.
     */
    public record MigrationVersion(
        String version,
        String description,
        long executionTimeMs,
        String state,
        Instant installedOn
    ) {}

    /**
     * CDI event fired after a migration batch completes. Observers can introspect
     * the applied migrations and react accordingly.
     */
    public record MigrationLifecycleEvent(
        List<MigrationVersion> appliedMigrations
    ) {}
}
