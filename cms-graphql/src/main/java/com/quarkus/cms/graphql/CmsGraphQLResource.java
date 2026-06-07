package com.quarkus.cms.graphql;

import com.quarkus.cms.graphql.model.AuthPayload;
import com.quarkus.cms.graphql.model.AuthPayload.UserInfo;
import com.quarkus.cms.graphql.model.Entry;
import com.quarkus.cms.graphql.model.EntryCollection;
import com.quarkus.cms.graphql.model.PaginationInput;
import com.quarkus.cms.graphql.model.SchemaTypes;
import com.quarkus.cms.graphql.service.GraphQLAuthService;
import com.quarkus.cms.graphql.service.GraphQLContentService;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Main GraphQL API for the Quarkus Headless CMS.
 *
 * <p>Provides content queries and mutations matching Strapi v5 GraphQL plugin capabilities,
 * schema introspection, authentication, and localization support.
 *
 * <p>All content operations share the same service layer as the REST API.
 */
@GraphQLApi
public class CmsGraphQLResource {

  @Inject GraphQLContentService contentService;

  @Inject GraphQLAuthService authService;

  @Inject SecurityIdentity securityIdentity;

  // ================================================================
  // Content Queries
  // ================================================================

  @Query("entries")
  @Description("Find many entries for a content type with filters, sort, pagination, and locale support")
  @PermitAll
  public EntryCollection entries(
      @Name("contentType") @Description("Content type UID (e.g. api::article.article)") String contentType,
      @Name("filters") @Description("Filter conditions as a JSON object matching Strapi filter syntax") String filters,
      @Name("sort") @Description("Sort specification: field:asc/desc or array of field:direction") String sort,
      @Name("pagination") @Description("Pagination parameters (page, pageSize)") PaginationInput pagination,
      @Name("locale") @Description("Locale code (e.g. en, fr). Defaults to system default.") String locale,
      @Name("status") @Description("Status filter: 'published' (default) or 'draft'") @DefaultValue("published") String status) {
    return contentService.findMany(contentType, filters, sort, pagination, locale, status);
  }

  @Query("entry")
  @Description("Find a single entry by document ID")
  @PermitAll
  public Entry entry(
      @Name("contentType") @Description("Content type UID") String contentType,
      @Name("documentId") @Description("Document ID (UUID)") String documentId,
      @Name("locale") @Description("Locale code") String locale,
      @Name("status") @Description("Status filter") String status) {
    return contentService.findOne(contentType, documentId, locale, status);
  }

  // ================================================================
  // Schema Introspection
  // ================================================================

  @Query("contentTypes")
  @Description("List all registered content type schemas with their fields, relations, and zones")
  @PermitAll
  public List<SchemaTypes.ContentTypeSchema> contentTypes() {
    return contentService.getContentTypes();
  }

  @Query("contentType")
  @Description("Get a single content type schema by UID")
  @PermitAll
  public SchemaTypes.ContentTypeSchema contentType(
      @Name("uid") @Description("Content type UID") String uid) {
    return contentService.getContentType(uid);
  }

  @Query("components")
  @Description("List all registered component schemas")
  @PermitAll
  public List<SchemaTypes.ComponentSchema> components() {
    return contentService.getComponents();
  }

  @Query("component")
  @Description("Get a single component schema by UID")
  @PermitAll
  public SchemaTypes.ComponentSchema component(
      @Name("uid") @Description("Component UID") String uid) {
    return contentService.getComponent(uid);
  }

  @Query("locales")
  @Description("List all configured locales")
  @PermitAll
  public List<SchemaTypes.LocaleInfo> locales() {
    return contentService.getLocales();
  }

  // ================================================================
  // Auth / User Queries
  // ================================================================

  @Query("me")
  @Description("Returns the currently authenticated user's profile")
  @RolesAllowed({"authenticated", "admin"})
  public UserInfo me() {
    if (securityIdentity == null || securityIdentity.isAnonymous()) {
      return null;
    }
    String username = securityIdentity.getPrincipal().getName();
    Long userId = Long.valueOf(securityIdentity.getPrincipal().getName());
    // Fallback: try to extract from JWT claims
    Map<String, Object> attributes = securityIdentity.getAttributes();
    if (attributes != null && attributes.containsKey("sub")) {
      userId = Long.valueOf(attributes.get("sub").toString());
    }
    return new UserInfo(userId, username, "", securityIdentity.getRoles());
  }

  // ================================================================
  // Content Mutations
  // ================================================================

  @Mutation("createEntry")
  @Description("Create a new content entry in draft status")
  @RolesAllowed({"authenticated", "admin"})
  public Entry createEntry(
      @Name("contentType") @Description("Content type UID") String contentType,
      @Name("data") @Description("Content field data as a JSON string") String data,
      @Name("locale") @Description("Locale code") String locale) {
    Long userId = extractUserId();
    Map<String, Object> dataMap = parseJsonData(data);
    return contentService.create(contentType, dataMap, locale, userId);
  }

  @Mutation("updateEntry")
  @Description("Update an existing draft entry")
  @RolesAllowed({"authenticated", "admin"})
  public Entry updateEntry(
      @Name("contentType") @Description("Content type UID") String contentType,
      @Name("documentId") @Description("Document ID") String documentId,
      @Name("data") @Description("Updated content field data as a JSON string") String data) {
    Map<String, Object> dataMap = parseJsonData(data);
    return contentService.update(documentId, dataMap);
  }

  @Mutation("deleteEntry")
  @Description("Permanently delete an entry (all versions)")
  @RolesAllowed({"authenticated", "admin"})
  public boolean deleteEntry(
      @Name("contentType") @Description("Content type UID") String contentType,
      @Name("documentId") @Description("Document ID") String documentId) {
    return contentService.delete(documentId);
  }

  @Mutation("publishEntry")
  @Description("Publish a draft entry to make it publicly visible")
  @RolesAllowed({"authenticated", "admin"})
  public Entry publishEntry(
      @Name("contentType") @Description("Content type UID") String contentType,
      @Name("documentId") @Description("Document ID") String documentId) {
    Long userId = extractUserId();
    return contentService.publish(documentId, userId);
  }

  @Mutation("unpublishEntry")
  @Description("Unpublish an entry (removes the published version, draft remains)")
  @RolesAllowed({"authenticated", "admin"})
  public boolean unpublishEntry(
      @Name("contentType") @Description("Content type UID") String contentType,
      @Name("documentId") @Description("Document ID") String documentId) {
    return contentService.unpublish(documentId);
  }

  // ================================================================
  // Lifecycle Mutations
  // ================================================================

  @Mutation("createLocalization")
  @Description("Create a locale variant (translation) of an existing entry")
  @RolesAllowed({"authenticated", "admin"})
  public Entry createLocalization(
      @Name("documentId") @Description("Document ID of the source entry") String documentId,
      @Name("sourceLocale") @Description("Source locale code") String sourceLocale,
      @Name("targetLocale") @Description("Target locale code for the new translation") String targetLocale,
      @Name("data") @Description("Locale-specific field data as a JSON string") String data) {
    Long userId = extractUserId();
    Map<String, Object> localizedData = parseJsonData(data);
    return contentService.createLocalization(
        documentId, sourceLocale, targetLocale, localizedData, userId);
  }

  // ================================================================
  // Auth Mutations
  // ================================================================

  @Mutation("login")
  @Description("Authenticate a user by username/email and password. Returns a JWT access token.")
  @PermitAll
  public AuthPayload login(
      @Name("identifier") @Description("Username or email") String identifier,
      @Name("password") @Description("Password") String password) {
    return authService.login(identifier, password);
  }

  // ================================================================
  // DataLoader: Relation Resolution (batch loading via @Source)
  // ================================================================

  /**
   * Batch-resolves relations for entries queried from the same parent query.
   *
   * <p>SmallRye GraphQL calls this method once with all entries returned by a parent
   * {@code entries()} or {@code entry()} query, enabling batch relation loading and
   * preventing the N+1 query problem.
   *
   * @param entries   the list of source entries (populated by SmallRye GraphQL)
   * @param fieldName the relation field name to resolve
   * @return list of target entries for each source entry, preserving order
   */
  @Description("Resolve related entries for a relation field (batch-loaded for N+1 prevention)")
  @PermitAll
  public List<List<Entry>> resolveRelations(
      @Source List<Entry> entries,
      @Name("relation") @Description("Relation field name to resolve") String fieldName) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    Map<String, List<Entry>> relationMap =
        contentService.resolveRelations(entries, fieldName);
    return entries.stream()
        .map(e -> relationMap.getOrDefault(e.getDocumentId(), List.of()))
        .toList();
  }

  // ================================================================
  // Helpers
  // ================================================================

  /** Extracts the user ID from the current security context, or null if anonymous. */
  private Long extractUserId() {
    try {
      if (securityIdentity != null && !securityIdentity.isAnonymous()) {
        Object subObj = securityIdentity.getAttributes().get("sub");
        if (subObj != null) {
          return Long.valueOf(subObj.toString());
        }
      }
    } catch (Exception e) {
      Log.debug("Could not extract user ID from security context");
    }
    return null;
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Parses a JSON string into a Map, or returns an empty Map on null/error. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJsonData(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, Map.class);
    } catch (Exception e) {
      Log.warnf("Failed to parse JSON data: %s", e.getMessage());
      return Map.of();
    }
  }
}
