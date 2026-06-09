# Quarkus Headless CMS

A high-performance, lightweight headless CMS built on the Quarkus reactive Java framework. Inspired by the architectural concepts of Strapi v5, this system implements a flexible, dynamic content-modeling platform with a sub-second startup footprint, ultra-low memory utilization under load (<150MB), and seamless GraalVM native compilation support.

```
                      +------------------------------------------+
                      |         HTTP Clients / Admin UI          |
                      +----+--------------------------------+----+
                           | (REST APIs)                    | (SSR Qute + HTMX)
                           v                                v
              +------------+------------+     +-------------+------------+
              |    RESTEasy Reactive     |     |    SSR Qute Template     |
              |      REST Endpoints      |     |   Engine + HTMX / Tailwind|
              +------------+------------+     +-------------+------------+
                           |                                |
                           +---------------+----------------+
                                           |
                                           v
                   +-----------------------+-----------------------+
                   |         Quarkus Security (JWT / RBAC)         |
                   |       Custom SecurityIdentityAugmentor        |
                   +-----------------------+-----------------------+
                                           |
                                           v
                   +-----------------------+-----------------------+
                   |         Dynamic Document Service              |
                   |  (Schema Registry, Validation, Query Builder) |
                   +-----------------------+-----------------------+
                                           |
                                           v
                   +-----------------------+-----------------------+
                   |          Hibernate ORM with Panache           |
                   +-----------------------+-----------------------+
                                           |
                                           v
             +-----------------------------+-----------------------------+
             |               PostgreSQL / CockroachDB Database           |
             |  +--------------------------+  +------------------------+ |
             |  |   cms_entries (JSONB)    |  |     cms_relations      | |
             |  |   Core fields + JSON payload|  |     Adjacency links    | |
             |  +--------------------------+  +------------------------+ |
             |  |       cms_assets         |  |   cms_sys_permissions  | |
             |  |     Media registry       |  |     Auth & RBAC data   | |
             |  +--------------------------+  +------------------------+ |
             +-----------------------------------------------------------+
```

---

## Key Features

- **Hybrid Document-on-RDBMS Schema**: Dynamic content-type definitions are compiled to a generic `JSONB` data column inside a standard static database table (`cms_entries`). This allows instant schema modifications in production with zero database schema migration overhead, retaining full GraalVM native compilation support.
- **Dynamic Document Service & Query Engine**: Standard REST and GraphQL dynamic content manipulation including draft/publish mechanics, internationalization (i18n), and a query compiler that translates complex nested Left-Hand Side (LHS) bracket query parameters into efficient PostgreSQL JSONB SQL filters.
- **SSR Administration Dashboard**: A server-side rendered, low-overhead admin portal built on **Quarkus Qute**, **HTMX**, and **Tailwind CSS**. It provides a beautiful interface that updates dynamically without client-side Javascript frameworks or single-page-app bloat.
  - **Content Manager** — Create, edit, publish/unpublish, and manage localized content entries.
  - **Content-Type Builder** — Define and register dynamic content schemas via the admin UI.
  - **Webhooks** — Configure, test, and monitor webhook delivery logs at `/admin/webhooks-ui`.
  - **Users & Roles** — Full CRUD admin for users and roles with permission matrix at `/admin/users-ui` and `/admin/roles-ui`.
  - **Review Workflows** — Build approval workflows with stages, color coding, and review actions at `/admin/review-workflows`.
- **Identity & Access Management (IAM)**: Dual-layer security covering stateful, cookie-based JWT sessions for administrative users, and header-based bearer access for clients using database-backed API tokens with dynamic, fine-grained RBAC permissions.
- **Media Library & Asset Management**: Dynamic multi-part asset upload manager, supporting local disk or AWS S3 / Cloudflare R2 storage, featuring file-type verification via **Apache Tika** and thumbnail optimization via **Thumbnailator**.
- **Reliable Non-Blocking Webhooks**: Events (create, update, delete, publish) are placed on the Vert.x Event Bus and dispatched asynchronously using non-blocking HTTP Clients, featuring HMAC-SHA256 signatures, retry configurations, and delivery auditing.
- **Modular Plugin Architecture**: Extensions are packaged in independent sub-modules and discovered dynamically at startup via Java's `ServiceLoader` SPI mechanism, allowing field types and sidebar elements to be augmented cleanly.

---

## Directory & Sub-Module Layout

| Module Name | Path | Description |
| :--- | :--- | :--- |
| `runtime` | `runtime/` | Core Quarkus extension runtime components, recorders, and configs. |
| `deployment` | `deployment/` | Build-time extension bytecode processing and validation. |
| `cms-core` | `cms-core/` | In-memory schema parsing, validation, and JSONB persistence entities. |
| `cms-rest-api` | `cms-rest-api/` | Standard API routes, dynamic query compiler, and controller logic. |
| `cms-admin-api` | `cms-admin-api/` | JAX-RS routes servicing the administrative console actions. |
| `cms-admin-ui` | `cms-admin-ui/` | Qute HTML templates, Tailwind configurations, and HTMX flows. |
| `cms-auth` | `cms-auth/` | Users, roles, JWT generation, and API Token authorization. |
| `cms-media` | `cms-media/` | File uploads, mime-type validation, and storage providers (Local/S3/R2). |
| `cms-i18n` | `cms-i18n/` | Locale resolution and multi-locale entity mapping services. |
| `cms-graphql` | `cms-graphql/` | Dynamic runtime GraphQL schema generation. |
| `cms-webhooks` | `cms-webhooks/` | Event dispatching engine triggering user-defined HTTP callbacks. |
| `cms-plugin` | `cms-plugin/` | Service SPI and class loaders to register extensions and hook events. |
| `example` | `example/` | Sample application demonstrating usage of the CMS extension. |
| `integration-tests`| `integration-tests/` | Comprehensive test suite checking DB migrations, REST, and UI endpoints. |

---

## 5-Minute Quick Start Guide

Follow these simple steps to spin up the CMS and create your first article locally.

### Step 1: Spin up Postgres
Create a local database container using Docker:
```bash
docker run --name quarkus-cms-db \
  -e POSTGRES_USER=cms_user \
  -e POSTGRES_PASSWORD=*** \
  -e POSTGRES_DB=quarkus_cms \
  -p 5432:5432 -d postgres:15
```

### Step 2: Configure Properties
Add database credentials and storage configuration to `example/src/main/resources/application.properties`:
```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=cms_user
quarkus.datasource.password=cms_pass
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/quarkus_cms

# Automatic flyway migrations
quarkus.flyway.migrate-at-start=true

# Media upload storage provider
cms.media.storage.provider=local
cms.media.storage.local.path=/var/lib/quarkus-cms/uploads
```

### Step 3: Build the Project
Compile the parent pom and all sub-modules using Maven:
```bash
mvn clean install
```

### Step 4: Run the Application
Launch the CMS in development mode with hot-reload support:
```bash
mvn -pl example quarkus:dev
```

Once running, access the services:
- **Admin Panel UI**: [http://localhost:8080/admin/content-manager](http://localhost:8080/admin/content-manager)
  - Content Manager: [`/admin/content-manager`](http://localhost:8080/admin/content-manager)
  - Content-Type Builder: [`/admin/content-types`](http://localhost:8080/admin/content-types)
  - Webhooks: [`/admin/webhooks-ui`](http://localhost:8080/admin/webhooks-ui)
  - Users: [`/admin/users-ui`](http://localhost:8080/admin/users-ui)
  - Roles: [`/admin/roles-ui`](http://localhost:8080/admin/roles-ui)
  - Review Workflows: [`/admin/review-workflows`](http://localhost:8080/admin/review-workflows)
- **REST APIs**: [http://localhost:8080/api](http://localhost:8080/api)
- **GraphQL & GraphiQL Playground**: [http://localhost:8080/q/graphql](http://localhost:8080/q/graphql)

---

## Comprehensive Configuration Reference (`application.properties`)

The table below lists all key configuration parameters supported by Quarkus Headless CMS:

| Property Option | Type | Default | Description |
|---|---|---|---|
| **`quarkus.datasource.db-kind`** | String | `postgresql` | Database type (e.g., `postgresql`, `cockroachdb`). |
| **`quarkus.datasource.jdbc.url`** | String | None | JDBC URL connection string. |
| **`quarkus.flyway.migrate-at-start`**| Boolean| `true` | Runs Flyway migration scripts on application startup. |
| **`cms.media.storage.provider`** | String | `local` | Upload storage provider: `local`, `s3`, or `r2`. |
| **`cms.media.storage.local.path`** | String | `/tmp/uploads` | Local directory path to save assets (for `local`). |
| **`cms.media.storage.s3.bucket`** | String | None | S3 bucket name (for `s3` or `r2`). |
| **`cms.media.storage.s3.region`** | String | `us-east-1` | S3 region code (for `s3`). |
| **`cms.media.storage.s3.endpoint-override`**| String| None | Endpoint URL (required for custom R2 integrations). |
| **`smallrye.jwt.sign.key.location`**| String | `privateKey.pem`| Absolute path or classpath location of private key. |
| **`smallrye.jwt.encrypt.key.location`**| String| `publicKey.pem` | Absolute path or classpath location of public key. |
| **`cms.i18n.default-locale`** | String | `en` | Fallback language code for dynamic requests. |
| **`cms.webhooks.dispatcher.max-retries`**| Integer| `5` | Retry attempts for failed webhook dispatch requests. |
| **`cms.webhooks.dispatcher.timeout-ms`**| Long | `3000` | Webhook HTTP timeout threshold. |
| **`mp.graphql.query.complexity`** | Integer | `100` | GraphQL maximum complexity threshold. |
| **`mp.graphql.query.depth`** | Integer | `10` | GraphQL maximum request depth threshold. |

---

## Native Compilation

To compile the entire headless CMS into an ultra-low footprint, zero-dependency, and high-performance native executable:

```bash
mvn clean package -Pnative -DskipTests
```

This generates a standalone native binary in `example/target/example-1.0.0-SNAPSHOT-runner` which can be run instantly:

```bash
./example/target/example-1.0.0-SNAPSHOT-runner
```

---

## Technical Documentation Guides

For in-depth explanations, configuration references, and tutorials:

- [User Guide](docs/USER_GUIDE.md) — Create schemas, CRUD operations, draft-publish lifecycle, multi-locale, and upload configuration.
- [Architecture & Design](docs/ARCHITECTURE.md) — Deep dive into the database models, GIN indexing, LHS query parser, and request execution flows.
- [Developer Guide](DEVELOPER_GUIDE.md) — Learn how to build locally, write synchronous/asynchronous lifecycle hooks, and build plugins.
- [Strapi v5 Migration Guide](docs/MIGRATION_GUIDE.md) — Step-by-step instructions for porting content, database structures, and Javascript plugins to Quarkus Java.
- [API Documentation Reference](API_DOCUMENTATION.md) — Reference for all REST, GraphQL, and Authentication endpoints.
