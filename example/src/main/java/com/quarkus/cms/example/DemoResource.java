package com.quarkus.cms.example;

import com.quarkus.cms.admin.api.service.ContentManagerService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo REST resource that showcases the Quarkus Headless CMS extension.
 * <p>
 * Provides educational endpoints demonstrating every major CMS feature:
 * content types, entries, relations, draft/publish lifecycle, and metadata.
 */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DemoResource {

    @Inject
    ContentManagerService contentManager;

    @Inject
    SchemaStorageService schemaStorage;

    private static final String LOCALE = "en";
    private static final long USER_ID = 1L;

    // ---------------------------------------------------------------
    // Overview & Content Type Metadata
    // ---------------------------------------------------------------

    /**
     * Dashboard overview: count of entries per content type.
     */
    @GET
    @Path("/overview")
    public Response overview() {
        List<ContentTypeDefinition> contentTypes = schemaStorage.getAllContentTypes();
        List<Map<String, Object>> typeSummaries = new ArrayList<>();

        for (ContentTypeDefinition ct : contentTypes) {
            long count = contentManager.countEntries(ct.getUid(), null, LOCALE);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("uid", ct.getUid());
            summary.put("displayName", ct.getDisplayName());
            summary.put("kind", ct.getKind().name());
            summary.put("singularName", ct.getSingularName());
            summary.put("pluralName", ct.getPluralName());
            summary.put("draftAndPublish", ct.isDraftAndPublish());
            summary.put("localized", ct.isLocalized());
            summary.put("fieldCount", ct.getFields().size());
            summary.put("relationCount", ct.getRelations().size());
            summary.put("entryCount", count);
            typeSummaries.add(summary);
        }

        return Response.ok(Map.of(
            "title", "Quarkus Headless CMS — Demo Overview",
            "description", "This demo showcases the CMS extension with sample content types, seeded content, and REST API interactions.",
            "contentTypes", typeSummaries,
            "totalContentTypes", contentTypes.size()
        )).build();
    }

    /**
     * List all registered content types with full schema details.
     */
    @GET
    @Path("/content-types")
    public Response listContentTypes() {
        List<ContentTypeDefinition> contentTypes = schemaStorage.getAllContentTypes();
        List<Map<String, Object>> result = contentTypes.stream()
            .map(this::describeContentType)
            .collect(Collectors.toList());
        return Response.ok(result).build();
    }

    /**
     * Get a single content type by UID with full schema details.
     */
    @GET
    @Path("/content-types/{uid}")
    public Response getContentType(@PathParam("uid") String uid) {
        ContentTypeDefinition ct = schemaStorage.getContentType(uid);
        if (ct == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Content type not found: " + uid))
                .build();
        }
        return Response.ok(describeContentType(ct)).build();
    }

    // ---------------------------------------------------------------
    // Articles CRUD
    // ---------------------------------------------------------------

    /**
     * List all articles, optionally filtered by status.
     */
    @GET
    @Path("/articles")
    public Response listArticles(@QueryParam("status") String status,
                                 @QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("pageSize") @DefaultValue("20") int pageSize) {
        List<CmsEntry> entries = contentManager.listEntries(
            "api::article.article", status, LOCALE, page, pageSize);
        long total = contentManager.countEntries("api::article.article", status, LOCALE);

        List<Map<String, Object>> enriched = entries.stream()
            .map(this::enrichArticle)
            .collect(Collectors.toList());

        return Response.ok(Map.of(
            "data", enriched,
            "total", total,
            "page", page,
            "pageSize", pageSize
        )).build();
    }

    /**
     * Get a single article by document ID with populated relations.
     */
    @GET
    @Path("/articles/{documentId}")
    public Response getArticle(@PathParam("documentId") String documentId) {
        CmsEntry entry = contentManager.getEntry(documentId, LOCALE);
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Article not found: " + documentId))
                .build();
        }
        return Response.ok(enrichArticle(entry)).build();
    }

    /**
     * Create a new article (draft).
     */
    @POST
    @Path("/articles")
    @Transactional
    public Response createArticle(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getOrDefault("data", body);

        try {
            CmsEntry entry = contentManager.createEntry("api::article.article", data, LOCALE, USER_ID);
            return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                    "message", "Article created as draft",
                    "documentId", entry.documentId,
                    "entry", enrichArticle(entry)
                ))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Update an article draft.
     */
    @PUT
    @Path("/articles/{documentId}")
    @Transactional
    public Response updateArticle(@PathParam("documentId") String documentId,
                                  Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getOrDefault("data", body);

        try {
            CmsEntry entry = contentManager.updateEntry(documentId, data, LOCALE, USER_ID);
            return Response.ok(Map.of(
                "message", "Article draft updated",
                "entry", enrichArticle(entry)
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Delete an article.
     */
    @DELETE
    @Path("/articles/{documentId}")
    @Transactional
    public Response deleteArticle(@PathParam("documentId") String documentId) {
        try {
            contentManager.deleteDocument(documentId);
            return Response.ok(Map.of("message", "Article deleted", "documentId", documentId))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    // ---------------------------------------------------------------
    // Draft/Publish Lifecycle
    // ---------------------------------------------------------------

    /**
     * Publish an article (draft → published).
     */
    @POST
    @Path("/articles/{documentId}/publish")
    @Transactional
    public Response publishArticle(@PathParam("documentId") String documentId) {
        try {
            CmsEntry published = contentManager.publishEntry(documentId, LOCALE, USER_ID);
            return Response.ok(Map.of(
                "message", "Article published",
                "versionNumber", published.versionNumber,
                "publishedAt", published.publishedAt,
                "entry", enrichArticle(published)
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Unpublish an article.
     */
    @POST
    @Path("/articles/{documentId}/unpublish")
    @Transactional
    public Response unpublishArticle(@PathParam("documentId") String documentId) {
        try {
            contentManager.unpublishEntry(documentId, LOCALE);
            return Response.ok(Map.of("message", "Article unpublished")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Discard the current draft of an article (keep published version).
     */
    @POST
    @Path("/articles/{documentId}/discard-draft")
    @Transactional
    public Response discardDraft(@PathParam("documentId") String documentId) {
        try {
            contentManager.discardDraft(documentId, LOCALE);
            return Response.ok(Map.of("message", "Draft discarded, published version preserved")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * List version history for an article.
     */
    @GET
    @Path("/articles/{documentId}/versions")
    public Response getVersions(@PathParam("documentId") String documentId) {
        try {
            List<CmsEntry> versions = contentManager.getVersions(documentId, LOCALE);
            List<Map<String, Object>> versionList = versions.stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("versionNumber", v.versionNumber);
                    m.put("status", v.status);
                    m.put("publishedAt", v.publishedAt);
                    m.put("createdAt", v.createdAt);
                    m.put("updatedAt", v.updatedAt);
                    m.put("title", v.data != null ? v.data.get("title") : null);
                    return m;
                })
                .collect(Collectors.toList());
            return Response.ok(Map.of("documentId", documentId, "versions", versionList)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    // ---------------------------------------------------------------
    // Authors & Categories (CRUD)
    // ---------------------------------------------------------------

    /**
     * List all authors.
     */
    @GET
    @Path("/authors")
    public Response listAuthors() {
        return listAll("api::author.author", "Author");
    }

    /**
     * Get a single author by document ID.
     */
    @GET
    @Path("/authors/{documentId}")
    public Response getAuthor(@PathParam("documentId") String documentId) {
        return getEntry(documentId, "api::author.author", "Author");
    }

    /**
     * List all categories.
     */
    @GET
    @Path("/categories")
    public Response listCategories() {
        return listAll("api::category.category", "Category");
    }

    /**
     * Get a single category by document ID.
     */
    @GET
    @Path("/categories/{documentId}")
    public Response getCategory(@PathParam("documentId") String documentId) {
        return getEntry(documentId, "api::category.category", "Category");
    }

    // ---------------------------------------------------------------
    // Single Types
    // ---------------------------------------------------------------

    /**
     * Get the homepage single-type entry.
     */
    @GET
    @Path("/homepage")
    public Response getHomepage() {
        CmsEntry entry = contentManager.getSingleTypeEntry("api::homepage.homepage", LOCALE);
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Homepage not configured"))
                .build();
        }
        return Response.ok(enrichEntryView(entry)).build();
    }

    /**
     * Get global settings.
     */
    @GET
    @Path("/settings")
    public Response getSettings() {
        CmsEntry entry = contentManager.getSingleTypeEntry("api::global.global", LOCALE);
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Global settings not configured"))
                .build();
        }
        return Response.ok(enrichEntryView(entry)).build();
    }

    // ---------------------------------------------------------------
    // Relation Management
    // ---------------------------------------------------------------

    /**
     * Get relations for an article (author + categories).
     */
    @GET
    @Path("/articles/{documentId}/relations")
    public Response getArticleRelations(@PathParam("documentId") String documentId) {
        var authorRelations = contentManager.findRelations(documentId, "author");
        var categoryRelations = contentManager.findRelations(documentId, "categories");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);

        // Resolve author
        if (!authorRelations.isEmpty()) {
            String authorDocId = authorRelations.get(0).targetDocumentId;
            CmsEntry author = contentManager.getEntry(authorDocId, LOCALE);
            result.put("author", author != null ? Map.of(
                "documentId", author.documentId,
                "name", author.data != null ? author.data.get("name") : null
            ) : null);
        } else {
            result.put("author", null);
        }

        // Resolve categories
        List<Map<String, Object>> resolvedCategories = new ArrayList<>();
        for (CmsRelation rel : categoryRelations) {
            CmsEntry cat = contentManager.getEntry(rel.targetDocumentId, LOCALE);
            if (cat != null) {
                resolvedCategories.add(Map.of(
                    "documentId", cat.documentId,
                    "name", cat.data != null ? cat.data.get("name") : null,
                    "slug", cat.data != null ? cat.data.get("slug") : null
                ));
            }
        }
        result.put("categories", resolvedCategories);

        return Response.ok(result).build();
    }

    /**
     * Attach a relation (author or category) to an article.
     */
    @POST
    @Path("/articles/{documentId}/relations")
    @Transactional
    public Response attachRelation(@PathParam("documentId") String documentId,
                                   Map<String, Object> body) {
        String fieldName = (String) body.get("fieldName");
        String targetDocId = (String) body.get("targetDocumentId");
        String targetType = (String) body.get("targetType");

        if (fieldName == null || targetDocId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "fieldName and targetDocumentId are required"))
                .build();
        }

        try {
            var rel = contentManager.attachRelation(
                documentId, "api::article.article",
                targetDocId, targetType != null ? targetType : "api::author.author",
                fieldName, 0);
            return Response.ok(Map.of(
                "message", "Relation attached",
                "relation", Map.of(
                    "id", rel.id,
                    "fieldName", rel.fieldName,
                    "sourceDocumentId", rel.sourceDocumentId,
                    "targetDocumentId", rel.targetDocumentId
                )
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Detach a relation from an article.
     */
    @DELETE
    @Path("/articles/{documentId}/relations")
    @Transactional
    public Response detachRelation(@PathParam("documentId") String documentId,
                                   @QueryParam("fieldName") String fieldName,
                                   @QueryParam("targetDocumentId") String targetDocumentId) {
        if (fieldName == null || targetDocumentId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "fieldName and targetDocumentId query params are required"))
                .build();
        }
        try {
            contentManager.detachRelation(documentId, fieldName, targetDocumentId);
            return Response.ok(Map.of("message", "Relation detached")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    /**
     * Search across all content types.
     */
    @GET
    @Path("/search")
    public Response search(@QueryParam("q") String query) {
        if (query == null || query.isBlank()) {
            return Response.ok(Map.of("results", List.of())).build();
        }

        // Simple search across multiple content types
        String q = query.toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();

        for (String ctUid : List.of("api::article.article", "api::author.author", "api::category.category")) {
            List<CmsEntry> entries = contentManager.listAllEntries(ctUid, LOCALE);
            for (CmsEntry entry : entries) {
                if (entry.data != null) {
                    boolean matches = entry.data.values().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(v -> v.toString().toLowerCase().contains(q));

                    if (matches) {
                        ContentTypeDefinition ct = schemaStorage.getContentType(ctUid);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("documentId", entry.documentId);
                        item.put("contentType", ctUid);
                        item.put("contentTypeDisplayName", ct != null ? ct.getDisplayName() : ctUid);
                        item.put("status", entry.status);
                        item.put("title", entry.data.get("title") != null ? entry.data.get("title")
                            : entry.data.get("name") != null ? entry.data.get("name")
                            : entry.data.get("heroTitle"));
                        results.add(item);
                    }
                }
            }
        }

        return Response.ok(Map.of(
            "query", query,
            "resultCount", results.size(),
            "results", results
        )).build();
    }

    // ---------------------------------------------------------------
    // Health
    // ---------------------------------------------------------------

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
            "status", "ok",
            "cms", "quarkus-headless-cms",
            "version", "1.0.0-SNAPSHOT",
            "contentTypes", schemaStorage.getAllContentTypes().size(),
            "components", schemaStorage.getAllComponents().size()
        )).build();
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private Map<String, Object> describeContentType(ContentTypeDefinition ct) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uid", ct.getUid());
        result.put("kind", ct.getKind().name());
        result.put("singularName", ct.getSingularName());
        result.put("pluralName", ct.getPluralName());
        result.put("displayName", ct.getDisplayName());
        result.put("description", ct.getDescription());
        result.put("draftAndPublish", ct.isDraftAndPublish());
        result.put("localized", ct.isLocalized());

        List<Map<String, Object>> fieldList = ct.getFields().stream()
            .map(this::describeField)
            .collect(Collectors.toList());
        result.put("fields", fieldList);

        List<Map<String, Object>> relationList = ct.getRelations().stream()
            .map(this::describeRelation)
            .collect(Collectors.toList());
        result.put("relations", relationList);

        if (!ct.getComponents().isEmpty()) {
            result.put("components", ct.getComponents());
        }
        if (!ct.getDynamicZones().isEmpty()) {
            result.put("dynamicZones", ct.getDynamicZones());
        }

        return result;
    }

    private Map<String, Object> describeField(FieldDefinition field) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", field.getName());
        m.put("type", field.getType().name());
        m.put("required", field.isRequired());
        m.put("unique", field.isUnique());
        if (field.getDefaultValue() != null) m.put("defaultValue", field.getDefaultValue());
        if (field.getMinLength() != null) m.put("minLength", field.getMinLength());
        if (field.getMaxLength() != null) m.put("maxLength", field.getMaxLength());
        if (field.getMin() != null) m.put("min", field.getMin());
        if (field.getMax() != null) m.put("max", field.getMax());
        if (field.getEnumValues() != null && !field.getEnumValues().isEmpty())
            m.put("enumValues", field.getEnumValues());
        if (field.getComponent() != null) m.put("component", field.getComponent());
        if (field.getAllowedComponents() != null && !field.getAllowedComponents().isEmpty())
            m.put("allowedComponents", field.getAllowedComponents());
        if (field.isRepeatable()) m.put("repeatable", true);
        return m;
    }

    private Map<String, Object> describeRelation(RelationDefinition rel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fieldName", rel.getFieldName());
        m.put("type", rel.getType().name());
        m.put("target", rel.getTarget());
        if (rel.getTargetAttribute() != null) m.put("targetAttribute", rel.getTargetAttribute());
        return m;
    }

    private Map<String, Object> enrichArticle(CmsEntry entry) {
        Map<String, Object> view = enrichEntryView(entry);

        // Resolve author relation
        var authorRels = contentManager.findRelations(entry.documentId, "author");
        if (!authorRels.isEmpty()) {
            CmsEntry author = contentManager.getEntry(authorRels.get(0).targetDocumentId, LOCALE);
            if (author != null && author.data != null) {
                view.put("author", Map.of(
                    "documentId", author.documentId,
                    "name", author.data.get("name"),
                    "email", author.data.get("email")
                ));
            }
        }

        // Resolve categories
        var catRels = contentManager.findRelations(entry.documentId, "categories");
        List<Map<String, Object>> resolvedCats = new ArrayList<>();
        for (CmsRelation rel : catRels) {
            CmsEntry cat = contentManager.getEntry(rel.targetDocumentId, LOCALE);
            if (cat != null && cat.data != null) {
                resolvedCats.add(Map.of(
                    "documentId", cat.documentId,
                    "name", cat.data.get("name"),
                    "slug", cat.data.get("slug")
                ));
            }
        }
        view.put("categories", resolvedCats);

        return view;
    }

    private Map<String, Object> enrichEntryView(CmsEntry entry) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("documentId", entry.documentId);
        view.put("id", entry.id);
        view.put("contentType", entry.contentType);
        view.put("locale", entry.locale);
        view.put("status", entry.status);
        view.put("versionNumber", entry.versionNumber);
        view.put("data", entry.data);
        view.put("createdAt", entry.createdAt);
        view.put("updatedAt", entry.updatedAt);
        view.put("publishedAt", entry.publishedAt);
        return view;
    }

    private Response getEntry(String documentId, String expectedType, String label) {
        CmsEntry entry = contentManager.getEntry(documentId, LOCALE);
        if (entry == null || !expectedType.equals(entry.contentType)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", label + " not found: " + documentId))
                .build();
        }
        return Response.ok(enrichEntryView(entry)).build();
    }

    private Response listAll(String contentType, String label) {
        List<CmsEntry> entries = contentManager.listAllEntries(contentType, LOCALE);
        long count = contentManager.countEntries(contentType, null, LOCALE);
        List<Map<String, Object>> result = entries.stream()
            .map(this::enrichEntryView)
            .collect(Collectors.toList());
        return Response.ok(Map.of(
            "data", result,
            "total", count
        )).build();
    }
}
