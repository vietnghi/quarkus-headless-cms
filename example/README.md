# Quarkus Headless CMS — Example Application

A comprehensive demonstration application showcasing the Quarkus Headless CMS extension with sample content types, seeded demo data, and a showcase REST API.

## What's Demonstrated

This demo application showcases the following CMS features:

- **Schema-Driven Content Types** — 5 content types (Article, Author, Category, Homepage, Global Settings) defined as JSON schema files + 2 reusable components (SEO, Media)
- **18 Field Types** — STRING, TEXT, INTEGER, BOOLEAN, EMAIL, UID, RICHTEXT, JSON, MEDIA, and more
- **Draft/Publish Workflow** — Create drafts, publish, unpublish, discard drafts, version history
- **Content Relations** — Many-to-one (Article → Author), Many-to-many (Article ↔ Category) with inverse side
- **Single-Type Management** — Homepage and Global Settings as singleton content entries
- **Dynamic Content Queries** — List, filter by status, paginate, search across content types
- **Schema Introspection** — List and inspect registered content types with full field metadata

## Quick Start

```bash
# Clone and build the parent project
cd quarkus-headless-cms
mvn clean install -DskipTests

# Run the example in dev mode
cd example
mvn quarkus:dev
```

The application starts on `http://localhost:8080`.

## Demo API Endpoints

All demo endpoints are under `/demo/` and serve JSON responses.

### Overview & Discovery

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/demo/health` | Health check with module counts |
| `GET` | `/demo/overview` | Dashboard overview with counts per content type |
| `GET` | `/demo/content-types` | List all registered content types with schemas |
| `GET` | `/demo/content-types/{uid}` | Get a single content type definition |

### Articles

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/demo/articles` | List articles (optional: `?status=draft\|published&page=0&pageSize=20`) |
| `GET` | `/demo/articles/{documentId}` | Get single article with populated relations |
| `POST` | `/demo/articles` | Create article as draft |
| `PUT` | `/demo/articles/{documentId}` | Update article draft |
| `DELETE` | `/demo/articles/{documentId}` | Delete article and all versions |

### Draft/Publish Lifecycle

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/demo/articles/{documentId}/publish` | Publish draft → versioned snapshot |
| `POST` | `/demo/articles/{documentId}/unpublish` | Unpublish current version |
| `POST` | `/demo/articles/{documentId}/discard-draft` | Discard draft, keep published |
| `GET` | `/demo/articles/{documentId}/versions` | List version history |

### Relations

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/demo/articles/{documentId}/relations` | Get populated author and categories |
| `POST` | `/demo/articles/{documentId}/relations` | Attach relation (author or category) |
| `DELETE` | `/demo/articles/{documentId}/relations` | Detach relation |

### Other Content Types

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/demo/authors` | List all authors |
| `GET` | `/demo/authors/{documentId}` | Get a single author |
| `GET` | `/demo/categories` | List all categories |
| `GET` | `/demo/categories/{documentId}` | Get a single category |
| `GET` | `/demo/homepage` | Get homepage single-type entry |
| `GET` | `/demo/settings` | Get global settings single-type entry |
| `GET` | `/demo/search?q=term` | Search across all content types |

## Seeded Demo Data

On first startup, the `DemoDataSeeder` creates:

- **2 Authors**: Alice Johnson (tech writer), Bob Smith (full-stack developer)
- **3 Categories**: Technology, Design, Science
- **4 Articles**: 3 published, 1 draft — with author and category relations
- **Homepage**: Single-type entry with hero section
- **Global Settings**: Site name, description, social links

The seeder is idempotent — it only runs if no authors exist in the database.

## Architecture

```
example/src/main/
├── java/com/quarkus/cms/example/
│   ├── DemoResource.java      # Showcase REST API (27+ endpoints)
│   ├── DemoDataSeeder.java    # Startup data seeder
│   └── ExampleResource.java   # Legacy health endpoint
├── resources/
│   ├── application.properties # App configuration (H2 in-memory DB)
│   └── schemas/              # Content-type JSON schema definitions
│       ├── articles.json      # api::article.article
│       ├── authors.json       # api::author.author
│       ├── categories.json    # api::category.category
│       ├── homepage.json      # api::homepage.homepage
│       ├── settings.json      # api::global.global
│       └── basic/             # Reusable component schemas
│           ├── seo.json       # SEO metadata
│           └── media.json     # Media with caption
```

## Running Tests

```bash
# From the project root, run all tests
mvn test

# Run only the example module tests
mvn test -pl example

# Run a specific test class
mvn test -pl example -Dtest=DemoResourceTest
```

## Building as a Standalone Application

```bash
cd example
mvn clean package -DskipTests
# Run the JAR directly:
java -jar target/quarkus-app/quarkus-run.jar
```

## Using the API

### Create a new article:

```bash
curl -X POST http://localhost:8080/demo/articles \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "title": "My New Article",
      "slug": "my-new-article",
      "excerpt": "A short description",
      "body": "# Article body\n\nContent here..."
    }
  }'
```

### Publish an article:

```bash
curl -X POST http://localhost:8080/demo/articles/{documentId}/publish
```

### Search content:

```bash
curl "http://localhost:8080/demo/search?q=Quarkus"
```

## Content-Type Schema Format

Content types are defined as JSON files in `src/main/resources/schemas/`. Each file follows this structure:

```json
{
  "uid": "api::article.article",
  "kind": "COLLECTION_TYPE",
  "singularName": "article",
  "pluralName": "articles",
  "displayName": "Article",
  "description": "Blog article content type",
  "draftAndPublish": true,
  "fields": [
    { "name": "title", "type": "STRING", "required": true },
    { "name": "body", "type": "RICHTEXT" }
  ],
  "relations": [
    {
      "fieldName": "author",
      "type": "MANY_TO_ONE",
      "target": "api::author.author",
      "targetAttribute": "articles"
    }
  ],
  "components": ["basic.seo"]
}
```

Components go in subdirectories matching their category:

```json
// schemas/basic/seo.json
{
  "uid": "basic.seo",
  "category": "basic",
  "displayName": "SEO",
  "fields": [
    { "name": "metaTitle", "type": "STRING", "maxLength": 70 }
  ]
}
```
