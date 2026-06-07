package com.quarkus.cms.samplecontent;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.schema.builder.ContentTypeBuilder;
import com.quarkus.cms.core.schema.model.*;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.ConfigProvider;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Seeds the CMS with sample content types, components, and data entries on application startup.
 *
 * <p>This seeder is independent of the {@code example} module's {@code DemoDataSeeder} and
 * provides a richer set of sample types including Tags, hierarchical Categories, and more
 * detailed field definitions.
 *
 * <p>Content types are registered in two passes to handle circular relation dependencies:
 * <ol>
 *   <li>Register all content types without cross-referencing relations</li>
 *   <li>Update each content type to add relations</li>
 * </ol>
 *
 * <p>To disable this seeder, set {@code cms.sample-content.enabled=false} in application.properties.
 */
@ApplicationScoped
public class SampleContentSeeder {

    @Inject
    SchemaStorageService schemaStorage;

    @Inject
    ContentManagerService contentManager;

    private static final String LOCALE = "en";
    private static final long USER_ID = 1L;

    void seed(@Observes @Priority(2000) StartupEvent event) {
        if (!isEnabled()) {
            Log.info("Sample content seeder disabled via cms.sample-content.enabled=false");
            return;
        }

        // Only seed if no sample content types exist yet (idempotent)
        if (schemaStorage.getContentType("api::article.article") == null) {
            Log.info("Seeding sample schema definitions and data...");
            try {
                registerComponents();
                registerContentTypesPass1();
                registerContentTypesPass2();
                seedDemoData();
                Log.info("Sample data seeded successfully");
            } catch (Exception e) {
                Log.errorf("Failed to seed sample data: %s", e.getMessage());
                for (StackTraceElement el : e.getStackTrace()) {
                    Log.errorf("  at %s", el.toString());
                }
            }
        } else {
            Log.info("Sample content types already exist, skipping seed");
        }
    }

    private boolean isEnabled() {
        // Check quarkus config, fall back to system property for backward compatibility
        try {
            var config = ConfigProvider.getConfig();
            return config.getOptionalValue("cms.sample-content.enabled", Boolean.class).orElse(true);
        } catch (Exception e) {
            String val = System.getProperty("cms.sample-content.enabled", "true");
            return !"false".equalsIgnoreCase(val);
        }
    }

    // ---------------------------------------------------------------
    // Components
    // ---------------------------------------------------------------

    @Transactional
    void registerComponents() {
        schemaStorage.registerComponent(
            ComponentDefinition.builder("shared.seo")
                .category("shared")
                .displayName("SEO")
                .description("Search engine optimization metadata")
                .fields(List.of(
                    FieldDefinition.builder("metaTitle", FieldType.STRING).maxLength(70).build(),
                    FieldDefinition.builder("metaDescription", FieldType.TEXT).maxLength(160).build(),
                    FieldDefinition.builder("keywords", FieldType.TEXT).build(),
                    FieldDefinition.builder("ogImage", FieldType.MEDIA).build(),
                    FieldDefinition.builder("ogTitle", FieldType.STRING).maxLength(70).build(),
                    FieldDefinition.builder("ogDescription", FieldType.TEXT).maxLength(160).build(),
                    FieldDefinition.builder("noIndex", FieldType.BOOLEAN).defaultValue("false").build(),
                    FieldDefinition.builder("canonicalUrl", FieldType.STRING).maxLength(500).build()
                ))
                .build(),
            "Sample schema", "sample-seeder");

        schemaStorage.registerComponent(
            ComponentDefinition.builder("shared.media")
                .category("shared")
                .displayName("Media")
                .description("Rich media attachment with caption, alt text, dimensions, and focal point")
                .fields(List.of(
                    FieldDefinition.builder("mediaUrl", FieldType.STRING).required(true).build(),
                    FieldDefinition.builder("altText", FieldType.STRING).maxLength(255).build(),
                    FieldDefinition.builder("caption", FieldType.STRING).maxLength(500).build(),
                    FieldDefinition.builder("width", FieldType.INTEGER).min(0).build(),
                    FieldDefinition.builder("height", FieldType.INTEGER).min(0).build(),
                    FieldDefinition.builder("mimeType", FieldType.STRING).maxLength(100).build(),
                    FieldDefinition.builder("fileSize", FieldType.INTEGER).min(0).build(),
                    FieldDefinition.builder("focalPointX", FieldType.FLOAT).defaultValue("0.5").build(),
                    FieldDefinition.builder("focalPointY", FieldType.FLOAT).defaultValue("0.5").build()
                ))
                .build(),
            "Sample schema", "sample-seeder");

        schemaStorage.registerComponent(
            ComponentDefinition.builder("shared.related-links")
                .category("shared")
                .displayName("Related Links")
                .description("A set of related external or internal links with labels")
                .fields(List.of(
                    FieldDefinition.builder("label", FieldType.STRING).required(true).maxLength(100).build(),
                    FieldDefinition.builder("url", FieldType.STRING).required(true).maxLength(500).build(),
                    FieldDefinition.builder("description", FieldType.STRING).maxLength(300).build(),
                    FieldDefinition.builder("newWindow", FieldType.BOOLEAN).defaultValue("true").build(),
                    FieldDefinition.builder("sortOrder", FieldType.INTEGER).defaultValue("0").build()
                ))
                .build(),
            "Sample schema", "sample-seeder");
    }

    // ---------------------------------------------------------------
    // Content Types — Pass 1 (no cross-referencing relations)
    // ---------------------------------------------------------------

    @Transactional
    void registerContentTypesPass1() {
        // Tag — no cross-references (articles are added in Pass 2)
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::tag.tag", ContentTypeKind.COLLECTION_TYPE)
                .singularName("tag")
                .pluralName("tags")
                .displayName("Tag")
                .description("Lightweight content tagging — many-to-many with articles")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(100).unique(true).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(120).build())
                .addField(FieldDefinition.builder("color", FieldType.STRING).maxLength(7).defaultValue("#6b7280").regex("^#[0-9a-fA-F]{6}$").build())
                .build(),
            "Sample schema", "sample-seeder");

        // Category — no cross-references (self-referential parent relation added in Pass 2)
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::category.category", ContentTypeKind.COLLECTION_TYPE)
                .singularName("category")
                .pluralName("categories")
                .displayName("Category")
                .description("Content categorization taxonomy — grouped articles by topic area")
                .draftAndPublish(false)
                .localized(true)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).localized(true).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("description", FieldType.TEXT).localized(true).build())
                .addField(FieldDefinition.builder("color", FieldType.STRING).maxLength(7).defaultValue("#6366f1").regex("^#[0-9a-fA-F]{6}$").build())
                .addField(FieldDefinition.builder("icon", FieldType.STRING).maxLength(50).build())
                .addField(FieldDefinition.builder("sortOrder", FieldType.INTEGER).defaultValue("0").build())
                .build(),
            "Sample schema", "sample-seeder");

        // Author — no cross-references (articles added in Pass 2)
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::author.author", ContentTypeKind.COLLECTION_TYPE)
                .singularName("author")
                .pluralName("authors")
                .displayName("Author")
                .description("Content author/contributor profile with social links and bio")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("email", FieldType.EMAIL).build())
                .addField(FieldDefinition.builder("jobTitle", FieldType.STRING).maxLength(150).build())
                .addField(FieldDefinition.builder("bio", FieldType.RICHTEXT).build())
                .addField(FieldDefinition.builder("avatar", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("socialLinks", FieldType.JSON).build())
                .addField(FieldDefinition.builder("featuredAuthor", FieldType.BOOLEAN).defaultValue("false").build())
                .build(),
            "Sample schema", "sample-seeder");

        // Homepage single type — only references components, not other CTs
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::homepage.homepage", ContentTypeKind.SINGLE_TYPE)
                .singularName("homepage")
                .pluralName("homepages")
                .displayName("Homepage")
                .description("Homepage content managed as a single entry with dynamic sections")
                .draftAndPublish(true)
                .localized(true)
                .addField(FieldDefinition.builder("heroTitle", FieldType.STRING).required(true).maxLength(200).localized(true).build())
                .addField(FieldDefinition.builder("heroSubtitle", FieldType.STRING).maxLength(300).localized(true).build())
                .addField(FieldDefinition.builder("heroCtaText", FieldType.STRING).maxLength(50).build())
                .addField(FieldDefinition.builder("heroCtaUrl", FieldType.STRING).maxLength(500).build())
                .addField(FieldDefinition.builder("heroImage", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("aboutTitle", FieldType.STRING).maxLength(200).localized(true).build())
                .addField(FieldDefinition.builder("aboutText", FieldType.RICHTEXT).localized(true).build())
                .addField(FieldDefinition.builder("featuredArticleIds", FieldType.JSON).build())
                .build(),
            "Sample schema", "sample-seeder");

        // Global settings single type — no references
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::global.global", ContentTypeKind.SINGLE_TYPE)
                .singularName("setting")
                .pluralName("settings")
                .displayName("Global Settings")
                .description("Global site-wide configuration: branding, SEO defaults, social links, and operational settings")
                .draftAndPublish(false)
                .localized(true)
                .addField(FieldDefinition.builder("siteName", FieldType.STRING).required(true).maxLength(100).defaultValue("My CMS Site").localized(true).build())
                .addField(FieldDefinition.builder("siteDescription", FieldType.TEXT).maxLength(300).localized(true).build())
                .addField(FieldDefinition.builder("logo", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("favicon", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("ogDefaultImage", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("socialLinks", FieldType.JSON).build())
                .addField(FieldDefinition.builder("contactEmail", FieldType.EMAIL).build())
                .addField(FieldDefinition.builder("footerText", FieldType.RICHTEXT).localized(true).build())
                .addField(FieldDefinition.builder("customHeadHtml", FieldType.TEXT).build())
                .addField(FieldDefinition.builder("customFooterHtml", FieldType.TEXT).build())
                .build(),
            "Sample schema", "sample-seeder");

        Log.info("Pass 1: registered 5 content types (without relations)");
    }

    // ---------------------------------------------------------------
    // Content Types — Pass 2 (add cross-referencing relations)
    // ---------------------------------------------------------------

    @Transactional
    void registerContentTypesPass2() {
        // Article — depends on Author, Category, and Tag (all from Pass 1)
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::article.article", ContentTypeKind.COLLECTION_TYPE)
                .singularName("article")
                .pluralName("articles")
                .displayName("Article")
                .description("Rich blog article with SEO metadata, tags, and related content support")
                .draftAndPublish(true)
                .localized(true)
                .addField(FieldDefinition.builder("title", FieldType.STRING).required(true).maxLength(255).localized(true).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("subtitle", FieldType.STRING).maxLength(300).localized(true).build())
                .addField(FieldDefinition.builder("excerpt", FieldType.TEXT).maxLength(500).localized(true).build())
                .addField(FieldDefinition.builder("body", FieldType.RICHTEXT).localized(true).build())
                .addField(FieldDefinition.builder("featured", FieldType.BOOLEAN).defaultValue("false").build())
                .addField(FieldDefinition.builder("coverImage", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("readingTime", FieldType.INTEGER).min(1).max(120).build())
                .addField(FieldDefinition.builder("publishedAt", FieldType.DATETIME).build())
                .addField(FieldDefinition.builder("seo", FieldType.COMPONENT).component("shared.seo").build())
                .addRelation(RelationDefinition.builder("author", RelationType.MANY_TO_ONE, "api::author.author")
                    .targetAttribute("articles").build())
                .addRelation(RelationDefinition.builder("categories", RelationType.MANY_TO_MANY, "api::category.category")
                    .targetAttribute("articles").joinTable("articles_categories").build())
                .addRelation(RelationDefinition.builder("tags", RelationType.MANY_TO_MANY, "api::tag.tag")
                    .targetAttribute("articles").joinTable("articles_tags").build())
                .addComponent("shared.seo")
                .build(),
            "Sample schema", "sample-seeder");

        // Update Author with articles relation
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::author.author", ContentTypeKind.COLLECTION_TYPE)
                .singularName("author")
                .pluralName("authors")
                .displayName("Author")
                .description("Content author/contributor profile with social links and bio")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("email", FieldType.EMAIL).build())
                .addField(FieldDefinition.builder("jobTitle", FieldType.STRING).maxLength(150).build())
                .addField(FieldDefinition.builder("bio", FieldType.RICHTEXT).build())
                .addField(FieldDefinition.builder("avatar", FieldType.MEDIA).build())
                .addField(FieldDefinition.builder("socialLinks", FieldType.JSON).build())
                .addField(FieldDefinition.builder("featuredAuthor", FieldType.BOOLEAN).defaultValue("false").build())
                .addRelation(RelationDefinition.builder("articles", RelationType.ONE_TO_MANY, "api::article.article")
                    .targetAttribute("author").build())
                .build(),
            "Sample schema (updated)", "sample-seeder");

        // Update Category with articles + self-referential parent relation
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::category.category", ContentTypeKind.COLLECTION_TYPE)
                .singularName("category")
                .pluralName("categories")
                .displayName("Category")
                .description("Content categorization taxonomy — grouped articles by topic area")
                .draftAndPublish(false)
                .localized(true)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(200).localized(true).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(255).build())
                .addField(FieldDefinition.builder("description", FieldType.TEXT).localized(true).build())
                .addField(FieldDefinition.builder("color", FieldType.STRING).maxLength(7).defaultValue("#6366f1").regex("^#[0-9a-fA-F]{6}$").build())
                .addField(FieldDefinition.builder("icon", FieldType.STRING).maxLength(50).build())
                .addField(FieldDefinition.builder("sortOrder", FieldType.INTEGER).defaultValue("0").build())
                .addRelation(RelationDefinition.builder("articles", RelationType.MANY_TO_MANY, "api::article.article")
                    .targetAttribute("categories").joinTable("articles_categories").build())
                .addRelation(RelationDefinition.builder("parentCategory", RelationType.MANY_TO_ONE, "api::category.category")
                    .targetAttribute("subcategories").build())
                .addRelation(RelationDefinition.builder("subcategories", RelationType.ONE_TO_MANY, "api::category.category")
                    .targetAttribute("parentCategory").build())
                .build(),
            "Sample schema (updated)", "sample-seeder");

        // Update Tag with articles relation
        schemaStorage.registerContentType(
            ContentTypeBuilder.create("api::tag.tag", ContentTypeKind.COLLECTION_TYPE)
                .singularName("tag")
                .pluralName("tags")
                .displayName("Tag")
                .description("Lightweight content tagging — many-to-many with articles")
                .draftAndPublish(false)
                .localized(false)
                .addField(FieldDefinition.builder("name", FieldType.STRING).required(true).maxLength(100).unique(true).build())
                .addField(FieldDefinition.builder("slug", FieldType.UID).required(true).maxLength(120).build())
                .addField(FieldDefinition.builder("color", FieldType.STRING).maxLength(7).defaultValue("#6b7280").regex("^#[0-9a-fA-F]{6}$").build())
                .addRelation(RelationDefinition.builder("articles", RelationType.MANY_TO_MANY, "api::article.article")
                    .targetAttribute("tags").joinTable("articles_tags").build())
                .build(),
            "Sample schema (updated)", "sample-seeder");

        Log.info("Pass 2: registered Article, updated Author, Category, and Tag with relations");
    }

    // ---------------------------------------------------------------
    // Sample Data
    // ---------------------------------------------------------------

    @Transactional
    void seedDemoData() {
        seedTags();
        seedCategories();
        seedAuthors();
        seedArticles();
        seedHomepage();
        seedGlobalSettings();
    }

    void seedTags() {
        contentManager.createEntry("api::tag.tag",
            Map.of("name", "quarkus", "slug", "quarkus", "color", "#4695EB"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "java", "slug", "java", "color", "#ED8B00"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "headless-cms", "slug", "headless-cms", "color", "#6366f1"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "tutorial", "slug", "tutorial", "color", "#10b981"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "graphql", "slug", "graphql", "color", "#E535AB"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "rest-api", "slug", "rest-api", "color", "#2563eb"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "architecture", "slug", "architecture", "color", "#8b5cf6"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "performance", "slug", "performance", "color", "#f59e0b"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "ai", "slug", "ai", "color", "#ef4444"), LOCALE, USER_ID);

        contentManager.createEntry("api::tag.tag",
            Map.of("name", "design-systems", "slug", "design-systems", "color", "#ec4899"), LOCALE, USER_ID);

        Log.infof("Seeded 10 tags");
    }

    void seedCategories() {
        contentManager.createEntry("api::category.category",
            Map.of("name", "Technology", "slug", "technology",
                "description", "Articles about software engineering, hardware, and emerging tech trends.",
                "color", "#6366f1", "icon", "laptop", "sortOrder", 1),
            LOCALE, USER_ID);

        contentManager.createEntry("api::category.category",
            Map.of("name", "Design", "slug", "design",
                "description", "UI/UX design, design systems, typography, and creative processes.",
                "color", "#ec4899", "icon", "palette", "sortOrder", 2),
            LOCALE, USER_ID);

        contentManager.createEntry("api::category.category",
            Map.of("name", "Science", "slug", "science",
                "description", "Scientific discoveries, research, and breakthroughs.",
                "color", "#10b981", "icon", "flask-conical", "sortOrder", 3),
            LOCALE, USER_ID);

        contentManager.createEntry("api::category.category",
            Map.of("name", "DevOps", "slug", "devops",
                "description", "CI/CD, containers, IaC, monitoring, and SRE.",
                "color", "#f59e0b", "icon", "container", "sortOrder", 4),
            LOCALE, USER_ID);

        contentManager.createEntry("api::category.category",
            Map.of("name", "Open Source", "slug", "open-source",
                "description", "Open source software, communities, and collaborative development.",
                "color", "#8b5cf6", "icon", "github", "sortOrder", 5),
            LOCALE, USER_ID);

        Log.infof("Seeded 5 categories");
    }

    void seedAuthors() {
        CmsEntry alice = contentManager.createEntry("api::author.author",
            Map.of("name", "Alice Johnson", "slug", "alice-johnson",
                "email", "alice@example.com",
                "jobTitle", "Senior Technical Writer",
                "bio", "Alice is a senior technical writer and software engineer with over a decade of experience in developer documentation and content strategy.",
                "featuredAuthor", true,
                "socialLinks", Map.of(
                    "twitter", "@alicejohnson",
                    "github", "alicejohnson",
                    "linkedin", "https://linkedin.com/in/alicejohnson")),
            LOCALE, USER_ID);

        CmsEntry bob = contentManager.createEntry("api::author.author",
            Map.of("name", "Bob Smith", "slug", "bob-smith",
                "email", "bob@example.com",
                "jobTitle", "Full-Stack Developer",
                "bio", "Bob is a full-stack developer and content creator focusing on web technologies and user experience.",
                "featuredAuthor", false,
                "socialLinks", Map.of(
                    "twitter", "@bobsmith",
                    "github", "bobsmithdev",
                    "website", "https://bobsmith.dev")),
            LOCALE, USER_ID);

        CmsEntry maria = contentManager.createEntry("api::author.author",
            Map.of("name", "Dr. Maria Garcia", "slug", "maria-garcia",
                "email", "maria@example.com",
                "jobTitle", "Research Scientist & Science Communicator",
                "bio", "Maria holds a PhD in Computer Science and is passionate about making scientific research accessible.",
                "featuredAuthor", true,
                "socialLinks", Map.of(
                    "twitter", "@mariagarcia",
                    "linkedin", "https://linkedin.com/in/mariagarcia")),
            LOCALE, USER_ID);

        CmsEntry james = contentManager.createEntry("api::author.author",
            Map.of("name", "James Wilson", "slug", "james-wilson",
                "email", "james@example.com",
                "jobTitle", "UX Designer & Design Systems Lead",
                "bio", "James is a UX designer passionate about design systems, accessibility, and human-centered design.",
                "featuredAuthor", false,
                "socialLinks", Map.of(
                    "twitter", "@jameswilson",
                    "github", "jameswilsonux",
                    "website", "https://jameswilson.design")),
            LOCALE, USER_ID);

        Log.infof("Seeded 4 authors");
    }

    @SuppressWarnings("unchecked")
    void seedArticles() {
        List<CmsEntry> authors = contentManager.listAllEntries("api::author.author", LOCALE);
        List<CmsEntry> categories = contentManager.listAllEntries("api::category.category", LOCALE);
        List<CmsEntry> tags = contentManager.listAllEntries("api::tag.tag", LOCALE);

        String aliceDocId = findAuthor(authors, "alice-johnson");
        String bobDocId = findAuthor(authors, "bob-smith");
        String mariaDocId = findAuthor(authors, "maria-garcia");
        String jamesDocId = findAuthor(authors, "james-wilson");

        String techCatId = findCategory(categories, "technology");
        String designCatId = findCategory(categories, "design");
        String scienceCatId = findCategory(categories, "science");
        String devopsCatId = findCategory(categories, "devops");
        String ossCatId = findCategory(categories, "open-source");

        String quarkusTagId = findTag(tags, "quarkus");
        String javaTagId = findTag(tags, "java");
        String cmsTagId = findTag(tags, "headless-cms");
        String tutorialTagId = findTag(tags, "tutorial");
        String graphqlTagId = findTag(tags, "graphql");
        String restTagId = findTag(tags, "rest-api");
        String archTagId = findTag(tags, "architecture");
        String perfTagId = findTag(tags, "performance");
        String aiTagId = findTag(tags, "ai");
        String dsTagId = findTag(tags, "design-systems");

        // Article 1: Getting Started with Quarkus Headless CMS
        CmsEntry a1 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Getting Started with Quarkus Headless CMS",
            "slug", "getting-started-quarkus-headless-cms",
            "subtitle", "Build a modern content management system on Kubernetes-native Java",
            "excerpt", "Learn how to build a powerful, extensible headless CMS using Quarkus, Hibernate Panache, and PostgreSQL JSONB.",
            "body", "## Introduction\n\nQuarkus Headless CMS provides a powerful, extensible platform for managing structured content. Built on Quarkus, it offers fast startup times, low memory footprint, and a rich set of features including draft/publish workflows, content versioning, and schema-driven content types.\n\n## Key Features\n\n- **Schema-Driven Content Types**: Define your content structure using JSON schemas or the Java builder API.\n- **Draft/Publish Workflow**: Create drafts, publish them, and manage version history.\n- **Dynamic Relations**: Establish relationships between content types without DDL changes.\n- **RESTful and GraphQL APIs**: Access your content through both REST endpoints and GraphQL queries.\n\n## Architecture Overview\n\nThe CMS uses a hybrid document-on-RDBMS model. Standard metadata fields (document ID, content type, locale, status, timestamps) are stored as physical columns, while custom fields are serialized into a JSONB column. This enables dynamic schemas without requiring DDL changes.",
            "featured", true,
            "readingTime", 8,
            "publishedAt", "2026-01-15T10:00:00Z"), LOCALE, USER_ID);
        contentManager.publishEntry(a1.documentId, LOCALE, USER_ID);
        attachAuthor(a1, aliceDocId);
        attachCategories(a1, techCatId);
        attachTags(a1, quarkusTagId, javaTagId, cmsTagId, tutorialTagId);

        // Article 2: Designing Flexible Content Schemas
        CmsEntry a2 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Designing Flexible Content Schemas for Modern Websites",
            "slug", "designing-flexible-content-schemas",
            "subtitle", "Best practices for schema design that adapts to changing requirements",
            "excerpt", "A well-designed content schema is the foundation of a successful headless CMS implementation.",
            "body", "## Why Schema Design Matters\n\nA well-designed content schema is the foundation of a successful headless CMS implementation. It determines how flexible, maintainable, and performant your content management system will be.\n\n## Key Principles\n\n### 1. Start Simple\nBegin with the minimum fields you need and add more as requirements emerge.\n\n### 2. Use Components\nBreak reusable field groups into components for consistency.\n\n### 3. Leverage Relations\nModel real-world relationships between content types with appropriate cardinality.\n\n### 4. Plan for Localization\nDesign for localization from day one if your content needs to reach a global audience.\n\n### 5. Use JSON for Complex Structures\nFor truly dynamic data like social links and configuration, use the JSON field type.",
            "featured", true,
            "readingTime", 12,
            "publishedAt", "2026-02-03T14:30:00Z"), LOCALE, USER_ID);
        contentManager.publishEntry(a2.documentId, LOCALE, USER_ID);
        attachAuthor(a2, bobDocId);
        attachCategories(a2, designCatId, techCatId);
        attachTags(a2, cmsTagId, archTagId, dsTagId);

        // Article 3: The Future of Quantum Computing
        CmsEntry a3 = contentManager.createEntry("api::article.article", Map.of(
            "title", "The Future of Quantum Computing in Content Management",
            "slug", "future-quantum-computing-cms",
            "subtitle", "How quantum algorithms could transform content delivery and personalization",
            "excerpt", "Exploring the potential impact of quantum computing on content management systems.",
            "body", "## A Bold New Frontier\n\nQuantum computing promises to revolutionize many fields, and content management is no exception. With the ability to process vast amounts of data in parallel, quantum algorithms could enable unprecedented levels of content personalization and recommendation.\n\n## Potential Applications\n\n1. **Hyper-Personalized Content**: Quantum ML could analyze user behavior across millions of dimensions simultaneously.\n2. **Optimal Content Assembly**: Quantum optimization could determine the optimal arrangement of content components.\n3. **Natural Language Understanding**: Quantum NLP models could process context beyond classical capabilities.\n\n## Current Limitations\n\nPractical quantum computing for content management is still years away. However, research is progressing rapidly.",
            "featured", false,
            "readingTime", 7), LOCALE, USER_ID);
        attachAuthor(a3, mariaDocId);
        attachCategories(a3, scienceCatId);
        attachTags(a3, aiTagId, archTagId);

        // Article 4: Building Scalable APIs with Quarkus
        CmsEntry a4 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Building Scalable APIs with Quarkus: A Practical Guide",
            "slug", "building-scalable-apis-quarkus",
            "subtitle", "High-performance REST and GraphQL APIs with Kubernetes-native Java",
            "excerpt", "Learn how to build high-performance, scalable APIs using Quarkus.",
            "body", "## Why Quarkus for APIs?\n\nQuarkus is a Kubernetes-native Java framework tailored for GraalVM and HotSpot. It offers blazing fast startup times and low RSS memory, making it ideal for microservices and serverless architectures.\n\n## Key Performance Features\n\n- **Fast Boot Time**: Sub-second startup\n- **Low Memory Footprint**: Typically 1/10th of traditional Java frameworks\n- **Native Compilation**: Compile to native binary with GraalVM\n\n## REST API Example\n\n```java\n@Path(\"/articles\")\n@Produces(MediaType.APPLICATION_JSON)\npublic class ArticleResource {\n    @Inject ContentManagerService contentManager;\n\n    @GET\n    public Response listArticles() { ... }\n}\n```",
            "featured", true,
            "readingTime", 15,
            "publishedAt", "2026-03-10T09:00:00Z"), LOCALE, USER_ID);
        contentManager.publishEntry(a4.documentId, LOCALE, USER_ID);
        attachAuthor(a4, bobDocId);
        attachCategories(a4, techCatId, devopsCatId);
        attachTags(a4, quarkusTagId, javaTagId, restTagId, perfTagId);

        // Article 5: Understanding Draft/Publish Workflows
        CmsEntry a5 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Understanding Draft/Publish Workflows in Content Management",
            "slug", "understanding-draft-publish-workflows",
            "subtitle", "Versioning, publishing, and content lifecycle management explained",
            "excerpt", "Explore how draft/publish workflows enable content teams to collaborate effectively.",
            "body", "## The Content Lifecycle\n\nContent doesn't go from creation to publication in one step. It goes through a lifecycle — drafted, reviewed, published, and sometimes archived.\n\n## Draft State\n\nWhen content is first created, it's in a draft state. Drafts are visible only to content editors.\n\n## Published State\n\nWhen a draft is published, it becomes visible via the public API. The CMS maintains both versions simultaneously.\n\n## Version History\n\nEvery publish creates a new version. The CMS keeps a complete version history.",
            "featured", false,
            "readingTime", 6,
            "publishedAt", "2026-03-22T11:00:00Z"), LOCALE, USER_ID);
        contentManager.publishEntry(a5.documentId, LOCALE, USER_ID);
        attachAuthor(a5, aliceDocId);
        attachCategories(a5, techCatId);
        attachTags(a5, cmsTagId, tutorialTagId);

        // Article 6: Content Localization Strategies
        CmsEntry a6 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Content Localization Strategies for Global Audiences",
            "slug", "content-localization-strategies-global-audiences",
            "subtitle", "Reach readers worldwide with effective i18n and l10n approaches",
            "excerpt", "Strategies and best practices for managing multi-language content in a headless CMS.",
            "body", "## Why Localization Matters\n\nIn today's connected world, reaching a global audience requires more than just translation. True localization adapts your content to cultural contexts.\n\n## The CMS Localization Model\n\nThe CMS supports locale per entry through the locale column on every content entry.\n\n## Best Practices\n\n1. Start with locale-ready schemas\n2. Use a localization management platform\n3. Test with real users in each target locale",
            "featured", false,
            "readingTime", 10,
            "publishedAt", "2026-04-05T08:00:00Z"), LOCALE, USER_ID);
        contentManager.publishEntry(a6.documentId, LOCALE, USER_ID);
        attachAuthor(a6, mariaDocId);
        attachCategories(a6, techCatId);
        attachTags(a6, cmsTagId, archTagId, tutorialTagId);

        // Article 7: GraphQL vs REST
        CmsEntry a7 = contentManager.createEntry("api::article.article", Map.of(
            "title", "GraphQL vs REST: Choosing the Right API for Your CMS",
            "slug", "graphql-vs-rest-cms-api",
            "subtitle", "Comparing two API paradigms for content delivery and management",
            "excerpt", "A practical comparison of GraphQL and REST APIs for headless CMS.",
            "body", "## The API Debate\n\nWhen building a headless CMS, one of the first architectural decisions is choosing your API paradigm.\n\n## REST API\n\nSimple URL structure, easy HTTP caching, widely understood.\n\n## GraphQL API\n\nRequest exactly the data you need, single endpoint, strongly typed schema.\n\n## Our Recommendation\n\nThe CMS supports both REST and GraphQL APIs. Use REST for simple CRUD, GraphQL for complex frontend queries.",
            "featured", true,
            "readingTime", 9,
            "publishedAt", "2026-04-20T16:00:00Z"), LOCALE, USER_ID);
        contentManager.publishEntry(a7.documentId, LOCALE, USER_ID);
        attachAuthor(a7, jamesDocId);
        attachCategories(a7, designCatId, techCatId);
        attachTags(a7, graphqlTagId, restTagId, archTagId);

        // Article 8: Setting Up CI/CD
        CmsEntry a8 = contentManager.createEntry("api::article.article", Map.of(
            "title", "Setting Up CI/CD for Your Headless CMS Project",
            "slug", "cicd-headless-cms-project",
            "subtitle", "Automate testing and deployment of your content infrastructure",
            "excerpt", "Learn how to set up continuous integration and delivery for a Quarkus-based CMS.",
            "body", "## Why CI/CD Matters\n\nAutomated testing and deployment is essential for maintaining a healthy CMS project.\n\n## Test Structure\n\n- Unit Tests: Test individual components\n- Integration Tests: Test the full Quarkus application\n- Contract Tests: Verify API contracts\n\n## Running Tests\n\n```bash\nmvn test -Pdev -pl cms-core,cms-graphql\n```\n\n## Deployment Pipeline\n\nBuild -> Test -> Containerize -> Push -> Deploy -> Smoke Test",
            "featured", false,
            "readingTime", 8,
            "publishedAt", "2026-05-01T12:00:00Z"), LOCALE, USER_ID);
        contentManager.publishEntry(a8.documentId, LOCALE, USER_ID);
        attachAuthor(a8, bobDocId);
        attachCategories(a8, devopsCatId, ossCatId);
        attachTags(a8, quarkusTagId, javaTagId, tutorialTagId);

        Log.infof("Seeded 8 articles with relations");
    }

    void seedHomepage() {
        contentManager.upsertSingleType("api::homepage.homepage", Map.of(
            "heroTitle", "Welcome to the Quarkus CMS Demo",
            "heroSubtitle", "A powerful, extensible headless content management system built on Quarkus",
            "heroCtaText", "Get Started",
            "heroCtaUrl", "/docs",
            "aboutTitle", "About This Demo",
            "aboutText", "## About This Demo\n\nThis application showcases the capabilities of the Quarkus Headless CMS extension. It demonstrates schema-driven content types, draft/publish workflows, content relations, and API-driven content management.\n\nThe sample content includes articles, authors, categories, and tags — all linked together through relations and searchable via both REST and GraphQL APIs."
        ), LOCALE, USER_ID);
        Log.info("Seeded homepage");
    }

    void seedGlobalSettings() {
        contentManager.upsertSingleType("api::global.global", Map.of(
            "siteName", "Quarkus CMS Demo",
            "siteDescription", "A demonstration of the Quarkus Headless CMS extension with sample content and API-driven management.",
            "socialLinks", Map.of(
                "github", "https://github.com/quarkus-cms",
                "twitter", "https://twitter.com/quarkus-cms",
                "linkedin", "https://linkedin.com/company/quarkus-cms",
                "youtube", "https://youtube.com/@quarkus-cms"
            ),
            "contactEmail", "hello@quarkus-cms.dev",
            "footerText", "Built with Quarkus Headless CMS \u00a9 2026. All rights reserved.",
            "customHeadHtml", "<meta name=\"generator\" content=\"Quarkus Headless CMS\" />"
        ), LOCALE, USER_ID);
        Log.info("Seeded global settings");
    }

    // ---- Helper methods ----

    private String findAuthor(List<CmsEntry> authors, String slug) {
        for (CmsEntry a : authors) {
            if (a.data != null && slug.equals(a.data.get("slug"))) {
                return a.documentId;
            }
        }
        return null;
    }

    private String findCategory(List<CmsEntry> categories, String slug) {
        for (CmsEntry c : categories) {
            if (c.data != null && slug.equals(c.data.get("slug"))) {
                return c.documentId;
            }
        }
        return null;
    }

    private String findTag(List<CmsEntry> tags, String slug) {
        for (CmsEntry t : tags) {
            if (t.data != null && slug.equals(t.data.get("slug"))) {
                return t.documentId;
            }
        }
        return null;
    }

    private void attachAuthor(CmsEntry article, String authorDocId) {
        if (authorDocId != null) {
            contentManager.attachRelation(article.documentId, "api::article.article",
                authorDocId, "api::author.author", "author", 0);
        }
    }

    private void attachCategories(CmsEntry article, String... categoryDocIds) {
        int order = 0;
        for (String catId : categoryDocIds) {
            if (catId != null) {
                contentManager.attachRelation(article.documentId, "api::article.article",
                    catId, "api::category.category", "categories", order++);
            }
        }
    }

    private void attachTags(CmsEntry article, String... tagDocIds) {
        int order = 0;
        for (String tagId : tagDocIds) {
            if (tagId != null) {
                contentManager.attachRelation(article.documentId, "api::article.article",
                    tagId, "api::tag.tag", "tags", order++);
            }
        }
    }
}
