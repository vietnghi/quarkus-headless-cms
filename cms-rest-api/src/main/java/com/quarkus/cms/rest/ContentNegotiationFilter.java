package com.quarkus.cms.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

/**
 * Ensures proper Content-Type negotiation for JSON:API and regular JSON responses.
 *
 * <p>When a request includes an {@code Accept} header of {@code application/vnd.api+json}
 * (the JSON:API media type), this filter ensures the response {@code Content-Type}
 * matches. For all other JSON requests, the response uses {@code application/json}.
 *
 * <p>The filter only acts on responses from paths under {@code /api/} and responses
 * that already have a JSON content type, preserving any explicit Content-Type set
 * by the endpoint.
 *
 * @see <a href="https://jsonapi.org/format/#content-negotiation">JSON:API Content Negotiation</a>
 */
@Provider
public class ContentNegotiationFilter implements ContainerResponseFilter {

  private static final String JSON_API_MEDIA_TYPE = "application/vnd.api+json";
  private static final String API_PATH_PREFIX = "/api/";

  @Override
  public void filter(ContainerRequestContext requestContext,
                     ContainerResponseContext responseContext) {

    // Only handle API paths
    String path = requestContext.getUriInfo().getPath();
    if (!path.startsWith(API_PATH_PREFIX)) {
      return;
    }

    // Only handle responses with a JSON content type
    MediaType responseType = responseContext.getMediaType();
    if (responseType == null || !responseType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
      return;
    }

    // Check if the client requested JSON:API via Accept header
    List<MediaType> acceptable = requestContext.getAcceptableMediaTypes();
    boolean prefersJsonApi = acceptable.stream()
        .anyMatch(mt -> JSON_API_MEDIA_TYPE.equals(mt.toString())
            || JSON_API_MEDIA_TYPE.equals(mt.getType() + "/" + mt.getSubtype()));

    if (prefersJsonApi) {
      responseContext.getHeaders().putSingle(
          HttpHeaders.CONTENT_TYPE, JSON_API_MEDIA_TYPE);
    }
    // Otherwise, keep the default application/json (already set by @Produces)
  }
}
