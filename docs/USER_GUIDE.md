# Quarkus Headless CMS - User Guide

Welcome to the User Guide for the Quarkus Headless CMS. This guide is designed to help content creators, site administrators, and developers understand how to model content, manage translation workflows, secure access, handle media, configure webhooks, and use the plugin system.

---

## 1. Content Modeling & Creating Content Types

In Quarkus Headless CMS, all content models are defined as dynamic document schemas. Since the system uses a **Hybrid Document-on-RDBMS Schema**, you do not need to perform SQL schema migrations or compile Java classes when creating or modifying content types. Changes take effect instantly in-memory.

### 1.1 Content Type JSON Schema Structure
Every content type is represented by a JSON file stored in the `/schemas/` directory of your project workspace (e.g., `/schemas/article.json`). 

Here is a standard, comprehensive example of an `Article` content type:

```json
{
  "info": {
    "displayName": "Article",
    "singularName": "article",
    "pluralName": "articles",
    "description": "Standard blog post or news article"
  },
  "options": {
    "draftAndPublish": true,
    "localized": true
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
      "type": "richtext",
      "required": true
    },
    "views": {
      "type": "integer",
      "default": 0
    },
    "publishedAt": {
      "type": "datetime"
    },
    "category": {
      "type": "relation",
      "relationType": "manyToOne",
      "target": "api::category.category",
      "mappedBy": "articles"
    }
  }
}
```

### 1.2 Attributes & Data Types
The following built-in field types are supported natively:

| Type | Java Representation | Description | Validation Options |
|---|---|---|---|
| `string` | `java.lang.String` | Plain text line | `required`, `maxLength`, `unique`, `regex` |
| `text` | `java.lang.String` | Multi-line text block | `required`, `maxLength` |
| `richtext` | `java.lang.String` | Markdown or HTML body | `required` |
| `integer` | `java.lang.Integer` | Standard 32-bit integer | `min`, `max`, `default` |
| `decimal` | `java.lang.Double` | Double-precision decimal | `min`, `max`, `default` |
| `boolean` | `java.lang.Boolean` | True/False toggle | `default` |
| `date` | `java.time.LocalDate` | Year-Month-Day calendar | `required` |
| `datetime` | `java.time.Instant` | ISO 8601 Timestamp | `required` |
| `relation` | `java.lang.String` | Link to another content type | `relationType`, `target`, `mappedBy` |
| `custom` | Dynamic | User-defined custom field | Defined by SPI extensions |

### 1.3 Creating Schemas via Content-Type Builder API
While schemas are loaded from `/schemas/*.json` files on startup, they can also be created dynamically during runtime using the Admin Panel Content-Type Builder endpoints:

- **Endpoint**: `POST /admin/content-types`
- **Payload**: Full schema JSON. The system writes this payload as a physical file to `/schemas/<singularName>.json` and triggers an in-memory hot-reload of the `CmsSchemaRegistry` without restarting the JVM.

---

## 2. Managing Content (CRUD, Draft/Publish & i18n)

Content management is performed either via the SSR Admin Panel or programmatically via the dynamic REST or GraphQL APIs.

### 2.1 Content CRUD Operations
All REST operations are exposed dynamically under `/api/<pluralName>`.

#### 1. Creating Content (POST)
When creating content, write the payload nested within a `"data"` envelope:
- **Request**: `POST /api/articles`
- **Body**:
  ```json
  {
    "data": {
      "title": "Unlocking Quarkus Performance",
      "slug": "unlocking-quarkus-performance",
      "content": "Quarkus compiles to native code..."
    }
  }
  ```
- **Response Status**: `201 Created`

#### 2. Querying Content (GET)
To list multiple articles with dynamic filters, sorting, and pagination:
- **Request**: `GET /api/articles?filters[views][$gt]=10&sort=views:desc&pagination[page]=1&pagination[pageSize]=10`
- **Response**:
  ```json
  {
    "data": [
      {
        "id": 42,
        "documentId": "art_8a2b3c4d",
        "locale": "en",
        "status": "published",
        "attributes": {
          "title": "Unlocking Quarkus Performance",
          "slug": "unlocking-quarkus-performance",
          "content": "Quarkus compiles to native code...",
          "views": 150
        }
      }
    ],
    "meta": {
      "pagination": {
        "page": 1,
        "pageSize": 10,
        "pageCount": 1,
        "total": 1
      }
    }
  }
  ```

### 2.2 Draft and Publish Lifecycle
The CMS splits content states into two isolated rows inside the database: `draft` and `published`. This allows content editors to work on changes without exposing them to production readers.

1. **Staging**: When you create or update an entry, it is saved under the `draft` status.
2. **Promoting to Production (Publish)**:
   - **Request**: `POST /api/articles/art_8a2b3c4d/publish`
   - **Action**: The system copies the current `draft` state, updates its status to `published`, and records the `publishedAt` timestamp.
3. **Reverting (Unpublish)**:
   - **Request**: `POST /api/articles/art_8a2b3c4d/unpublish`
   - **Action**: The `published` database row is deleted, while the `draft` version remains fully intact.

*Note: The client REST API `/api/articles` defaults to serving `published` content. To query draft content, append `?status=draft` to the URL.*

### 2.3 Internationalization (i18n)
Quarkus Headless CMS supports translation workflows out of the box using **Document-Id Binding**. Under this model:
- A single conceptual document has multiple translation entries in the database.
- Each entry has its own unique sequential `id`, but they all share a stable, identical `documentId` (e.g., `art_8a2b3c4d`).
- Language variations are separated using the `locale` field (e.g., `en`, `fr`, `de`).

#### Translating Content (Create Localization)
To translate an existing article to French:
- **Request**: `POST /api/articles/art_8a2b3c4d/localizations`
- **Body**:
  ```json
  {
    "locale": "fr",
    "data": {
      "title": "Optimiser les performances de Quarkus",
      "slug": "optimiser-les-performances-de-quarkus",
      "content": "Quarkus compile en code natif..."
    }
  }
  ```

#### Querying Translations
When consuming APIs, the system resolves locales dynamically:
1. **Query Parameter**: Append `?locale=fr` to fetch French.
2. **Accept-Language Header**: Send `Accept-Language: fr,en;q=0.9` to retrieve the requested language.
3. **Locale Fallback**: If a localized version does not exist, the system automatically falls back to the Default Locale configured in your `application.properties` (defaults to `en`).

---

## 3. Security, Users, Roles & Permissions

Security is divided into two strict layers: Admin Authentication (cookie-based JWT) and API Token Access (header-based Bearer authentication).

### 3.1 Administrative RBAC
Administrative accounts manage the CMS via the Admin Panel. Roles (e.g., `SuperAdmin`, `Editor`, `Author`) have custom CASL-inspired dynamic permissions checked dynamically upon entry into admin routes.

- **Admin Login**: `POST /admin/login` yields a secure, stateless session cookie named `CMS_SESSION`.
- **JWT Protection**: The session cookie is authenticated on the server side using Quarkus SmallRye JWT security.

### 3.2 Client API Tokens
Programmatic consumers (headless frontends, mobile clients) must access the `/api/*` endpoints using a bearer token generated inside the admin panel.

- **Header Format**: `Authorization: Bearer <api_token_value>`
- **Token Permissions**: Every token is mapped to an access policy. Policies define fine-grained action arrays such as:
  ```json
  [
    "api::article.article.findMany",
    "api::article.article.findOne",
    "api::category.category.findMany"
  ]
  ```
  If an API token makes a request to `GET /api/articles` without having `api::article.article.findMany` in its permission set, the system throws an HTTP `403 Forbidden` response.

---

## 4. Media Upload & Asset Management

The Media Library tracks uploaded assets and exposes endpoints under `/api/upload`.

### 4.1 Storage Providers
The backend abstracts storage using the `StorageProvider` SPI. Two main providers are supported out of the box:
1. **Local Disk**: Stores files in a local directory. Good for local testing and container mounts.
2. **S3 / R2 Object Storage**: Uploads assets directly to Amazon S3 or Cloudflare R2 bucket. Recommended for production and native deployments.

Enable your preferred provider in `application.properties`:
```properties
cms.media.storage.provider=s3
cms.media.storage.s3.bucket=my-cms-bucket
cms.media.storage.s3.region=us-east-1
```

### 4.2 Image Processing & Optimization
Upon upload, the system validates the file and creates multi-size options automatically:
- **Type Checking**: File mime-types are validated on-stream using **Apache Tika** to prevent uploading malicious executable files disguised as images.
- **Auto-Optimization**: Large images are automatically resized, compressed, and stripped of metadata to speed up delivery.
- **Thumbnail Generation**: A low-overhead thumbnail (e.g. 150x150 pixels) is automatically generated using the **TwelveMonkeys/Thumbnailator** libraries and linked to the media entry.

---

## 5. Webhook Configuration

Webhooks dispatch real-time events to external servers when content changes occur.

### 5.1 Registering Webhooks
Webhooks are managed via the REST admin API at `POST /admin/webhooks` or through the administrative portal:

```json
{
  "name": "Frontend Rebuilder",
  "url": "https://api.frontend.com/rebuild-trigger",
  "secret": "my_hmac_secret_key",
  "events": [
    "entry.publish",
    "entry.unpublish"
  ],
  "headers": {
    "X-Custom-Source": "Quarkus-CMS"
  },
  "enabled": true
}
```

### 5.2 Reliable Non-Blocking Dispatch
- **Vert.x Dispatcher**: Webhooks are dispatched asynchronously using non-blocking Vert.x HTTP Clients, preventing webhook calls from blocking standard database transactions or slowing down user response times.
- **HMAC Signatures**: Each request contains an `X-Hub-Signature-256` header containing the hex signature of the JSON payload hashed using your configured HMAC secret key.
- **Retries**: If the receiving server is offline, the dispatcher retries using an exponential backoff strategy (up to a configurable maximum, defaulting to 5 times).
- **Audit Logs**: All deliveries are logged in the `cms_webhook_deliveries` database table, capturing status codes, request/response bodies, and round-trip latencies.

---

## 6. Using the Plugin System

The modular plugin system allows developers to install and activate features dynamically. 

### 6.1 Discovering Plugins
The `PluginRegistry` automatically registers all plugins packaged inside the classpath using Java's `ServiceLoader` SPI mechanism.

- **Check Active Plugins**: Make a `GET /admin/plugins` call. It returns all active extensions, their metadata, configurations, and paths.

### 6.2 Example: The SEO Plugin
When the **SEO Plugin** (`cms-seo-plugin`) is enabled:
- **Automatic Fields**: It automatically appends three SEO fields (`metaTitle`, `metaDescription`, `metaKeywords`) to all dynamic schemas loaded by the system.
- **Automatic Metadata Generation**: If an author saves an article but leaves the `metaDescription` empty, the SEO Plugin's lifecycle hook intercepts the operation, extracts the first 160 characters from the plain-text `content` field, and populates the `metaDescription` field dynamically before saving.
- **Admin Integration**: It injects an administrative settings page dynamically into the sidebar at `/admin/plugins/seo`.
