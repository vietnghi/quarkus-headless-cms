# Migration System & Schema Management

This document describes the database migration system for the Quarkus Headless CMS.

---

## Overview

The CMS uses [Flyway](https://flywaydb.org/) for database schema migrations, integrated via
[Quarkus Flyway](https://quarkus.io/guides/flyway). Migration scripts are versioned SQL files
stored in the `runtime` module's classpath at `db/migration/`. They are automatically available
to any application that depends on the CMS extension.

## Migration Lifecycle

### File naming convention

Migration files follow Flyway's conventional naming:

```
V<version>__<description>.sql
```

- `V` — versioned migration prefix (required)
- `<version>` — numeric version (1, 2, 3...)
- `__` — double underscore separator (required)
- `<description>` — snake_case description of the change

Examples:
- `V1__init_cms_core.sql`
- `V2__webhook_extra_columns_and_deliveries.sql`
- `V3__supporting_entities.sql`
- `V4__seed_data.sql`

### Ordering

Migrations are applied in version order. Never modify an applied migration — create a new one.
Flyway tracks which migrations have been applied via the `flyway_schema_history` table and
will reject any migration whose checksum differs from the applied version.

### Current migration inventory

| Migration | Description |
|-----------|-------------|
| V1 | Core tables: entries, relations, schema registry, users, roles, permissions, tokens, webhooks |
| V2 | Webhook extra columns (secret, max_retries, timeout_ms), webhook deliveries table |
| V3 | Supporting entities: media files/folders, audit log, locales, custom fields, reviews; unique constraint on `core_schema.uid` |
| V4 | Seed data: default roles, super admin user, default locale, and starter permissions |

## Configuration

### Production (PostgreSQL)

In `application.properties`:

```properties
# Main datasource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/cms_db

# Disable Hibernate auto-DDL — Flyway manages the schema
quarkus.hibernate-orm.database.generation=none

# Flyway settings
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=db/migration
quarkus.flyway.baseline-on-migrate=true
```

### Development

In dev mode, use H2 with Hibernate's `drop-and-create` for rapid iteration:

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:cms;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.flyway.migrate-at-start=false
```

### Testing

Integration tests also use H2 with `drop-and-create` and Flyway disabled:

```properties
quarkus.flyway.migrate-at-start=false
quarkus.hibernate-orm.database.generation=drop-and-create
```

## SQL Server Compatibility

The primary target is PostgreSQL (with JSONB for dynamic fields). For SQL Server support:

| PostgreSQL | SQL Server equivalent |
|------------|----------------------|
| `BIGSERIAL` | `BIGINT IDENTITY(1,1)` |
| `JSONB` | `NVARCHAR(MAX)` with `JSON` constraint |
| `TIMESTAMP WITH TIME ZONE` | `DATETIME2` |
| `BOOLEAN` | `BIT` |
| `TEXT` | `NVARCHAR(MAX)` |
| `gen_random_uuid()` | `NEWID()` |
| `CURRENT_TIMESTAMP` | `GETUTCDATE()` |
| `unnest(ARRAY[...])` | `VALUES (...), (...)` |

To add SQL Server-specific migrations, use Flyway's vendor-detection naming:
```
V3__supporting_entities__mssql.sql
```

## Programmatic Migration Management

The `MigrationLifecycleManager` CDI bean provides runtime access to migration state and
lifecycle events.

### Checking schema version

```java
@Inject
MigrationLifecycleManager migrationManager;

Optional<MigrationVersion> current = migrationManager.getCurrentVersion();
current.ifPresent(v -> Log.infof("Schema at version %s", v.version()));
```

### Listing pending migrations

```java
List<MigrationVersion> pending = migrationManager.getPendingMigrations();
```

### Programmatic migration

```java
migrationManager.migrate(); // triggers flyway.migrate()
```

### Observing migration lifecycle events

```java
void onMigrationComplete(@Observes MigrationLifecycleManager.MigrationLifecycleEvent event) {
    event.appliedMigrations().forEach(m ->
        Log.infof("Applied migration %s: %s", m.version(), m.description()));
}
```

### Admin API endpoint

The migration status is exposed via the admin API at:

```
GET /admin/migrations/status
```

Returns JSON with current version, pending count, and full migration history.

## Writing New Migrations

1. Determine the next version number by looking at the last applied migration.
2. Create `V<next>__<description>.sql` in `runtime/src/main/resources/db/migration/`.
3. Write the SQL — use `IF NOT EXISTS` for tables, `IF NOT EXISTS` pattern from `pg_constraint`
   for constraints. This makes migrations idempotent.
4. For seed data, use `INSERT ... WHERE NOT EXISTS` to ensure idempotency.
5. Verify the migration is valid by building the project and checking that Flyway
   parses the file correctly.
6. Do NOT modify an applied migration — create a new one. Flyway checksums
   prevent tampering.

### Idempotency pattern for tables

```sql
CREATE TABLE IF NOT EXISTS cms_example (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
```

### Idempotency pattern for seed data

```sql
INSERT INTO cms_example (name)
SELECT 'Default Name'
WHERE NOT EXISTS (SELECT 1 FROM cms_example WHERE name = 'Default Name');
```

### Idempotency pattern for constraints

```sql
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_example_name'
          AND conrelid = 'cms_example'::regclass
    ) THEN
        ALTER TABLE cms_example ADD CONSTRAINT uq_example_name UNIQUE (name);
    END IF;
END $$;
```

## Dynamic Content-Type Schema Versioning

In addition to Flyway's schema-level versioning, each content-type and component definition
stored in the `core_schema` table has its own version history managed by
`SchemaStorageService`. This is independent of Flyway and reflects the application-level
schema lifecycle (content editors adding fields, creating components).

The `SchemaStorageService.registerContentType()` method:
1. Backs up the current definition
2. Increments the version number
3. Records the change in `version_history` JSONB column
4. Updates the cache

Rollback is available via `SchemaStorageService.rollbackContentType()`.

## Troubleshooting

### "Flyway failed to apply migration" — checksum mismatch

You modified an already-applied migration. Fix: create a new migration with the corrected
logic, or reset the database (dev only).

### "Relation already exists"

A migration was partially applied. Flyway's `flyway_schema_history` table may have an entry
for a migration that was not fully applied. Use `flyway repair` or manually clean up.

### Flyway disabled in tests

This is by design. Tests use Hibernate's `drop-and-create` for speed. To enable Flyway in
a specific test, override `quarkus.flyway.migrate-at-start=true` in the test profile.
