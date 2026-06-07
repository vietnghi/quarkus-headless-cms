# Quarkus Headless CMS - Strapi v5 Migration Guide

This guide is designed for developers migrating headless CMS solutions from **Strapi v5** (Node.js) to the high-performance **Quarkus Headless CMS** (Java). It covers architectural mappings, database schema structures, API usage differences, and plugin porting guidelines.

---

## 1. Architectural Mapping

Porting your application from Node.js (Koa) to Java (Quarkus) involves replacing asynchronous Javascript frameworks with reactive, high-concurrency Java specifications.

| Concept / Layer | Strapi v5 (Node.js) | Quarkus Headless CMS (Java) |
|---|---|---|
| **Underlying Engine**| Node.js / V8 | Quarkus / Vert.x Event Loop |
| **HTTP Framework** | Koa / Koa Router | RESTEasy Reactive / Jakarta REST |
| **Dependency Injection** | Internal Service Registry | Jakarta CDI (Arc container) |
| **Database Migrations**| Umzug / Knex | Flyway Database Migrations |
| **Data Access & ORM** | Knex / Bookshelf | Hibernate ORM with Panache |
| **Content Modeling** | Dynamic physical database tables | Hybrid Document-on-RDBMS (JSONB) |
| **Template Engine** | React (Admin) / Koa-static | Qute SSR Templates / HTMX |
| **Security & JWT** | Passport / jsonwebtoken | Quarkus Security / SmallRye JWT |
| **Asynchronous Events**| Node Event Emitters | Jakarta CDI Events & Vert.x Event Bus|

---

## 2. Database Schema Migration

Strapi v5 introduced a conceptual document model where content records are grouped under a logical `documentId` and split by versions, locales, and publication statuses. Quarkus Headless CMS maintains a highly compatible document layout but stores dynamic fields in a hybrid `JSONB` database column.

### 2.1 Schema Definition Differences

In **Strapi v5**, each content type compiles into its own dedicated SQL table (e.g., `articles` with columns `title`, `slug`, `views`, etc.).

In **Quarkus Headless CMS**, all user-defined content type entries are stored in a single unified table named `cms_entries`. Dynamic schema definitions are compiled to a `JSONB` data column inside that table, eliminating DDL changes in production and securing complete GraalVM Native compilation support.

### 2.2 Table Mapping Reference

If you are writing a data migration script (e.g., pg_dump, ETL pipeline, Python migration script), use this structural mapping to migrate records:

| Strapi v5 Table / Field | Quarkus Headless CMS Table / Column | Notes |
|---|---|---|
| `articles` (Physical table) | `cms_entries` (Unified table) | Core target for all custom schemas. |
| `articles.id` | `cms_entries.id` | Sequential autoincrement integer ID. |
| `articles.document_id` | `cms_entries.document_id` | Stable shared logical ID. |
| `articles.locale` | `cms_entries.locale` | Locale ISO code (e.g., `en`, `fr`). |
| `articles.published_at` | `cms_entries.published_at` | Null for draft, filled for published. |
| `articles.title` (Column) | `cms_entries.data ->> 'title'` | Custom attributes go inside the JSONB payload. |
| `articles.slug` (Column) | `cms_entries.data ->> 'slug'` | Custom attributes go inside the JSONB payload. |
| `articles_categories_links` (Join) | `cms_relations` | Unified dynamic adjacency relation links. |
| `files` (Media) | `cms_assets` | Media metadata catalog. |
| `strapi_webhooks` | `cms_webhooks` | Webhook configurations. |

### 2.3 Executing a DB-Level Data Migration (SQL Script)
Below is an example PostgreSQL query demonstrating how to extract and load data directly from a Strapi v5 PostgreSQL database structure into the Quarkus `cms_entries` hybrid schema:

```sql
-- Example SQL data transfer script
INSERT INTO cms_entries (
    document_id,
    content_type,
    locale,
    status,
    data,
    created_at,
    updated_at,
    published_at
)
SELECT 
    s.document_id,
    'api::article.article' AS content_type,
    COALESCE(s.locale, 'en') AS locale,
    CASE 
        WHEN s.published_at IS NOT NULL THEN 'published' 
        ELSE 'draft' 
    END AS status,
    -- Construct the JSONB data envelope dynamically
    jsonb_build_object(
        'title', s.title,
        'slug', s.slug,
        'content', s.content,
        'views', COALESCE(s.views, 0)
    ) AS data,
    s.created_at,
    s.updated_at,
    s.published_at
FROM strapi_v5_database.public.articles s;
```

---

## 3. API Query Parameter Mapping

The REST API of Quarkus Headless CMS was built to maintain maximum backwards compatibility with Strapi v5's client query parser, preserving LHS (Left-Hand Side) bracket structures.

### 3.1 Syntax Comparisons

| Feature | Strapi v5 Request | Quarkus CMS Request | Difference / Notes |
|---|---|---|---|
| **Equal Filtering** | `/api/articles?filters[title][$eq]=hello` | `/api/articles?filters[title][$eq]=hello` | Fully identical syntax. |
| **Comparison Filters**| `/api/articles?filters[views][$gt]=10` | `/api/articles?filters[views][$gt]=10` | Fully identical syntax. |
| **String Containment**| `/api/articles?filters[title][$contains]=java` | `/api/articles?filters[title][$contains]=java` | Fully identical syntax (case-insensitive).|
| **Sorting** | `/api/articles?sort=views:desc` | `/api/articles?sort=views:desc` | Fully identical syntax. |
| **Pagination** | `/api/articles?pagination[page]=1` | `/api/articles?pagination[page]=1` | Fully identical syntax. |
| **Locale Filtering** | `/api/articles?locale=fr` | `/api/articles?locale=fr` | Fully identical syntax. |
| **Status Isolation** | `/api/articles?status=draft` | `/api/articles?status=draft` | Defaults to `published` on client API. |

---

## 4. Porting Plugins (Strapi v5 vs. Quarkus CMS)

Porting custom Javascript/Typescript plugins from Strapi's modular architecture to Quarkus Java requires replacing the node plugin API with Java SPI (Service Provider Interface) implementations.

### 4.1 Concept Mapping

| Strapi v5 Plugin Component | Quarkus Headless CMS Java Equivalent |
|---|---|
| `strapi-server.js` (entry point) | `com.quarkus.cms.plugin.CmsPlugin` (SPI interface) |
| `register()` callback | `register(PluginRegistrationContext context)` method |
| `bootstrap()` callback | CDI `@Observes StartupEvent` in plugin codebase |
| `contentType` schema extension | `com.quarkus.cms.plugin.content.ContentTypeExtension` |
| `server.routes` (Custom endpoints) | `com.quarkus.cms.plugin.endpoint.EndpointRegistration` |
| `lifecycle.beforeCreate` hooks | CDI event observers or `com.quarkus.cms.plugin.hook.PluginHook` |

### 4.2 Code Conversion Example

#### Strapi v5 Plugin Registration (`strapi-server.js`)
```javascript
module.exports = () => ({
  register({ strapi }) {
    strapi.customField.register({
      name: 'color',
      plugin: 'color-picker',
      type: 'string',
    });
  },
  bootstrap({ strapi }) {
    console.log('Color Picker Plugin bootstrapped!');
  }
});
```

#### Quarkus Headless CMS Java Porting (`ColorPickerPlugin.java`)
```java
package com.quarkus.cms.plugin.colorpicker;

import com.quarkus.cms.plugin.CmsPlugin;
import com.quarkus.cms.plugin.PluginMetadata;
import com.quarkus.cms.plugin.PluginRegistrationContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ColorPickerPlugin implements CmsPlugin {

  private static final PluginMetadata METADATA = 
      PluginMetadata.builder("color-picker-plugin", "1.0.0")
          .displayName("Color Picker Plugin")
          .description("Custom fields supporting hex color picker configurations")
          .author("Dev Team")
          .build();

  @Override
  public PluginMetadata getMetadata() {
    return METADATA;
  }

  @Override
  public void register(PluginRegistrationContext context) {
    Log.info("Color Picker Plugin registering...");
    // Register custom fields, admin pages, or hooks here via context
    Log.info("Color Picker Plugin registered successfully!");
  }

  @Override
  public void unregister() {
    Log.info("Color Picker Plugin deactivated.");
  }
}
```

To make the plugin discoverable by Quarkus on startup, create a service descriptor file under `src/main/resources/META-INF/services/com.quarkus.cms.plugin.CmsPlugin` containing the fully qualified class name of your plugin class:
```
com.quarkus.cms.plugin.colorpicker.ColorPickerPlugin
```
This mirrors Strapi's package-based discovery with native, zero-overhead Java standard class loaders.
