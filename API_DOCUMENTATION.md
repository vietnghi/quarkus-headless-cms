# Quarkus Headless CMS - API Documentation

This reference guide documents the REST, GraphQL, and Authentication endpoints exposed by Quarkus Headless CMS.

---

## 1. Authentication Endpoints

The system uses a split authentication model: JAX-RS cookie-based authentication for administrative operations (`/admin/*`) and header-based Bearer authentication (`Authorization: Bearer *** for consuming applications (`/api/*`).

### 1.1 Administrative User Login
Log in to receive a secure, stateless session cookie for the Admin Panel.

- **URL**: `/admin/login`
- **Method**: `POST`
- **Headers**:
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "username": "admin",
    "password": "secure_password_here"
  }
  ```
- **Response**:
  - **Status**: `200 OK` (with `Set-Cookie: CMS_SESSION=<jwt_token>; Path=/admin; HttpOnly; Secure`)
  - **Body**:
    ```json
    {
      "status": "success",
      "user": {
        "username": "admin",
        "email": "admin@quarkuscms.com",
        "role": "SuperAdmin"
      }
    }
    ```

---

### 1.2 Administrative User Logout
Invalidate the active administration session.

- **URL**: `/admin/logout`
- **Method**: `POST`
- **Response**:
  - **Status**: `200 OK` (clears the `CMS_SESSION` cookie)

---

## 2. API Token Access (Bearer Auth)

To make requests to the Client API (`/api/*`), client applications must provide an API Token as an HTTP Bearer Token.

- **Header**: `Authorization: Bearer <API_T...nAPI Tokens can be generated inside the admin panel or stored in the `cms_api_tokens` database table. Each token has an associated access policy containing specific roles and permissions (e.g., `api::article.article.findMany`, `api::article.article.findOne`).

---

## 3. Core REST API (`/api/*`)

All custom content types (e.g. `article`, `category`) are exposed dynamically under `/api/<content_type_plural>`.

---

### 3.1 Fetch Multiple Entries
Query a list of records for a specific content type. Supports advanced queries via URL query parameters.

- **URL**: `/api/articles`
- **Method**: `GET`
- **Headers**:
  - `Authorization: Bearer *** **Query Parameters**: See [Query Parameters & Filters](#4-query-parameters--filters) below.
- **Response**:
  - **Status**: `200 OK`
  - **Body**:
    ```json
    {
      "data": [
        {
          "id": 1,
          "documentId": "art_9x2b8c5d",
          "contentType": "api::article.article",
          "locale": "en",
          "status": "published",
          "createdAt": "2026-06-07T12:00:00Z",
          "updatedAt": "2026-06-07T14:30:00Z",
          "publishedAt": "2026-06-07T14:30:00Z",
          "attributes": {
            "title": "Introduction to Quarkus Headless CMS",
            "slug": "introduction-to-quarkus-headless-cms",
            "content": "Quarkus CMS is ultra-fast...",
            "views": 320
          }
        }
      ],
      "meta": {
        "pagination": {
          "page": 1,
          "pageSize": 25,
          "pageCount": 1,
          "total": 1
        }
      }
    }
    ```

---

### 3.2 Fetch One Entry
Query a single record by its `documentId` or sequential integer database `id`.

- **URL**: `/api/articles/art_9x2b8c5d`
- **Method**: `GET`
- **Headers**:
  - `Authorization: Bearer *** **Response**:
  - **Status**: `200 OK`
  - **Body**:
    ```json
    {
      "data": {
        "id": 1,
        "documentId": "art_9x2b8c5d",
        "contentType": "api::article.article",
        "locale": "en",
        "status": "published",
        "createdAt": "2026-06-07T12:00:00Z",
        "updatedAt": "2026-06-07T14:30:00Z",
        "publishedAt": "2026-06-07T14:30:00Z",
        "attributes": {
          "title": "Introduction to Quarkus Headless CMS",
          "slug": "introduction-to-quarkus-headless-cms",
          "content": "Quarkus CMS is ultra-fast...",
          "views": 320
        }
      }
    }
    ```

---

### 3.3 Create an Entry
Create a new record. By default, new entries are created with a `draft` status.

- **URL**: `/api/articles`
- **Method**: `POST`
- **Headers**:
  - `Authorization: Bearer ***  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "data": {
      "title": "A Brand New Post",
      "slug": "a-brand-new-post",
      "content": "Some interesting facts...",
      "views": 0
    }
  }
  ```
- **Response**:
  - **Status**: `201 Created`
  - **Body**:
    ```json
    {
      "data": {
        "id": 2,
        "documentId": "art_7k4f1p9q",
        "contentType": "api::article.article",
        "locale": "en",
        "status": "draft",
        "createdAt": "2026-06-07T15:00:00Z",
        "updatedAt": "2026-06-07T15:00:00Z",
        "publishedAt": null,
        "attributes": {
          "title": "A Brand New Post",
          "slug": "a-brand-new-post",
          "content": "Some interesting facts...",
          "views": 0
        }
      }
    }
    ```

---

### 3.4 Update an Entry
Partially or fully update an existing entry. Modifying a published entry updates its `draft` version.

- **URL**: `/api/articles/art_7k4f1p9q`
- **Method**: `PUT`
- **Headers**:
  - `Authorization: Bearer ***  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "data": {
      "title": "A Brand New Post (Updated Title)",
      "views": 5
    }
  }
  ```
- **Response**:
  - **Status**: `200 OK`
  - **Body**:
    ```json
    {
      "data": {
        "id": 2,
        "documentId": "art_7k4f1p9q",
        "contentType": "api::article.article",
        "locale": "en",
        "status": "draft",
        "createdAt": "2026-06-07T15:00:00Z",
        "updatedAt": "2026-06-07T15:15:00Z",
        "publishedAt": null,
        "attributes": {
          "title": "A Brand New Post (Updated Title)",
          "slug": "a-brand-new-post",
          "content": "Some interesting facts...",
          "views": 5
        }
      }
    }
    ```

---

### 3.5 Delete an Entry
Permanently remove an entry, deleting both draft and published status rows, plus cascades in the relation tables.

- **URL**: `/api/articles/art_7k4f1p9q`
- **Method**: `DELETE`
- **Headers**:
  - `Authorization: Bearer *** **Response**:
  - **Status**: `204 No Content`

---

### 3.6 Draft & Publish Endpoints
Promote a draft version of an entry to a production-published row.

#### Publish a Document
- **URL**: `/api/articles/art_7k4f1p9q/publish`
- **Method**: `POST`
- **Headers**:
  - `Authorization: Bearer *** **Response**:
  - **Status**: `200 OK`
  - **Body**:
    ```json
    {
      "id": 3,
      "documentId": "art_7k4f1p9q",
      "status": "published",
      "publishedAt": "2026-06-07T15:20:00Z"
    }
    ```

#### Unpublish a Document (Revert to Draft only)
- **URL**: `/api/articles/art_7k4f1p9q/unpublish`
- **Method**: `POST`
- **Headers**:
  - `Authorization: Bearer *** **Response**:
  - **Status**: `200 OK`
  - **Body**:
    ```json
    {
      "documentId": "art_7k4f1p9q",
      "status": "draft",
      "publishedAt": null
    }
    ```

---

## 4. Query Parameters & Filters

The dynamic SQL query compiler parses standard URL parameters to dynamically filter, sort, paginate, and fetch localized data.

### 4.1 Filtering
Filters are declared using LHS bracket syntax matching `filters[attribute_name][operator]=value`.

| Operator | SQL Translation | Description | Example |
| :--- | :--- | :--- | :--- |
| `$eq` | `=` | Equal to | `/api/articles?filters[views][$eq]=320` |
| `$ne` | `!=` | Not equal to | `/api/articles?filters[slug][$ne]=home` |
| `$contains` | `ILIKE %val%` | Case-insensitive match | `/api/articles?filters[title][$contains]=quarkus` |
| `$gt` | `>` | Greater than | `/api/articles?filters[views][$gt]=100` |
| `$gte` | `>=` | Greater than or equal to | `/api/articles?filters[views][$gte]=100` |
| `$lt` | `<` | Less than | `/api/articles?filters[views][$lt]=50` |
| `$lte` | `<=` | Less than or equal to | `/api/articles?filters[views][$lte]=50` |

Multiple filters can be combined, resolving as a logical `AND`.
- **Example**: `/api/articles?filters[title][$contains]=quarkus&filters[views][$gt]=50`

---

### 4.2 Sorting
Sort entries using the `sort` parameter. Syntax: `sort=attribute_name:asc` or `sort=attribute_name:desc`.

- **Example**: Sort articles by views descending, then by title ascending.
  - `/api/articles?sort=views:desc&sort=title:asc`

---

### 4.3 Pagination
Paginate results by supplying page indexes and page sizes.

- **Example**: Fetch page 2 with 10 records per page.
  - `/api/articles?pagination[page]=2&pagination[pageSize]=10`

---

### 4.4 Status Isolation
Specify whether to query `draft` or `published` content (defaults to `published` on client API, `draft` in admin panel).

- **Example**: Query draft articles:
  - `/api/articles?status=draft`

---

### 4.5 Localization
Filter documents by target locale code (defaults to `en`).

- **Example**: Query French translations:
  - `/api/articles?locale=fr`

---

## 5. GraphQL API (`/q/graphql`)

Exposes a dynamic GraphQL endpoint dynamically generated from schemas loaded at startup.

### 5.1 Querying Articles
Fetch a list of articles with select fields.

- **Endpoint**: `/q/graphql`
- **Method**: `POST`
- **Query**:
  ```graphql
  query {
    articles(locale: "en", status: "published", limit: 5) {
      documentId
      title
      slug
      views
    }
  }
  ```
- **Response**:
  ```json
  {
    "data": {
      "articles": [
        {
          "documentId": "art_9x2b8c5d",
          "title": "Introduction to Quarkus Headless CMS",
          "slug": "introduction-to-quarkus-headless-cms",
          "views": 320
        }
      ]
    }
  }
  ```

---

### 5.2 Creating an Article Mutation
Add a new draft article via GraphQL.

- **Mutation**:
  ```graphql
  mutation {
    createArticle(data: {
      title: "GraphQL Integration"
      slug: "graphql-integration"
      content: "Exposing dynamic APIs is simple..."
    }) {
      documentId
      status
    }
  }
  ```
- **Response**:
  ```json
  {
    "data": {
      "createArticle": {
        "documentId": "art_3c8p4m1o",
        "status": "draft"
      }
    }
  }
  ```
