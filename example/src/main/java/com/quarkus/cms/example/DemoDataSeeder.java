package com.quarkus.cms.example;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.builder.ContentTypeBuilder;
import com.quarkus.cms.core.schema.model.*;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.annotation.Priority;

import java.util.List;
import java.util.Map;

/**
 * Seeds the CMS with demonstrative sample data on application startup.
 * <p>
 * Registers all demo content types and components programmatically, then
 * creates sample content entries with relations.
 * <p>
 * Content types are registered in two passes to handle circular relation
 * dependencies (e.g., Article ↔ Author → Article):
 * <ol>
 *   <li>Register all content types without cross-referencing relations</li>
 *   <li>Update each content type to add relations</li>
 * </ol>
 */
@ApplicationScoped
public class DemoDataSeeder {

    @Inject
    SchemaStorageService schemaStorage;

    @Inject
    ContentManagerService contentManager;

    private static final String LOCALE = "en";
    private static final long USER_ID = 1L;

    void seed(@Observes @Priority(3000) StartupEvent event) {
        // Only seed if no content types exist yet (idempotent)
        if (schemaStorage.getAllContentTypes().isEmpty()) {
            Log.info("Seeding demo schema definitions and data...");
            try {
                registerComponents();
                registerContentTypesPass1();
                registerContentTypesPass2();
                seedDemoData();
                Log.info("Demo data seeded successfully");
            } catch (Exception e) {
                Log.errorf("Failed to seed demo data: %s", e.getMessage());
                for (StackTraceElement el : e.getStackTrace()) {
                    Log.errorf("  at %s", el.toString());
                }
            }
        } else {
            Log.info("Demo data already exists, skipping seed");
        }
    }

    // ---------------------------------------------------------------
    // Components
    // ---------------------------------------------------------------

    @Transactional
    void registerComponents() {
        schemaStorage.registerComponent(
            ComponentDefinition.builder("basic.seo")
                .category("basic")
                .displayName("SEO")
                .description("Search engine optimization metadata")
                .fields(List.of(
                    FieldDefinition.builder("metaTitle", FieldType.STRING).maxLength(70).build(),
                    FieldDefinition.builder("metaDescription", FieldType.TEXT).maxLength(160).build(),
                    FieldDefinition.builder("keywords", FieldType.STRING).maxLength(255).build()
                ))
                .build(),
            "Demo schema", "demo-seeder");

        schemaStorage.registerComponent(
            ComponentDefinition.builder("basic.media")
                .category("basic")
                .displayName("Media")
                .description("Image or video with caption and alt text")
                .fields(List.of(
                    FieldDefinition.builder("mediaUrl", FieldType.STRING).required(true).build(),
                    FieldDefinition.builder("altText", FieldType.STRING).maxLength(255).build(),
                    FieldDefinition.builder("caption", FieldType.STRING).maxLength(500).build()
                ))
                .build(),
            "Demo schema", "demo-seeder");

        Log.info("Registered 2 demo components");
    }

    // ---------------------------------------------------------------
    // Content Types — Pass 1 (no cross-referencing relations)
    // ---------------------------------------------------------------

    @Transactional
    void registerContentTypesPass1() {
        // Category — no cross-references to other content types
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::category.category", ContentTypeKind.COLLECTION_TYPE)
                .singularName("category")
                .pluralName("categories")
                .displayName("Category")
                .description("Content categorization taxonomy")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("description", FieldType.TEXT).build())
                .build(),
            "Demo schema", "demo-seeder");

        // Author — no cross-references to other content types in Pass 1
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::author.author", ContentTypeKind.COLLECTION_TYPE)
                .singularName("author")
                .pluralName("authors")
                .displayName("Author")
                .description("Content author profile")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).build())
                .addField(FieldDefinition.builder("email", FieldType.EMAIL).build())
                .addField(FieldDefinition.builder("bio", FieldType.RICHTEXT).build())
                .build(),
            "Demo schema", "demo-seeder");

        // Homepage single type — only references components, not other CTs
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::homepage.homepage", ContentTypeKind.SINGLE_TYPE)
                .singularName("homepage")
                .pluralName("homepages")
                .displayName("Homepage")
                .description("Homepage content managed as a single entry")
                .draftAndPublish(true)
                .localized(false)
                .addField(FieldDefinition.builder("heroTitle", FieldType.STRING).required(true).maxLength(200).build())
                .addField(FieldDefinition.builder("heroSubtitle", FieldType.STRING).maxLength(300).build())
                .addField(FieldDefinition.builder("aboutText", FieldType.RICHTEXT).build())
                .build(),
            "Demo schema", "demo-seeder");

        // Global settings single type — no references
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::global.global", ContentTypeKind.SINGLE_TYPE)
                .singularName("setting")
                .pluralName("settings")
                .displayName("Global Settings")
                .description("Global site-wide configuration")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("siteName", FieldType.STRING).required(true).defaultValue("My CMS Site").build())
                .addField(FieldDefinition.builder("siteDescription", FieldType.TEXT).build())
                .addField(FieldDefinition.builder("socialLinks", FieldType.JSON).build())
                .addField(FieldDefinition.builder("footerText", FieldType.RICHTEXT).build())
                .build(),
            "Demo schema", "demo-seeder");

        Log.info("Pass 1: registered 4 content types (without relations)");
    }

    // ---------------------------------------------------------------
    // Content Types — Pass 2 (add cross-referencing relations)
    // ---------------------------------------------------------------

    @Transactional
    void registerContentTypesPass2() {
        // Article — now depends on Author and Category which exist from Pass 1
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::article.article", ContentTypeKind.COLLECTION_TYPE)
                .singularName("article")
                .pluralName("articles")
                .displayName("Article")
                .description("Blog article with rich content and relations")
                .draftAndPublish(true)
                .localized(false)
                .addField(FieldDefinition.builder("title", FieldType.STRING).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("excerpt", FieldType.TEXT).maxLength(500).build())
                .addField(FieldDefinition.builder("body", FieldType.RICHTEXT).build())
                .addField(FieldDefinition.builder("featured", FieldType.BOOLEAN).defaultValue("false").build())
                .addField(FieldDefinition.builder("coverImage", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("publishedAt", FieldType.DATETIME).build())
                .addRelation(RelationDefinition.builder("author", RelationType.MANY_TO_ONE, "api::author.author")
                    .targetAttribute("articles").build())
                .addRelation(RelationDefinition.builder("categories", RelationType.MANY_TO_MANY, "api::category.category")
                    .targetAttribute("articles").build())
                .build(),
            "Demo schema", "demo-seeder");

        // Update Author to add the articles relation (now Article exists)
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::author.author", ContentTypeKind.COLLECTION_TYPE)
                .singularName("author")
                .pluralName("authors")
                .displayName("Author")
                .description("Content author profile")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).build())
                .addField(FieldDefinition.builder("email", FieldType.EMAIL).build())
                .addField(FieldDefinition.builder("bio", FieldType.RICHTEXT).build())
                .addRelation(RelationDefinition.builder("articles", RelationType.ONE_TO_MANY, "api::article.article")
                    .targetAttribute("author").build())
                .build(),
            "Demo schema (updated with relations)", "demo-seeder");

        // Update Category to add article relation
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::category.category", ContentTypeKind.COLLECTION_TYPE)
                .singularName("category")
                .pluralName("categories")
                .displayName("Category")
                .description("Content categorization taxonomy")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("description", FieldType.TEXT).build())
                .addRelation(RelationDefinition.builder("articles", RelationType.MANY_TO_MANY, "api::article.article")
                    .targetAttribute("categories").build())
                .build(),
            "Demo schema (updated with relations)", "demo-seeder");

        Log.info("Pass 2: registered Article, updated Author and Category with relations");
    }

    // ---------------------------------------------------------------
    // Demo Data
    // ---------------------------------------------------------------

    @Transactional
    void seedDemoData() {
        seedAuthors();
        seedCategories();
        seedArticles();
        seedHomepage();
        seedGlobalSettings();
    }

    void seedAuthors() {
        CmsEntry alice = contentManager.createEntry("api::author.author",
            Map.of("name", "Alice Johnson", "email", "alice@example.com",
                "bio", "Senior technical writer and software engineer with 10+ years of experience."),
            LOCALE, USER_ID);

        CmsEntry bob = contentManager.createEntry("api::author.author",
            Map.of("name", "Bob Smith", "email", "bob@example.com",
                "bio", "Full-stack developer and content creator focusing on web technologies and design."),
            LOCALE, USER_ID);

        Log.infof("Seeded 2 authors");
    }

    void seedCategories() {
        contentManager.createEntry("api::category.category",
            Map.of("name", "Technology", "slug", "technology",
                "description", "Articles about software, hardware, and emerging tech trends."),
            LOCALE, USER_ID);

        contentManager.createEntry("api::category.category",
            Map.of("name", "Design", "slug", "design",
                "description", "UI/UX design, visual design, and creative processes."),
            LOCALE, USER_ID);

        contentManager.createEntry("api::category.category",
            Map.of("name", "Science", "slug", "science",
                "description", "Scientific discoveries, research, and breakthroughs."),
            LOCALE, USER_ID);

        Log.infof("Seeded 3 categories");
    }

    void seedArticles() {
        List<CmsEntry> authors = contentManager.listAllEntries("api::author.author", LOCALE);
        List<CmsEntry> categories = contentManager.listAllEntries("api::category.category", LOCALE);

        String aliceDocId = authors.size() > 0 ? authors.get(0).documentId : null;
        String bobDocId = authors.size() > 1 ? authors.get(1).documentId : null;
        String techCatId = categories.size() > 0 ? categories.get(0).documentId : null;
        String designCatId = categories.size() > 1 ? categories.get(1).documentId : null;
        String scienceCatId = categories.size() > 2 ? categories.get(2).documentId : null;

        // Article 1: Published, by Alice, in Technology
        CmsEntry a1 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Getting Started with Quarkus Headless CMS",
            "slug", "getting-started-quarkus-headless-cms",
            "excerpt", "Learn how to build a modern content management system with Quarkus.",
            "body", "## Introduction\n\nQuarkus Headless CMS provides a powerful, extensible platform for managing structured content. Built on Quarkus, it offers fast startup times, low memory footprint, and a rich set of features including draft/publish workflows, content versioning, and schema-driven content types.\n\n## Key Features\n\n- **Schema-Driven Content Types**: Define your content structure using JSON schemas or the Java builder API.\n- **Draft/Publish Workflow**: Create drafts, publish them, and manage version history.\n- **Dynamic Relations**: Establish relationships between content types without DDL changes.\n- **RESTful Admin API**: Full CRUD operations for content entries and content type definitions.",
            "featured", true), LOCALE, USER_ID);
        contentManager.publishEntry(a1.documentId, LOCALE, USER_ID);
        if (aliceDocId != null) {
            contentManager.attachRelation(a1.documentId, "api::article.article",
                aliceDocId, "api::author.author", "author", 0);
        }
        if (techCatId != null) {
            contentManager.attachRelation(a1.documentId, "api::article.article",
                techCatId, "api::category.category", "categories", 0);
        }

        // Article 2: Published, by Bob, in Design + Technology
        CmsEntry a2 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Designing Flexible Content Schemas for Modern Websites",
            "slug", "designing-flexible-content-schemas",
            "excerpt", "Best practices for designing content schemas that adapt to changing requirements.",
            "body", "## Why Schema Design Matters\n\nA well-designed content schema is the foundation of a successful headless CMS implementation. It determines how flexible, maintainable, and performant your content management system will be.\n\n## Principles\n\n1. **Start Simple**: Begin with the minimum fields you need and add more as requirements emerge.\n2. **Use Components**: Break reusable field groups into components for consistency.\n3. **Leverage Relations**: Model real-world relationships between content types.",
            "featured", true), LOCALE, USER_ID);
        contentManager.publishEntry(a2.documentId, LOCALE, USER_ID);
        if (bobDocId != null) {
            contentManager.attachRelation(a2.documentId, "api::article.article",
                bobDocId, "api::author.author", "author", 0);
        }
        if (designCatId != null) {
            contentManager.attachRelation(a2.documentId, "api::article.article",
                designCatId, "api::category.category", "categories", 0);
        }
        if (techCatId != null) {
            contentManager.attachRelation(a2.documentId, "api::article.article",
                techCatId, "api::category.category", "categories", 1);
        }

        // Article 3: Draft only, by Alice, in Science
        CmsEntry a3 = contentManager.createEntry("api::article.article", Map.of(
            "title", "The Future of Quantum Computing in Content Management",
            "slug", "future-quantum-computing-cms",
            "excerpt", "Exploring how quantum computing could transform content delivery.",
            "body", "## A Bold New Frontier\n\nQuantum computing promises to revolutionize many fields, and content management is no exception. With the ability to process vast amounts of data in parallel, quantum algorithms could enable unprecedented levels of content personalization.",
            "featured", false), LOCALE, USER_ID);
        if (aliceDocId != null) {
            contentManager.attachRelation(a3.documentId, "api::article.article",
                aliceDocId, "api::author.author", "author", 0);
        }
        if (scienceCatId != null) {
            contentManager.attachRelation(a3.documentId, "api::article.article",
                scienceCatId, "api::category.category", "categories", 0);
        }

        // Article 4: Published, by Bob, in Science
        CmsEntry a4 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Building Scalable APIs with Quarkus: A Practical Guide",
            "slug", "building-scalable-apis-quarkus",
            "excerpt", "Learn how to build high-performance, scalable REST APIs using Quarkus.",
            "body", "## Why Quarkus for APIs?\n\nQuarkus is a Kubernetes-native Java framework tailored for GraalVM and HotSpot. It offers blazing fast startup times and low RSS memory, making it ideal for microservices and serverless architectures.",
            "featured", true), LOCALE, USER_ID);
        contentManager.publishEntry(a4.documentId, LOCALE, USER_ID);
        if (bobDocId != null) {
            contentManager.attachRelation(a4.documentId, "api::article.article",
                bobDocId, "api::author.author", "author", 0);
        }
        if (scienceCatId != null) {
            contentManager.attachRelation(a4.documentId, "api::article.article",
                scienceCatId, "api::category.category", "categories", 0);
        }

        Log.infof("Seeded 4 articles with relations");
    }

    void seedHomepage() {
        contentManager.upsertSingleType("api::homepage.homepage", Map.of(
            "heroTitle", "Welcome to the Quarkus CMS Demo",
            "heroSubtitle", "A powerful, extensible headless content management system built on Quarkus",
            "aboutText", "## About This Demo\n\nThis application showcases the capabilities of the Quarkus Headless CMS extension. It demonstrates schema-driven content types, draft/publish workflows, content relations, and REST API-driven content management."
        ), LOCALE, USER_ID);
        Log.info("Seeded homepage");
    }

    void seedGlobalSettings() {
        contentManager.upsertSingleType("api::global.global", Map.of(
            "siteName", "Quarkus CMS Demo",
            "siteDescription", "A demonstration of the Quarkus Headless CMS extension with sample content types and API-driven content management.",
            "socialLinks", Map.of(
                "github", "https://github.com/quarkus-cms",
                "twitter", "https://twitter.com/quarkus-cms",
                "linkedin", "https://linkedin.com/company/quarkus-cms"
            ),
            "footerText", "Built with Quarkus Headless CMS \u00a9 2026"
        ), LOCALE, USER_ID);
        Log.info("Seeded global settings");
    }
}
