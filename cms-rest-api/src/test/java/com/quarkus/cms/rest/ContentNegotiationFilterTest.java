package com.quarkus.cms.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ContentNegotiationFilter}.
 *
 * <p>Verifies the content-negotiation logic: the filter should set the response
 * Content-Type to {@code application/vnd.api+json} when the client sends an
 * {@code Accept: application/vnd.api+json} header, and only for API paths
 * with a JSON response media type.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContentNegotiationFilter")
class ContentNegotiationFilterTest {

  @InjectMocks
  ContentNegotiationFilter filter;

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  ContainerResponseContext responseContext;

  @Mock
  UriInfo uriInfo;

  MultivaluedMap<String, Object> headers;

  @BeforeEach
  void setUp() {
    headers = new MultivaluedHashMap<>();
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(responseContext.getHeaders()).thenReturn(headers);
  }

  @Nested
  @DisplayName("path filtering")
  class PathFiltering {

    @Test
    @DisplayName("should skip non-API paths without inspecting response")
    void skipNonApiPath() {
      when(uriInfo.getPath()).thenReturn("/health");

      filter.filter(requestContext, responseContext);

      verify(responseContext, never()).getMediaType();
      assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should skip root path")
    void skipRootPath() {
      when(uriInfo.getPath()).thenReturn("/");

      filter.filter(requestContext, responseContext);

      verify(responseContext, never()).getMediaType();
    }

    @Test
    @DisplayName("should process API paths")
    void processApiPath() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(null);

      // MediaType is null, so it'll return before checking Accept
      filter.filter(requestContext, responseContext);

      verify(responseContext).getMediaType();
    }
  }

  @Nested
  @DisplayName("media type filtering")
  class MediaTypeFiltering {

    @Test
    @DisplayName("should skip when response has null media type")
    void skipNullMediaType() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(null);

      filter.filter(requestContext, responseContext);

      verify(requestContext, never()).getAcceptableMediaTypes();
      assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should skip when response media type is not JSON-compatible")
    void skipNonJsonMediaType() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.TEXT_PLAIN_TYPE);

      filter.filter(requestContext, responseContext);

      verify(requestContext, never()).getAcceptableMediaTypes();
      assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should process when response media type is JSON")
    void processJsonMediaType() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
      when(requestContext.getAcceptableMediaTypes()).thenReturn(List.of());

      filter.filter(requestContext, responseContext);

      verify(requestContext).getAcceptableMediaTypes();
    }
  }

  @Nested
  @DisplayName("content negotiation")
  class ContentNegotiation {

    @Test
    @DisplayName("should set JSON:API content type when Accept header contains application/vnd.api+json")
    void setJsonApiContentType() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
      when(requestContext.getAcceptableMediaTypes())
          .thenReturn(List.of(MediaType.valueOf("application/vnd.api+json")));

      filter.filter(requestContext, responseContext);

      assertEquals("application/vnd.api+json",
          headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should keep application/json when Accept does not contain JSON:API")
    void keepJsonWhenAcceptIsJson() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
      when(requestContext.getAcceptableMediaTypes())
          .thenReturn(List.of(MediaType.APPLICATION_JSON_TYPE));

      filter.filter(requestContext, responseContext);

      assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should handle multiple Accept types with JSON:API present")
    void multipleAcceptTypesWithJsonApi() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
      when(requestContext.getAcceptableMediaTypes())
          .thenReturn(List.of(
              MediaType.TEXT_HTML_TYPE,
              MediaType.APPLICATION_JSON_TYPE,
              MediaType.valueOf("application/vnd.api+json")
          ));

      filter.filter(requestContext, responseContext);

      assertEquals("application/vnd.api+json",
          headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should skip when response already has JSON:API (not JSON-compatible)")
    void alreadyJsonApi() {
      MediaType jsonApiType = MediaType.valueOf("application/vnd.api+json");
      when(uriInfo.getPath()).thenReturn("/api/articles");
      // application/vnd.api+json is NOT isCompatible with application/json
      when(responseContext.getMediaType()).thenReturn(jsonApiType);

      filter.filter(requestContext, responseContext);

      // Filter skips because response is not JSON-compatible
      verify(requestContext, never()).getAcceptableMediaTypes();
      assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should not set Content-Type when Accept list is empty")
    void emptyAcceptList() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
      when(requestContext.getAcceptableMediaTypes()).thenReturn(List.of());

      filter.filter(requestContext, responseContext);

      assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should not set JSON:API when Accept is */*")
    void acceptStarStar() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
      when(requestContext.getAcceptableMediaTypes())
          .thenReturn(List.of(MediaType.WILDCARD_TYPE));

      filter.filter(requestContext, responseContext);

      assertNull(headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("should handle Accept with JSON:API among wildcards")
    void acceptJsonApiWithWildcard() {
      when(uriInfo.getPath()).thenReturn("/api/articles");
      when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
      when(requestContext.getAcceptableMediaTypes())
          .thenReturn(List.of(
              MediaType.WILDCARD_TYPE,
              MediaType.valueOf("application/vnd.api+json")
          ));

      filter.filter(requestContext, responseContext);

      assertEquals("application/vnd.api+json",
          headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }
  }
}
