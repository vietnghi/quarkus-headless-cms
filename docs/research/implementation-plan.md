# Quarkus Headless CMS - Phased Implementation Plan

This implementation plan outlines the engineering roadmap for building a high-performance, lightweight headless CMS in Quarkus (Java) based on the architectural mapping of Strapi v5.

---

## 1. Executive Roadmap

The development of the Quarkus Headless CMS is structured into **5 sequential phases** spanning a total of **10 weeks**. 

```
                                  TIMELINE ROADMAP
   W1-W2             W3-W4             W5                W6-W8             W9-W10
+----------+      +----------+      +----------+      +----------+      +----------+
| Phase 1  |----->| Phase 2  |----->| Phase 3  |----->| Phase 4  |----->| Phase 5  |
| Database |      | Document |      | Auth &   |      | SSR Qute |      | Production|
| & Core   |      | Service  |      | Security |      | UI &     |      | Features |
| Found'n  |      | CRUD API |      | (RBAC)   |      | Assets   |      | & APIs   |
+----------+      +----------+      +----------+      +----------+      +----------+
```

*   **Overall Goal**: Build a headless CMS that equals Strapi's content-modeling flexibility, while delivering a sub-second startup footprint, less than 150MB of memory utilization under load, and native-compilation support via GraalVM.

---

## 2. Phased Engineering Plan

### Phase 1: Database & Core Foundation (Weeks 1-2)
Establish the Quarkus reactive runtime, database infrastructure, and content schema registry.

*   **Objectives**:
    1.  Set up the Quarkus skeleton project using Maven and standard extensions.
    2.  Implement Liquibase schema migrations for the standard administration and core tables.
    3.  Define the schema registry that loads dynamic content-type definitions from JSON configuration files (located in `/schemas`).
*   **Tasks**:
    *   Set up a standard Quarkus project with dependencies for RESTEasy Reactive, Hibernate ORM with Panache, Jackson, and Liquibase.
    *   Write database migration files for tables: `cms_entries`, `cms_relations`, `cms_assets`, `cms_users`, `cms_sys_permissions`.
    *   Create a schema parsing registry (`CmsSchemaRegistry.java`) that scans `/schemas/*.json` at application startup. Use Jackson to load attributes, field types, and validation rules.
*   **Deliverables**:
    *   Working Quarkus codebase with database migrations passing on PostgreSQL.
    *   Startup-time log confirming schemas successfully parsed and registered in-memory.

### Phase 2: Dynamic Document Service & CRUD Engine (Weeks 3-4)
Build the dynamic service layer that translates incoming request payloads into JSONB queries, validating data against active schemas.

*   **Objectives**:
    1.  Implement the `CmsDocumentService` to handle document creation, updates, locale handling, and publication status.
    2.  Build a dynamic SQL compiler translating query params (filters, pagination, sorting) into PostgreSQL JSONB query structures.
    3.  Enforce schema-based validation for incoming JSON payloads.
*   **Tasks**:
    *   Write `CmsDocumentService.java` with core methods: `findMany(contentType, params)`, `findOne(contentType, documentId, params)`, `create(contentType, data)`, `update(contentType, documentId, data)`, and `delete(contentType, documentId)`.
    *   Implement draft/publish logic: `publish(contentType, documentId)` duplicates the 'draft' status row to a 'published' status row.
    *   Develop a query parser (`CmsQueryBuilder.java`) converting standard Strapi-like filters (e.g. `filters[title][$contains]=Value`) into JPA/Hibernate native queries utilizing PG operators (`data ->> 'title' ILIKE '%Value%'`).
    *   Integrate networknt JSON Schema Validator to assert data payloads match defined content schemas.
*   **Deliverables**:
    *   Fully functional dynamic CRUD service engine.
    *   A suite of integration tests verifying JSONB CRUD, draft/publishing replication, and nested filtering.

### Phase 3: Identity & Access Management (Week 5)
Implement end-to-end security, covering Admin users, API client keys, and CASL-like dynamic permission evaluation.

*   **Objectives**:
    1.  Secure administrative routes under `/admin/*` via stateless secure JWT cookies.
    2.  Secure client API routes under `/api/*` via HTTP Bearer tokens and API Tokens.
    3.  Implement a dynamic Security Augmenter to load runtime RBAC permissions.
*   **Tasks**:
    *   Build login resources for admin users issuing JWT credentials signed via SmallRye JWT. Store the token in an `HttpOnly` and `Secure` cookie.
    *   Develop API Token registry table `cms_api_tokens`. Implement a JAX-RS ContainerRequestFilter that validates incoming requests against registered API tokens.
    *   Write `CmsSecurityIdentityAugmentor.java` to load permissions from `cms_sys_permissions` table for the authenticated user and dynamically bind them as roles to the security context.
*   **Deliverables**:
    *   Functional login UI and REST auth filters.
    *   API tests confirming unauthorized requests are blocked with HTTP 401/403.

### Phase 4: SSR Administration Interface (Weeks 6-8)
Develop a fast, modern, and server-side rendered admin dashboard with Qute templates, HTMX, and Tailwind.

*   **Objectives**:
    1.  Design a cohesive sidebar-based master layout for the administration panel.
    2.  Create content list, creation, and editing views that dynamically render input fields based on schema configurations.
    3.  Integrate file uploading and a media library manager.
*   **Tasks**:
    *   Write `admin-layout.html`, `content-list.html`, and `content-edit.html` Qute templates.
    *   Implement dynamic inputs: loop over schema attributes and render `<input type="text">` for string, checkbox for boolean, `<textarea>` for text, or rich text editor scripts for richtext.
    *   Implement HTMX-based inline saving, list pagination, and dynamic locale switching.
    *   Build media library views, allowing multi-file drag-and-drop uploads, integrating with local disk file storage or S3 buckets, and logging upload details in `cms_assets`.
*   **Deliverables**:
    *   Ready-to-use SSR Admin dashboard accessible via `/admin`.
    *   Support for CRUD of custom entities (e.g., Articles, Categories) directly from the browser.

### Phase 5: Production Features & Extensibility (Weeks 9-10)
Develop GraphQL support, API generation, email integrations, and benchmark the complete system.

*   **Objectives**:
    1.  Expose a dynamic, schema-driven GraphQL endpoint.
    2.  Integrate the email service and implement S3 upload adapters.
    3.  Run load testing and compile the application to a native GraalVM binary.
*   **Tasks**:
    *   Implement dynamic GraphQL type registration. Using SmallRye GraphQL, map dynamic content schemas to active GraphQL fields at application startup.
    *   Create `AmazonS3AssetProvider.java` utilising the Quarkus S3 Client to store media library uploads on S3.
    *   Integrate Quarkus Mailer to trigger transactional emails on user events.
    *   Run performance profiling (memory usage, request latency, throughput) using Gatling or wrk.
    *   Build the application with `-Dquarkus.package.type=native` and verify native executable runs flawlessly on Docker.
*   **Deliverables**:
    *   Production-ready HEADLESS CMS binary.
    *   Performance benchmark documentation confirming latency under 5ms and memory usage below 100MB.

---

## 3. Technology Stack & Extensions

The system utilizes standard Quarkus extensions and top-tier Java libraries:

| Technology Component | Java Library / Quarkus Extension | Purpose in CMS |
| :--- | :--- | :--- |
| **Reactive Web Engine** | `quarkus-resteasy-reactive` | Reactive JAX-RS REST endpoint routing |
| **JSON Parser** | `quarkus-resteasy-reactive-jackson` | Dynamic payload serialization/deserialization |
| **Database Access** | `quarkus-hibernate-orm-panache` | Active record ORM mappings for standard schemas |
| **Migrations** | `quarkus-liquibase` | Declarative database migrations |
| **JWT Authentication** | `quarkus-smallrye-jwt` | Stateless token creation and validation |
| **Template Engine** | `quarkus-qute` | Reactive, high-performance server-side layout rendering |
| **S3 Storage** | `quarkus-amazon-s3` | Storing uploaded media assets on AWS S3 buckets |
| **Email Service** | `quarkus-mailer` | Email dispatching capabilities |
| **GraphQL Engine** | `quarkus-smallrye-graphql` | Dynamic GraphQL endpoints generation |
| **Schema Validation** | `com.networknt:json-schema-validator` | Validating entry JSON payloads against schemas |
| **Image Processing** | `net.coobird:thumbnailator` | Creating responsive thumbnail formats |
| **Mime Detection** | `org.apache.tika:tika-core` | Securing uploads by verifying file contents |

---

## 4. Verification & Testing Strategy

A rigorous quality-assurance framework guarantees system stability across schema alterations.

### 4.1 Unit Testing (`quarkus-junit5`)
*   **Schema Parsing Tests**: Assert `CmsSchemaRegistry` parses multi-field JSON files correctly, reporting errors on malformed definitions.
*   **Validation Tests**: Mock schema rules and verify the networknt validation engine throws appropriate constraint exceptions when required fields are missing or text lengths are exceeded.

### 4.2 Integration Testing (`RESTAssured`)
*   **Generic CRUD Operations**: Programmatically call JAX-RS endpoints using RESTAssured to create, read, update, and delete entries. Verify database results in `cms_entries` match the payload.
*   **Query Compiler Verifications**: Write tests passing various query params (e.g. `sort=title:desc&filters[author][$eq]=admin`) to assert `CmsQueryBuilder` compiles the parameters to exact SQL operators.
*   **Draft/Publish Isolation**: Verify querying with `status=published` returns only published rows, while draft entries remain isolated.

### 4.3 UI Verification & Walkthrough
*   **Manual Inspection**: Navigate the SSR admin panel. Confirm creating a new content type (e.g., adding an API article schema) displays the new menu sidebar link, and opening the article editor dynamically loads input textboxes.
*   **HTMX Verification**: Open browser developer console and confirm form saving triggers lightweight partial HTML updates via HTMX without full-page reloads.
