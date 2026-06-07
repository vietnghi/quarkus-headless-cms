# Quarkus Headless CMS - Developer & Architecture Guide

Welcome to the Developer and Architecture Guide. This document provides an in-depth technical analysis of the design patterns, database strategies, security architectures, extension mechanics, and contribution guidelines for the Quarkus Headless CMS.

---

## 1. Architectural Overview

Traditional CMS platforms written in Java often suffer from rigid structures. Since Java is a statically typed language, adding custom fields or changing models dynamically usually requires compiling bytecode, loading new `.class` files dynamically, and rebuilding Hibernate session factories at runtime. This process is complex, prone to memory leaks, slows down startup times, and is incompatible with Quarkus GraalVM Native compilation.

To solve this problem while preserving sub-second cold starts and native compilation, Quarkus Headless CMS implements a **Hybrid Document-on-RDBMS Schema Design** inspired by modern database design patterns (e.g., Document-Store in Postgres). Standard, well-defined metadata and indexing fields are stored as static SQL columns, while custom, dynamic fields are serialized and queried inside a PostgreSQL `JSONB` column.

```
                  +-----------------------------------+
                  |        Administrative API         |
                  +-----------------+-----------------+
                                    |
                                    v
+------------------+     +----------+----------+     +-------------------+
||  Schema Registry |<--->|  CmsDocumentService |<--->|   Query Compiler  |
||  (Loads schemas  |     |   (Validation,      |     |  (Parses URL args |
||  from JSON)      |     |   CRUD pipelines)   |     |   into JSONB SQL) |
+------------------+     +----------+----------+     +-------------------+
                                    |
                                    v
                         +----------+----------+
                         |  Hibernate ORM      |
                         |  with Panache       |
                         +----------+----------+
                                    |
                                    v
                         +----------+----------+
                         |      PostgreSQL     |
                         |  (cms_entries, etc.)|
                         +---------------------+
```

---

## 2. Database Schema Design & Hybrid Storage

Database schemas are managed using static, declarative migrations via **Flyway**. 

### 2.1 Core Schema Table Inventory
1. **`cms_entries`**: Holds both draft and published states for content.
   - `id` (BIGINT): The absolute unique auto-increment identifier for a single entry version.
   - `document_id` (VARCHAR): A logical stable identifier shared across multiple localized versions or draft/published pairs of a single conceptual document (e.g., `art_8a9c2x4d`).
   - `locale` (VARCHAR): Language indicator (e.g., `en`, `fr`, `de`).
   - `status` (VARCHAR): Draft/publish indicator (`draft` or `published`).
   - `data` (JSONB): The hybrid payload containing all dynamic content-type attributes defined in the schema.
2. **`cms_relations`**: Serves as a dynamic adjacency-list table storing relationships between documents. This design allows many-to-many, one-to-many, and polymorphic links without modifying the database DDL at runtime.
   - `source_document_id`: The originating document's ID.
   - `source_type`: Originating content type (e.g., `api::article.article`).
   - `target_document_id`: The related document's ID.
   - `target_type`: Related content type (e.g., `api::category.category`).
   - `field_name`: The attribute name defining the relationship.
3. **`cms_assets`**: Metadata registry for media uploads.
4. **`cms_api_tokens`**: Access policies and credentials for programmatic API usage.

---

### 2.2 Hibernate Panache Entity Mapping
Custom Java entities are annotated with Jakarta Persistence. In `CmsEntry.java`, the dynamic `data` column is mapped using Hibernate's dynamic JSON capability:

```java
package com.quarkus.cms.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "cms_entries")
public class CmsEntry extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "document_id", nullable = false, length = 36)
    public String documentId;

    @Column(name = "content_type", nullable = false, length = 100)
    public String contentType;

    @Column(name = "locale", nullable = false, length = 10)
    public String locale = "en";

    @Column(name = "status", nullable = false, length = 15)
    public String status = "draft"; // "draft" or "published"

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    public Map<String, Object> data;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();
    
    @Column(name = "published_at")
    public Instant publishedAt;
}
```

---

## 3. Dynamic Schema Parsing & Validation

### 3.1 Content-Type JSON Schemas
Schemas are written as JSON files and stored under `/schemas/` inside the application project (e.g., `/schemas/article.json`).

```json
{
  "info": {
    "displayName": "Article",
    "singularName": "article",
    "pluralName": "articles"
  },
  "attributes": {
    "title": {
      "type": "string",
      "required": true,
      "maxLength": 150
    },
    "slug": {
      "type": "string",
      "required": true,
      "unique": true
    },
    "content": {
      "type": "richtext"
    },
    "views": {
      "type": "integer",
      "default": 0
    }
  }
}
```

### 3.2 Loading and Validation
1. **`CmsSchemaRegistry.java`**: Scans the `/schemas` directory at application startup, parses definitions using Jackson Databind, and caches them in memory.
2. **`SchemaValidator.java`**: Integrates with the networknt JSON Schema Validator. Before any dynamic entry is written to the database (on POST or PUT), the incoming payload is checked against the registered content schema. Malformed fields or failed validation constraints trigger a `SchemaValidationException` which is mapped to a standard `HTTP 400 Bad Request` REST response.

---

## 4. Query Compiler

The `CmsQueryBuilder` translates incoming REST API search query parameters into native SQL queries with PostgreSQL-specific operators.

For instance, when a client makes this API request:
`GET /api/articles?filters[views][$gt]=100&filters[title][$contains]=quarkus&sort=views:desc`

The compiler processes the LHS brackets and generates:

```sql
SELECT * FROM cms_entries 
WHERE content_type = 'api::article.article' 
  AND status = 'published'
  AND (data ->> 'views')::numeric > 100 
  AND data ->> 'title' ILIKE '%quarkus%'
ORDER BY (data ->> 'views')::numeric DESC;
```

For performance efficiency, the compiler leverages GIN indices on the `data` column inside `cms_entries` (supporting rapid search across JSON nested keys) and pre-filters lookup rows by standard relational columns (`content_type`, `status`, `locale`) before applying jsonb operators.

---

## 5. Security Architecture

The CMS enforces dual-layer security policies:

### 5.1 Admin Panel Cookie Auth
Access to administrative views under `/admin/*` requires a stateful secure cookie.
- Admin credentials are authenticated against the database.
- On success, the backend generates a signed JSON Web Token (JWT) via **SmallRye JWT** and attaches it as an `HttpOnly`, `Secure`, and `SameSite=Strict` cookie named `CMS_SESSION`.
- A high-priority `AdminSessionAuthFilter` intercepts administrative routes, extracts and validates the JWT, and sets the request's security context.

### 5.2 Client Bearer Auth & Dynamic RBAC
Access to client APIs (`/api/*`) uses HTTP Bearer tokens.
- **API Token Verification**: Client requests include `Authorization: Bearer <token>` in headers.
- **Dynamic Security Augmenter**: A custom `CmsSecurityIdentityAugmentor` is registered in CDI. Upon client request authentication, it loads the permissions and active rules mapped to that token from the database, dynamically binding them as JAX-RS roles to the active security request identity.
- JAX-RS endpoint classes or methods enforce role-based access control natively via `@RolesAllowed`.

---

## 6. How to Write Lifecycle Hooks

Lifecycle hooks allow developers to run custom logic (validation, auditing, side-effects) at twelve distinct points during content creation and modification.

### 6.1 Sync vs. Async Phases
- **Before Events** (`Phase.BEFORE`): Executed **synchronously** inside the active database transaction. Ideal for field validation, data sanitation, and auto-populating fields. Throwing a runtime exception here automatically aborts the operation and rolls back the transaction.
- **After Events** (`Phase.AFTER`): Executed **asynchronously** after the operation commits to the database. Best for integrations, sending mail notifications, warming CDN caches, or dispatching webhooks.

### 6.2 Writing a Hook Observer using CDI
To create a hook, annotate your observer method with Jakarta CDI's `@Observes` (or `@ObservesAsync` for async behavior) on a `LifecycleEvent` object:

```java
package com.quarkus.cms.hooks;

import com.quarkus.cms.webhooks.event.LifecycleEvent;
import com.quarkus.cms.webhooks.event.LifecycleEvent.EventType;
import com.quarkus.cms.webhooks.event.LifecycleEvent.Phase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.logging.Log;

@ApplicationScoped
public class ContentSanitizerHook {

    /**
     * Automatically sanitizes article titles before they are written to the database.
     */
    public void sanitizeArticleTitle(@Observes LifecycleEvent event) {
        if (event.getEventType() != EventType.CREATE && event.getEventType() != EventType.UPDATE) {
            return;
        }
        if (event.getPhase() != Phase.BEFORE || !"api::article.article".equals(event.getContentType())) {
            return;
        }

        if (event.getData() != null && event.getData().containsKey("title")) {
            String title = (String) event.getData().get("title");
            if (title != null) {
                // Strip HTML tags and excessive spaces
                String sanitized = title.replaceAll("<[^>]+>", "").trim();
                event.getData().put("title", sanitized);
                Log.infof("Hook sanitized title: %s -> %s", title, sanitized);
            }
        }
    }
}
```

### 6.3 Using the `@LifecycleHook` Interceptor
You can use the `@LifecycleHook` qualifier to target specific event operations directly without needing manual type checks:

```java
import com.quarkus.cms.webhooks.interceptor.LifecycleHook;

@ApplicationScoped
public class AuditLogHook {

    @LifecycleHook(eventType = EventType.DELETE, phase = Phase.AFTER)
    public void onEntryDeleted(@Observes LifecycleEvent event) {
        Log.infof("Audit: Document %s has been deleted.", event.getDocumentId());
    }
}
```

---

## 7. How to Create a Plugin

The plugin system allows extending the core CMS dynamically. Each plugin can add fields to existing types, register custom REST endpoints, create settings pages in the admin dashboard, or attach custom hooks.

### 7.1 The `CmsPlugin` SPI Interface
To write a plugin, implement the `com.quarkus.cms.plugin.CmsPlugin` interface:

```java
package com.quarkus.cms.myplugin;

import com.quarkus.cms.plugin.CmsPlugin;
import com.quarkus.cms.plugin.PluginMetadata;
import com.quarkus.cms.plugin.PluginRegistrationContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MyCustomPlugin implements CmsPlugin {

    private static final PluginMetadata METADATA = 
        PluginMetadata.builder("my-custom-plugin", "1.0.0")
            .displayName("My Custom Plugin")
            .description("Brings custom capabilities to Quarkus Headless CMS")
            .author("Developer Name")
            .build();

    @Override
    public PluginMetadata getMetadata() {
        return METADATA;
    }

    @Override
    public void register(PluginRegistrationContext context) {
        // Register custom sidebar pages, controllers, or configurations here
        context.registerAdminPage(
            AdminPageDefinition.builder("my-settings")
                .displayName("My Settings")
                .routePath("/admin/plugins/my-settings")
                .build()
        );
    }

    @Override
    public void unregister() {
        // Handle teardown of plugin assets
    }
}
```

### 7.2 Service Loader Registration
For the plugin manager to discover your extension on startup:
1. Create a file named `META-INF/services/com.quarkus.cms.plugin.CmsPlugin` inside your module's resources.
2. Enter the fully qualified path of your implementation:
   ```text
   com.quarkus.cms.myplugin.MyCustomPlugin
   ```

---

## 8. Extension Guide: Creating a Custom Feature Module

Because Quarkus Headless CMS is a multi-module project, writing custom features as decoupled modules is simple.

### Step 1: Create the Sub-Module pom.xml
Configure your new module directory under the parent project root:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.quarkus.cms</groupId>
        <artifactId>quarkus-headless-cms-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>cms-comments</artifactId>
    <name>Quarkus Headless CMS - Comments Module</name>
</project>
```

### Step 2: Register in Parent POM
Open the root `pom.xml` and append your module name to the `<modules>` array:
```xml
<modules>
    ...
    <module>cms-comments</module>
</modules>
```

---

## 9. How to Build and Run Locally

Follow these instructions to run the CMS on your local workstation.

### Prerequisites
- **Java**: JDK 17 or higher (GraalVM JDK recommended)
- **Maven**: Version 3.8.x or newer
- **Docker**: For running PostgreSQL locally

### Step-by-Step Setup

#### 1. Spin Up PostgreSQL
Run a Postgres container with Docker:
```bash
docker run --name quarkus-cms-db \
  -e POSTGRES_USER=cms_user \
  -e POSTGRES_PASSWORD=cms_pass \
  -e POSTGRES_DB=quarkus_cms \
  -p 5432:5432 -d postgres:15
```

#### 2. Configure Environment Properties
Create or edit the `example/src/main/resources/application.properties` file:
```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=cms_user
quarkus.datasource.password=cms_pass
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/quarkus_cms

# Automatic Flyway Schema Migrations
quarkus.flyway.migrate-at-start=true
```

#### 3. Build and Install Project
Compile all modules and download dependencies:
```bash
mvn clean install
```

#### 4. Run Interactive Development Mode
Launch the application with live hot-reloading:
```bash
mvn -pl example quarkus:dev
```
The application will launch. Access the endpoints:
- **Admin Dashboard**: `http://localhost:8080/admin/`
- **REST Content APIs**: `http://localhost:8080/api/`
- **GraphQL playground**: `http://localhost:8080/q/graphql`

---

## 10. Testing Strategy & Quality Gates

Code contributions must adhere to the standard three-tiered testing structure before merging.

### 10.1 Unit Testing (`@QuarkusTest`)
- Focuses on logical components (e.g., parsing schemas, permission trees).
- Avoid database calls inside unit tests; use Mockito to simulate dependencies.

### 10.2 Integration Testing (`RESTAssured`)
- Runs within the `integration-tests` module.
- Leverages **Quarkus Dev Services** to automatically provision a Postgres database container during testing (requires Docker to be running).
- Exercises full API lifecycle loops (Create -> Read -> Update -> Publish -> Delete) and asserts database tables directly.

To run the integration tests:
```bash
mvn -pl integration-tests verify
```

---

## 11. How to Contribute

We welcome community contributions. Follow this process to submit your code:

1. **Branch Naming**: Match the convention: `feature/your-feature-name` or `bugfix/issue-description`.
2. **Coding Standards**:
   - Format code using standard Java formatting guides.
   - Run a syntax and code style check before committing:
     ```bash
     mvn spotless:apply
     ```
3. **Write Tests**: Every feature or bug fix must include corresponding unit or integration tests with code coverage of at least 80%.
4. **Local Verification**: Ensure the entire project compiles successfully, all migrations pass, and tests execute without errors locally:
   ```bash
   mvn clean install
   ```
5. **Open a Pull Request**: Submit your PR targeting the `main` branch. Provide a comprehensive summary of changes, list affected modules, and include passing test results.
