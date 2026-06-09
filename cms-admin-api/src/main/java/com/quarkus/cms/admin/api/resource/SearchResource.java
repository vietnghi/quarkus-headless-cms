package com.quarkus.cms.admin.api.resource;

import com.quarkus.cms.admin.api.service.SearchService;
import com.quarkus.cms.core.security.PermissionCheck;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource providing global search across all CMS content types and media files.
 *
 * <p>This endpoint is used by the admin UI search bar to find entries and media
 * across the entire CMS instance. Results are paginated (max 20 per query) and
 * include a navigation URL for each result.
 *
 * <h3>Endpoint:</h3>
 * <ul>
 *   <li>{@code GET /admin/search?q={query}} — global search</li>
 *   <li>{@code GET /admin/search?q={query}&contentType={ct}} — filtered by content type</li>
 *   <li>{@code GET /admin/search?q={query}&locale={locale}} — filtered by locale</li>
 * </ul>
 *
 * <h3>Response:</h3>
 * <pre>{@code
 * {
 *   "results": [{
 *     "contentType": "api::article.article" | "media",
 *     "entryId": 42,
 *     "documentId": "abc-123-def",
 *     "title": "My Article Title",
 *     "excerpt": "This is a snippet of the content...",
 *     "url": "/admin/content-manager/collection-types/api::article.article/abc-123-def"
 *   }],
 *   "total": 5
 * }
 * }</pre>
 *
 * Requires the {@code admin::search.read} permission. Rate limiting is applied
 * automatically by {@link com.quarkus.cms.admin.api.filter.AdminRateLimitingFilter}.
 */
@Path("/admin/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    @Inject
    SearchService searchService;

    /**
     * Performs a global search across content entries and media files.
     *
     * @param query       the search query (required)
     * @param contentType optional — filter results to a specific content-type UID
     * @param locale      optional — filter results to a specific locale
     * @param page        zero-based page index (default: 0)
     * @param pageSize    results per page (default: 10, max: 20)
     * @return a paginated list of search results
     */
    @GET
    @PermissionCheck("admin::search.read")
    public Response search(
            @QueryParam("q") String query,
            @QueryParam("contentType") String contentType,
            @QueryParam("locale") String locale,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize) {

        if (query == null || query.isBlank()) {
            return Response.ok(new SearchResponse(java.util.List.of(), 0)).build();
        }

        SearchResponse response = searchService.search(query, contentType, locale, page, pageSize);
        return Response.ok(response).build();
    }
}
