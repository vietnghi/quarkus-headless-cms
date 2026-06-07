# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0-SNAPSHOT] - 2026-06-07

### Added
- **Project Structure Scaffold**: Set up a multi-module Maven parent aggregator project containing 17 modules mapping the dynamic headless capabilities of Strapi v5 to Quarkus.
- **Static Schema Database Migrations**: Added PostgreSQL database migrations utilizing `quarkus-liquibase`. Included baseline tables: `cms_entries` (utilizing `JSONB` for content storage), `cms_relations` (relation adjacency listing), `cms_assets` (media library uploads metadata), `cms_users`, and `cms_sys_permissions`.
- **Dynamic Content Modeling Schema parsing**: Implemented `CmsSchemaRegistry.java` which parses schema definition JSON files located under `/schemas` on startup using Jackson Databind.
- **Dynamic CRUD Services**: Integrated Hibernate Panache ORM and built a core document service for standard query operations, localization-aware selections, and publication-isolation pipelines.
- **Query Param Compiler**: Added an intelligent query compiler that parses URL structures (such as `filters[name][$eq]=val` or multi-level sorting) and translates them into high-performance SQL/JSONB Postgres statements.
- **Admin Authentication**: Added a JAX-RS Cookie JWT Session filter mapping logged-in administrative sessions dynamically using SmallRye JWT.
- **API Token Security**: Added dynamic Bearer API Token authorization checking client requests against policies loaded dynamically from `cms_api_tokens`.
- **Qute Template Administration Panel**: Scaffolded a server-side rendered admin UI utilizing Quarkus Qute, HTMX, and Tailwind CSS.
- **Asset Upload Management**: Added file uploading functionality including MIME-type verification with Apache Tika and resizing using Thumbnailator.
- **GraphQL Integration**: Added dynamic, schema-driven GraphQL schema generation using SmallRye GraphQL.
- **Example & Integration Test Apps**: Built a sample application showing extension usage, accompanied by complete endpoint test coverages.
- **Comprehensive Documentation**: Authored `README.md`, `API_DOCUMENTATION.md`, `DEVELOPER_GUIDE.md`, and this `CHANGELOG.md`.
