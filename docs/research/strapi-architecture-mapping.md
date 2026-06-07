# Strapi Architecture Deep Dive & Quarkus/Java Mapping

This document provides a comprehensive technical analysis of the Strapi v5 architecture, its codebase modules, its database schema systems, and maps them directly to high-performance Quarkus (Java) equivalents. It establishes the architectural foundation for porting Strapi's headless CMS capabilities into the Quarkus reactive ecosystem.

---

## 1. Module Inventory

The table below breaks down every major module in the Strapi monorepo, its functional responsibility, its dependencies, and the exact corresponding Quarkus/Java technology.

| Module / Package | Responsibility in Strapi (Node.js) | Key Dependencies | Quarkus / Java Equivalent Technology | Architectural Mapping & Notes |
| :--- | :--- | :--- | :--- | :--- |
| `@strapi/strapi` | Framework entry point, CLI manager, application bootstrap, and lifecycle management. | Koa, Commander, Chokidar, Dotenv | `quarkus-core`, `picocli` | Quarkus bootstrapper handles the application lifecycle. Picocli is used to build custom CLI commands (e.g., `cms:dev`, `cms:export`). |
| `@strapi/core` | Core server engine. Manages registries for controllers, services, policies, middlewares, and content-types. Sets up Koa HTTP routes. | Koa, `@koa/router`, `@casl/ability`, joi/yup | `quarkus-resteasy-reactive`, `quarkus-vertx`, `jakarta.enterprise.cdi` | Vert.x provides the underlying high-performance reactive event loop. JAX-RS / RESTEasy Reactive handles HTTP routing and middleware (filters). CDI (Arc) manages dependency injection. |
| `@strapi/database` | Database abstraction layer. Generates SQL schemas, manages migrations, translates logical queries into SQL, handles transaction boundaries, and manages hooks/lifecycles. | Knex, Umzug, Ajv | `quarkus-hibernate-orm-panache`, `quarkus-liquibase` or `flyway`, `quarkus-jdbc-postgresql` | Hibernate Panache handles ORM and data access. To handle dynamic schemas without continuous recompilation, we use a hybrid database structure mapping custom content fields to a PostgreSQL `JSONB` column. Liquibase handles static DB schemas (system tables). |
| `@strapi/admin` | Backend administrative API routes (users, roles, permissions, SSO) and Webpack/Vite bundlers for compiling the React-based admin dashboard. | React, Axios, Koa-passport, Formik, Sonner | `quarkus-qute`, `quarkus-security`, HTMX / Tailwind | Instead of client-side React, we replicate the admin panel using server-side rendering (SSR) via Quarkus Qute template engine. UI interactivity is handled by HTMX and Tailwind CSS, providing a blazing-fast, low-memory administration dashboard. |
| `@strapi/content-manager` | Core admin APIs used by the React Admin Panel to perform CRUD operations on both collection and single types, manage drafts, and resolve relations/pivots. | `@strapi/database`, `@strapi/utils` | Dynamic document CRUD service layer in Java | Serves as the central service layer in Java that abstracts CRUD operations, handling publication states, locale resolution, and saving/updating JSONB payloads. |
| `@strapi/content-type-builder` | Development-only module that exposes APIs to create, edit, and delete schemas, writing them to local physical JSON files in the project workspace. | `@strapi/generators`, `fs-extra` | Dynamic schema validator + file writing services in Java | During development, this service updates schema definitions in `/schemas` JSON files. Schema loading is accomplished via Jackson. Schema validation is done using JNetwork/networknt JSON Schema Validator. |
| `@strapi/upload` | Media Library plugin. Handles asset uploads, metadata extraction, responsive format generation (thumbnails, medium, large), and stores assets. | Sharp, Koa-static, mime-types | `quarkus-resteasy-reactive-jackson`, Thumbnailator, Apache Tika | Handles multi-part file uploads. Images are resized using Thumbnailator. Media types are detected using Apache Tika. Integrates with AWS S3 via Quarkus Amazon S3 extension or local disk store. |
| `@strapi/email` | Core plugin for sending emails. Exposes high-level email service configurations. | Nodemailer | `quarkus-mailer` | Standard Quarkus Mailer extension. Supports SMTP, Mailgun, Amazon SES, or SendGrid integration via custom SMTP/API adapters. |
| `@strapi/i18n` | Internationalization plugin. Handles creation and filtering of content across multiple locales. | `@strapi/utils`, react-intl | Multi-locale Document schema mapping | Handled at the database level using standard schema attributes `locale` and `document_id`. The document service resolves the active locale during queries. |
| `@strapi/plugin-users-permissions` | End-user JWT-based authentication, registration, social login providers, and endpoint-level role-based permissions (RBAC). | jsonwebtoken, bcryptjs, grant | `quarkus-security-jpa` or `quarkus-smallrye-jwt`, `bcrypt` | Standard JWT-based auth. Admin endpoints and client APIs are secured using Quarkus Security. End-user roles and permissions are checked via a custom `SecurityIdentityAugmentor`. |
| `@strapi/plugin-graphql` | Generates a unified GraphQL schema based on content-types and exposes a GraphQL endpoint. | Apollo Server, nexus, graphql | `quarkus-smallrye-graphql` | Dynamic schema generation using SmallRye GraphQL. A custom dynamic schema builder registers types at runtime based on JSON schema files. |
| `@strapi/plugin-documentation` | Analyzes content-types to generate OpenAPI specifications and mounts Swagger UI. | swagger-ui-dist, yaml | `quarkus-smallrye-openapi` | Exposes standard OpenAPI documents. Dynamic content-type endpoints are appended to the OpenAPI model at startup. |
| `@strapi/permissions` | Core RBAC permission checker used inside administrative paths to enforce fine-grained field-level and action-level policies. | `@casl/ability`, sift | Dynamic policy verification in Java | Enforces CASL-like permission policies in Java. Permissions are loaded from system tables and evaluated using a light JSONPath-like rule engine. |
| `@strapi/utils` | Shared utility classes (errors, schema validations, async pipes, model transformations). | date-fns, yup, lodash, zod | `jackson-databind`, `guava`, `apache-commons-lang3` | Native Java utilities and libraries. |

---

## 2. Architecture Map & Deep Dive

The diagram below outlines the core architecture of the Quarkus CMS, highlighting the reactive event loop, the Dynamic Document Service, and the hybrid JSONB storage mapping.

```
                   +---------------------------------------+
                   |          HTTP Requests / Clients      |
                   +----+------------------------------+---+
                        | (REST APIs)                  | (SSR Admin UI)
                        v                              v
           +------------+------------+     +-----------+-------------+
           |    RESTEasy Reactive    |     |   SSR Qute Template     |
           |      REST Endpoints     |     |   Engine + HTMX / CSS   |
           +------------+------------+     +-----------+-------------+
                        |                              |
                        +--------------+---------------+
                                       |
                                       v
               +-----------------------+-----------------------+
               |         Quarkus Security (JWT / RBAC)         |
               |      Custom SecurityIdentityAugmentor         |
               +-----------------------+-----------------------+
                                       |
                                       v
               +-----------------------+-----------------------+
               |           Dynamic Document Service            |
               | (Schema Registry, Validation, Query Builder)  |
               +-----------------------+-----------------------+
                                       |
                                       v
               +-----------------------+-----------------------+
               |           Hibernate Reactive / Panache        |
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

### 2.1 Dynamic Schema System

One of Strapi's defining features is its dynamic content modeling.
- In **Strapi v4**, content types represent physical database tables. Changes to content schemas in development trigger runtime DDL changes via Knex (`packages/core/database/src/schema/builder.ts`), updating column names, table links, and indices.
- In **Strapi v5**, this is abstracted by the **Document Service** (`packages/core/core/src/services/document-service`). Models are structured as Documents. A Document is a stable entity identified by a `documentId` (e.g., a cuid/nanoid string). The document has many associated entries, each uniquely representing a specific revision, locale, or draft/published status via a standard sequential integer `id`.

#### The Quarkus Architectural Challenge
Java is a statically typed language. Traditional ORM mappings (such as JPA/Hibernate) require compiled Java classes annotated with `@Entity` at startup. 
Replicating Strapi's model of creating physical SQL tables dynamically at runtime in Java requires either:
1. Dynamic generation of bytecode (`.class` files), injection into the classloader, and triggering Hibernate SessionFactory rebuilds (highly complex, prone to memory leaks, slows down startup, and is incompatible with Quarkus Native compilation via GraalVM).
2. A **Hybrid Document-on-RDBMS Schema Design**.

#### Recommended Solution: Hybrid Document-on-RDBMS Schema
To support high performance, sub-second startup times, and seamless Quarkus Native compilation, we map the dynamic attributes to a PostgreSQL `JSONB` column while keeping the relational index fields as standard physical columns. This mirrors modern database design patterns (e.g., Document-Store in Postgres).

Standard tables are managed statically by Hibernate Panache:
1. `cms_entries`: Stores document metadata (locale, draft/published status, IDs, timestamps) and has a `data` `JSONB` column storing all custom fields.
2. `cms_relations`: A generic relation table to store directional links between documents (allowing many-to-many, one-to-many, and polymorphic links without DDL alterations).
3. `cms_assets`: Stores uploaded media metadata (URLs, sizes, responsive sizes, hashes).

---

## 3. Database Schema Design & Hibernate Panache Entities

Below are the complete, ready-to-implement Jakarta Persistence (JPA) Hibernate Panache entities representing the hybrid dynamic content store.

### 3.1 PostgreSQL DDL Schema

```sql
-- Core Content Table
CREATE TABLE cms_entries (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    status VARCHAR(15) NOT NULL DEFAULT 'draft', -- 'draft', 'published'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    created_by_id BIGINT,
    updated_by_id BIGINT,
    published_by_id BIGINT,
    data JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- Indices for performance
CREATE INDEX idx_entries_doc_id ON cms_entries(document_id);
CREATE INDEX idx_entries_content_type_status ON cms_entries(content_type, status);
CREATE INDEX idx_entries_locale ON cms_entries(locale);
-- GIN Index for fast searching inside the JSONB payload
CREATE INDEX idx_entries_data_gin ON cms_entries USING gin (data);

-- Relations Table (Pivots/Joins)
CREATE TABLE cms_relations (
    id BIGSERIAL PRIMARY KEY,
    field_name VARCHAR(100) NOT NULL,
    source_document_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(100) NOT NULL,
    target_document_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    order_index INT DEFAULT 0
);

CREATE INDEX idx_relations_source ON cms_relations(source_document_id, field_name);
CREATE INDEX idx_relations_target ON cms_relations(target_document_id);
```

### 3.2 Java Panache Entity Implementations

#### CmsEntry.java

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

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "created_by_id")
    public Long createdById;

    @Column(name = "updated_by_id")
    public Long updatedById;

    @Column(name = "published_by_id")
    public Long publishedById;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    public Map<String, Object> data;

    // Helper finders
    public static CmsEntry findByDocumentId(String documentId, String status, String locale) {
        return find("documentId = ?1 and status = ?2 and locale = ?3", documentId, status, locale).firstResult();
    }

    public static java.util.List<CmsEntry> findByContentType(String contentType, String status, String locale) {
        return list("contentType = ?1 and status = ?2 and locale = ?3", contentType, status, locale);
    }
}
```

#### CmsRelation.java

```java
package com.quarkus.cms.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "cms_relations")
public class CmsRelation extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "field_name", nullable = false, length = 100)
    public String fieldName;

    @Column(name = "source_document_id", nullable = false, length = 36)
    public String sourceDocumentId;

    @Column(name = "source_type", nullable = false, length = 100)
    public String sourceType;

    @Column(name = "target_document_id", nullable = false, length = 36)
    public String targetDocumentId;

    @Column(name = "target_type", nullable = false, length = 100)
    public String targetType;

    @Column(name = "order_index")
    public Integer orderIndex = 0;

    // Helper finders
    public static java.util.List<CmsRelation> findRelations(String sourceDocumentId, String fieldName) {
        return list("sourceDocumentId = ?1 and fieldName = ?2 order by orderIndex asc", sourceDocumentId, fieldName);
    }
}
```

---

## 4. Admin Panel Replication with Server-Side Rendering (Qute Templates)

The React-based admin panel in Strapi (`packages/core/admin`) communicates with the backend exclusively via REST endpoints (e.g., `/admin/content-manager/collection-types/:model`).
To build an incredibly lightweight, fast, and resource-efficient alternative in Quarkus, we replace React with **Quarkus Qute server-side template engine**, enhanced with **HTMX** and **Tailwind CSS**.

### 4.1 Admin Route Layout Mapping

A centralized JAX-RS Resource routes administrative paths to Qute views:

```java
package com.quarkus.cms.admin;

import com.quarkus.cms.domain.CmsEntry;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/admin/content-manager")
@Produces(MediaType.TEXT_HTML)
public class AdminContentManagerController {

    @Inject
    Template contentList; // Injecting content-list.html Qute template

    @Inject
    Template contentEdit; // Injecting content-edit.html Qute template

    @GET
    @Path("/collection-types/{model}")
    public TemplateInstance list(@PathParam("model") String model, 
                                 @QueryParam("status") @DefaultValue("draft") String status,
                                 @QueryParam("locale") @DefaultValue("en") String locale) {
        List<CmsEntry> entries = CmsEntry.findByContentType("api::" + model + "." + model, status, locale);
        return contentList.data("model", model)
                          .data("entries", entries)
                          .data("status", status)
                          .data("locale", locale);
    }

    @GET
    @Path("/collection-types/{model}/{documentId}")
    public TemplateInstance edit(@PathParam("model") String model, @PathParam("documentId") String documentId) {
        CmsEntry entry = CmsEntry.findByDocumentId(documentId, "draft", "en");
        return contentEdit.data("model", model)
                          .data("entry", entry)
                          .data("documentId", documentId);
    }
}
```

### 4.2 Qute Templates

To render dynamic fields in Qute, we parse the content-type JSON schema. The template loops over the attributes defined in the schema and dynamically loads sub-templates for rendering form inputs based on the field type (e.g., string, richtext, boolean).

#### Layout Template: `admin-layout.html` (Base Layout with Tailwind and HTMX)

```html
<!DOCTYPE html>
<html lang="en" class="h-full bg-slate-900 text-slate-100">
<head>
    <meta charset="UTF-8">
    <title>{title ?: "Quarkus Headless CMS"}</title>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="h-full flex overflow-hidden">
    <!-- Sidebar -->
    <aside class="w-64 bg-slate-950 border-r border-slate-800 flex flex-col justify-between">
        <div class="p-6">
            <h1 class="text-xl font-bold tracking-wider text-indigo-400">QUARKUS CMS</h1>
            <nav class="mt-8 space-y-2">
                <a href="/admin/content-manager/collection-types/article" class="block px-4 py-2 rounded bg-slate-900 hover:bg-slate-800 transition">Articles</a>
                <a href="/admin/content-manager/collection-types/category" class="block px-4 py-2 rounded hover:bg-slate-800 transition">Categories</a>
                <a href="/admin/content-manager/collection-types/user" class="block px-4 py-2 rounded hover:bg-slate-800 transition">Users</a>
            </nav>
        </div>
        <div class="p-6 border-t border-slate-800 text-sm text-slate-400">
            Profile: admin
        </div>
    </aside>

    <!-- Content Area -->
    <main class="flex-1 flex flex-col overflow-y-auto bg-slate-900">
        <!-- Header -->
        <header class="h-16 border-b border-slate-800 px-8 flex items-center justify-between bg-slate-950">
            <span class="font-medium text-slate-300">Admin Panel</span>
            <a href="/admin/logout" class="text-sm hover:text-indigo-400 transition">Logout</a>
        </header>

        <!-- View Body -->
        <div class="p-8">
            {#insert body}{/insert}
        </div>
    </main>
</body>
</html>
```

#### Content List View Template: `content-list.html`

```html
{#include admin-layout}
  {#title}Articles - Quarkus Headless CMS{/title}
  {#body}
    <div class="flex items-center justify-between mb-8">
        <div>
            <h2 class="text-2xl font-bold text-white capitalize">{model}s</h2>
            <p class="text-slate-400 text-sm mt-1">Manage all your {model} records and translation versions.</p>
        </div>
        <button class="bg-indigo-600 hover:bg-indigo-500 text-white font-medium px-4 py-2 rounded shadow transition"
                hx-get="/admin/content-manager/collection-types/{model}/new"
                hx-target="body">
            + Create New {model}
        </button>
    </div>

    <!-- Filters and Status Selector -->
    <div class="flex space-x-4 mb-6 bg-slate-950 p-4 rounded-lg border border-slate-800">
        <span class="text-slate-400 self-center text-sm">Status:</span>
        <a href="?status=draft" class="px-3 py-1 rounded text-sm {status == 'draft' ? 'bg-indigo-600 text-white' : 'hover:bg-slate-800'}">Draft</a>
        <a href="?status=published" class="px-3 py-1 rounded text-sm {status == 'published' ? 'bg-indigo-600 text-white' : 'hover:bg-slate-800'}">Published</a>
    </div>

    <!-- Entries Table -->
    <div class="bg-slate-950 rounded-lg border border-slate-800 overflow-hidden shadow-xl">
        <table class="min-w-full divide-y divide-slate-800">
            <thead class="bg-slate-900">
                <tr>
                    <th class="px-6 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">Document ID</th>
                    <th class="px-6 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">Title</th>
                    <th class="px-6 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">Locale</th>
                    <th class="px-6 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">Last Modified</th>
                    <th class="px-6 py-3 text-right text-xs font-semibold text-slate-400 uppercase tracking-wider">Actions</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
                {#for entry in entries}
                <tr class="hover:bg-slate-900/50 transition">
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-indigo-400 font-mono font-medium">{entry.documentId}</td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-white font-medium">{entry.data.get("title") ?: "Untitled"}</td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-slate-300">
                        <span class="bg-slate-800 px-2.5 py-1 rounded text-xs font-semibold">{entry.locale}</span>
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-slate-400">{entry.updatedAt}</td>
                    <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                        <a href="/admin/content-manager/collection-types/{model}/{entry.documentId}" class="text-indigo-400 hover:text-indigo-300 mr-4">Edit</a>
                        <button hx-delete="/admin/content-manager/collection-types/{model}/{entry.documentId}" 
                                hx-confirm="Are you sure you want to delete this document?"
                                hx-swap="outerHTML" 
                                class="text-rose-400 hover:text-rose-300">
                            Delete
                        </button>
                    </td>
                </tr>
                {#else}
                <tr>
                    <td colspan="5" class="px-6 py-12 text-center text-slate-500">
                        No entries found. Click the button above to create one.
                    </td>
                </tr>
                {/for}
            </tbody>
        </table>
    </div>
  {/body}
{/include}
```

---

## 5. Auth Flow & JWT Role-Based Security

The CMS enforces two separate authentication security structures:
1. **Admin Panel Security**: Authenticating administrators accessing endpoints under `/admin/*`.
2. **Client API Security**: Authenticating client-side consumer applications calling dynamic API paths `/api/*`.

### 5.1 Admin Panel JWT Flow

In Quarkus, this is implemented using **Quarkus OIDC** or a stateless token cookie model using **SmallRye JWT**. 
For SSR admin screens, JWT tokens are issued upon successful login (validating administrators against a `cms_admin_users` table in the database containing bcrypt-hashed passwords) and are stored securely in an `HttpOnly` cookie named `CMS_SESSION`.

A JAX-RS ContainerRequestFilter automatically extracts the cookie, validates the JWT signature, and populates the SecurityContext:

```java
package com.quarkus.cms.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class AdminSessionAuthFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        UriInfo uriInfo = requestContext.getUriInfo();
        String path = uriInfo.getPath();

        // Skip auth for login pages and assets
        if (!path.startsWith("admin") || path.equals("admin/login")) {
            return;
        }

        Cookie sessionCookie = requestContext.getCookies().get("CMS_SESSION");
        if (sessionCookie == null) {
            requestContext.abortWith(Response.seeOther(uriInfo.getBaseUriBuilder().path("admin/login").build()).build());
            return;
        }

        // Validate the JWT and populate SecurityContext
        try {
            String token = sessionCookie.getValue();
            CmsUserPrincipal principal = TokenService.validateToken(token);
            SecurityContext sc = new CmsSecurityContext(principal, requestContext.getSecurityContext().isSecure());
            requestContext.setSecurityContext(sc);
        } catch (Exception e) {
            requestContext.abortWith(Response.seeOther(uriInfo.getBaseUriBuilder().path("admin/login").build()).build());
        }
    }
}
```

### 5.2 Dynamic Action RBAC (The CASL Mapping)

Strapi admin roles and permissions are highly dynamic. Permissions are modeled as:
`action: "api::article.article.create"`, linked to subject/fields.

In Quarkus, we achieve this by implementing a **Custom Security Identity Augmenter**:

```java
package com.quarkus.cms.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
public class CmsSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }

        return context.runBlocking(() -> {
            String username = identity.getPrincipal().getName();
            // Load user permissions from DB (e.g. "api::article.article.findMany", "plugin::upload.upload")
            Set<String> actions = PermissionService.getActionsForUser(username);

            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
            for (String action : actions) {
                // Add permissions as roles for JAX-RS compatibility
                builder.addRole(action);
            }
            return builder.build();
        });
    }
}
```

This maps CASL's backend policies natively to Jakarta Security. A REST endpoint checking permissions can simply declare:

```java
@GET
@Path("/articles")
@RolesAllowed("api::article.article.findMany")
public Response getArticles() {
    return Response.ok(documentService.findMany("api::article.article")).build();
}
```

---

## 6. Risk Assessment

Porting Strapi to Quarkus offers massive performance benefits (sub-second cold start, 10x lower memory utilization, 5x higher raw JSON throughput) but presents key engineering risks:

1. **Complex JSONB Querying & Indexing**:
   - *Risk*: Implementing complex queries (nested `$and`, `$or`, `$relation.field` deep filters) on a generic dynamic JSONB column is significantly harder in Java than in Node.js (where Knex can easily compile raw JS objects to SQL).
   - *Mitigation*: Develop a strongly-typed `JSONB` SQL compiler in Java that translates Strapi's standard query parameter syntax (like `filters[title][$contains]=Value`) into optimized PostgreSQL JSONB operators (e.g., `data ->> 'title' ILIKE '%Value%'` or utilizing JSONB containment operators `@>`).

2. **Server-Side UI Matching of Permissions**:
   - *Risk*: Strapi's admin React client parses CASL permission manifests to dynamically hide/disable buttons or input fields in real-time. In Qute SSR templates, we need to pre-evaluate these permissions before rendering the DOM.
   - *Mitigation*: Register a custom Qute TemplateExtension class that exposes a `hasPermission(String action)` method inside templates. Example: `{#if user.hasPermission("api::article.article.create")} <button>Create</button> {/if}`.

3. **Database Schema Synchronicity**:
   - *Risk*: Ensuring references in `cms_relations` remain intact when entries are created, updated, or deleted. Without foreign keys enforced directly by database-level engine constraints (since tables aren't physically linked), dynamic relations can easily lead to dangling references.
   - *Mitigation*: Ensure the Java Document Service wraps all relation modifications in a single atomic Transactional context, executing soft-cascade deletes inside a unified Repository class.
